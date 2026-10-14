# Architecture

## 1. Services

| Service | Owns | Public surface | Consumes | Produces |
|---|---|---|---|---|
| **order-service** | `orders`, `order_items`, `order_events`, `outbox`, `processed_events` | REST + SSE (edge), rate limit, idempotency | `inventory.events`, `payments.events` | `orders.events` |
| **inventory-service** | `inventory`, `reservations`, `outbox`, `processed_events` | internal read API for stock | `orders.events`, `payments.events` | `inventory.events` |
| **payment-service** | `payments` (amount + token from `order.created`), `outbox`, `processed_events`, failure-rate config | `PUT /admin/failure-rate` | `orders.events`, `inventory.events` | `payments.events` |
| **web** | nothing | Next.js storefront + `/ops` | via order-service REST/SSE | none |

order-service doubles as the edge/BFF to keep the system small. It also runs one extra consumer
(group `sse-feed-<instance>`) over all topics to power the SSE streams. Stretch goal: split into a gateway.

## 2. Event flow

```
Shopper      order-service        inventory-service       payment-service
  │ POST /orders  │                      │                      │
  │──────────────▶│ tx: order=PENDING + outbox(order.created)   │
  │◀── 202 ───────│                      │                      │
  │               │──── order.created ──▶│ tx: UPDATE stock WHERE available>=qty
  │               │──── order.created ─────────────────────────▶│ tx: payments row PENDING
  │               │                      │     (amount_cents, payment_token)  │
  │               │                      │     reservation + outbox           │
  │               │◀─ inventory.reserved ┤──── inventory.reserved ─▶│ tx: charge if not cancelled
  │               │   (→ RESERVED)       │   (stock facts only)   │     payment + outbox
  │               │◀────────────── payment.succeeded ───────────┤
  │               │   (→ CONFIRMED)      │                      │
  ── failure branches ─────────────────────────────────────────────────────────────
  inventory.rejected ─────────▶ order → CANCELLED (reason: out of stock)
  payment.failed ─────────────▶ order → CANCELLED ; inventory releases stock ─▶ inventory.released
  order.cancelled ─────────────▶ payment: skip charge or refund if already charged ─▶ payment.refunded
                                 inventory: release reservation ─▶ inventory.released
  ── cancel vs in-flight charge (unordered across topics) ───────────────────────
  order.cancelled and payment.succeeded have no Kafka ordering guarantee. payment-service
  records cancel on `order.cancelled`; if charge completes after cancel, refund in the
  same DB transaction as marking SUCCEEDED. order-service ignores payment.succeeded if
  the order is already CANCELLED (guarded transition).
```

Why **choreography** (not an orchestrator): fewer moving parts and it demonstrates event-driven design clearly.
Trade-off to state in interviews: the overall flow is implicit, so debugging needs good logs and event history (hence the `order_events` table and ELK).

## 3. Event catalog

All events share one envelope (in `common-events`):
```json
{ "eventId": "uuid", "type": "inventory.reserved", "version": 1,
  "orderId": "uuid", "occurredAt": "ISO-8601", "traceId": "uuid", "payload": { } }
```

| Topic | Type | Payload |
|---|---|---|
| orders.events | `order.created` | items[{sku,qty}], amountCents, paymentToken |
| orders.events | `order.cancelled` | reason, paidBeforeCancel (bool) |
| orders.events | `order.shipped` / `order.delivered` | none |
| inventory.events | `inventory.reserved` | items[{sku,qty}] |
| inventory.events | `inventory.rejected` | sku, reason |
| inventory.events | `inventory.released` | items |
| payments.events | `payment.succeeded` | amountCents |
| payments.events | `payment.failed` | reason |
| payments.events | `payment.refunded` | amountCents |

Topic settings: 6 partitions each (local), replication 1, key = `orderId`, consumers use `enable.auto.commit=false`
with commit after the DB transaction succeeds. DLTs: `<topic>.DLT`.

## 4. Data model (per service database; Flyway migrations)

**orders DB**
```
orders(id uuid pk, idempotency_key text unique, request_hash text, status text, total_cents bigint,
       payment_token text, cancel_reason text, version int, created_at, updated_at)
order_items(order_id fk, sku text, qty int, unit_price_cents bigint)
order_events(id bigserial pk, order_id, type text, data jsonb, at timestamptz)   -- append-only
outbox(id bigserial pk, event_id uuid unique, topic text, key text, payload jsonb, created_at, published_at null)
processed_events(event_id uuid pk, processed_at)
```
**inventory DB**
```
inventory(sku pk, name text, price_cents bigint, available int check(available>=0), version int)
reservations(order_id pk, status text /*RESERVED|RELEASED*/, items jsonb, created_at)
outbox(...) ; processed_events(...)
```
**payments DB**
```
payments(order_id pk, status text /*PENDING|SUCCEEDED|FAILED|REFUNDED*/, amount_cents bigint,
         payment_token text, cancel_requested boolean default false, created_at, updated_at)
outbox(...) ; processed_events(...)
```
- On `order.created`: insert `PENDING` with `amount_cents` and `payment_token` (idempotent on `order_id`).
- On `order.cancelled`: set `cancel_requested = true`; if already `SUCCEEDED`, refund once and move to `REFUNDED`.
- On `inventory.reserved`: charge only when `cancel_requested` is false and status is still `PENDING`.

## 5. The five mechanisms in detail

### 5.1 Transactional outbox
- Business code writes state + an `outbox` row in one `@Transactional` method.
- `OutboxPublisher` (scheduled every ~200ms): `SELECT ... FROM outbox WHERE published_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED`, send to Kafka (`acks=all`, idempotent producer), mark `published_at`.
- Duplicates are possible if the app dies after send but before marking. That's fine because consumers are idempotent.
- Alternative to mention: Debezium CDC reading the Postgres WAL instead of polling.

### 5.2 Idempotent consumer
Inside the same DB transaction as the effect: `INSERT INTO processed_events(event_id) ... ON CONFLICT DO NOTHING`; if 0 rows inserted, skip. Kafka offset is committed after the transaction.

### 5.3 Atomic stock reservation
```sql
UPDATE inventory SET available = available - :qty, version = version + 1
WHERE sku = :sku AND available >= :qty;      -- rowCount 0 => out of stock
```
Process SKUs in sorted order to avoid deadlocks. All lines in one transaction (all-or-nothing). Optimistic locking
(`version`) is the alternative to discuss; the conditional update is chosen because it needs no retry loop.

### 5.4 Order idempotency (edge)
1. **Fast path (replay):** `GET idem:{key}`. If present, load that `orderId` and return 200 with the existing order.
2. **Authoritative create:** In one DB transaction, insert the order with `idempotency_key` UNIQUE and `request_hash` (SHA-256 of body). On unique violation, load the existing row; if `request_hash` differs, return 422; else return 200 replay.
3. **After commit only:** `SET idem:{key} {orderId} EX 86400`. Never set Redis before the insert commits — a crash between a premature SET and commit would block retries with no order row.
4. **Parallel duplicates:** Two requests may both miss Redis; the DB unique constraint picks one winner; the loser follows step 2 and warms Redis in step 3.

### 5.5 Cancel vs in-flight charge
`order.cancelled` and `payment.succeeded` are on different topics, so they are not ordered relative to each other.
- **payment-service** consumes both `orders.events` and `inventory.events`.
- On `order.cancelled`: set `cancel_requested` on the payments row; refund immediately if status is already `SUCCEEDED`.
- On `inventory.reserved`: skip the gateway if `cancel_requested` or status is not `PENDING`.
- After a successful gateway call: if `cancel_requested` became true during the call, persist `SUCCEEDED` and emit `payment.refunded` (not `payment.succeeded`) in the same transaction, or emit succeeded then refund in one transaction — net effect: one charge and one refund, order stays cancelled.
- **order-service** applies `payment.succeeded` only when the state machine allows `CONFIRMED`; if the order is already `CANCELLED`, log and ignore (idempotent).

### 5.6 Retries and dead letters
`DefaultErrorHandler` + `ExponentialBackOff` (3 attempts), then `DeadLetterPublishingRecoverer` to `<topic>.DLT`.
Non-retryable exceptions (validation/deserialization) go straight to DLT. An ops endpoint reports DLT message counts.

## 6. REST API (order-service)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/products` | name, price, availability (Redis-cached) |
| POST | `/api/orders` | header `Idempotency-Key`; body `{items:[{sku,qty}], paymentToken}`; 202 / 200 replay / 422 / 429 |
| GET | `/api/orders?status=&page=&size=` | paginated list |
| GET | `/api/orders/{id}` | order + history |
| POST | `/api/orders/{id}/cancel` | 202; 409 if state forbids |
| POST | `/api/orders/{id}/ship`, `/deliver` | operator actions; 409 if illegal |
| GET | `/api/stream/orders/{id}` | SSE status updates |
| GET | `/api/stream/events` | SSE all events (ops) |
| GET | `/api/admin/stats` | counts by status, DLT counts, outbox backlog |
| POST | `/api/admin/flash-sale` | `{sku, concurrentOrders}`; resets SKU stock to 1 and fires orders |
| PUT | `/api/admin/failure-rate` | forwards to payment-service |

## 7. Redis usage
| Key | Purpose |
|---|---|
| `idem:{key}` | order idempotency fast path (TTL 24h) |
| `rl:{clientId}` | sliding-window rate limit (sorted set, Lua script for atomicity) |
| `stock:{sku}` | availability read cache (TTL 5s, deleted on stock change event) |

## 8. Frontend (Next.js)
- `/` product grid, `/cart`, `/checkout`, `/orders`, `/orders/[id]` (live timeline via `EventSource`)
- `/ops` live feed, stock table, status counters, failure-rate slider, flash-sale button
- Server Components for initial data, Client Components for SSE. Next.js rewrites `/api/*` to order-service (no CORS pain).

## 9. Observability
- Logback JSON encoder; MDC keys `orderId`, `eventId`, `traceId` (propagated through Kafka headers).
- Filebeat tails container logs → Elasticsearch → Kibana dashboard: orders by status over time, failure reasons, event-to-event latency (`order.created` → `payment.succeeded`).
- Micrometer metrics: outbox backlog, consumer lag, DLT count, orders created/cancelled counters.
- ELK runs under compose profile `elk` (needs ~4GB RAM).

## 10. Key decisions and trade-offs (know these cold)
| Decision | Why | Trade-off |
|---|---|---|
| Choreography | simple, decoupled | flow is implicit; harder to trace |
| Outbox over direct publish | no dual-write problem | extra table + polling latency (~200ms) |
| At-least-once + idempotent consumers | achievable and robust | every handler needs dedupe |
| Key = orderId | per-order ordering | hot keys not an issue here; ordering across orders not guaranteed |
| DB per service (one Postgres, 3 DBs locally) | real isolation | no cross-service joins; eventual consistency |
| Conditional UPDATE for stock | simplest correct approach | contention on very hot SKUs (mention Redis pre-check/sharding at scale) |
| order-service as edge | fewer components | should become a gateway at scale |
| Payment token on `order.created` only | inventory stays payment-agnostic; token not relayed on `inventory.reserved` | payment-service must consume `orders.events` before charging |
| Redis idem key after DB commit | avoids false cache entries on crash | replay path may hit DB until Redis is warmed |
| Cancel/charge race in payment-service | no cross-topic ordering | extra flags on `payments` row; order ignores late `payment.succeeded` |
