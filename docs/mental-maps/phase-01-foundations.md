# Phase 1: Foundations + happy-path saga

> **Status:** Complete  
> **Completed:** 2026-10-15  
> **Spec:** `docs/03-phases.md` — Phase 1

---

## One-screen picture

```
  curl POST :8080/api/orders
         │
         ▼
┌─────────────────┐     orders.events      ┌──────────────────┐
│  order-service  │ ─── order.created ────▶│ inventory-service│
│  DB: orders     │                        │ DB: inventory    │
│  Redis: idem:*  │ ─── order.created ────▶├──────────────────┤
│  outbox ──▶Kafka│                        │ payment-service  │
│                 │◀── inventory.reserved ─│ DB: payments     │
│                 │◀── payment.succeeded ──│ outbox ──▶Kafka  │
└─────────────────┘                        └──────────────────┘
        Kafka (KRaft) :9092    Postgres :5432 (3 DBs)    Redis :6379
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | `common-events`, `order-service` (:8080), `inventory-service` (:8081), `payment-service` (:8082) |
| REST (public) | `POST /api/orders`, `GET /api/orders/{id}` on order-service; `GET /internal/skus?sku=` on inventory (internal) |
| Kafka consume / produce | Topics `orders.events`, `inventory.events`, `payments.events`; outbox publisher ~200ms; manual ack consumers |
| DB tables (per service) | Per architecture §4 Flyway `V1__schema.sql` in each service |
| Redis keys | `idem:{idempotencyKey}` → orderId, TTL 24h, set **after** order commit |
| Tests | `common-events` unit test; per-service `@SpringBootTest` (Testcontainers, skipped if no Docker) |
| Docker / compose | `docker-compose.yml` + per-service Dockerfiles; service Dockerfiles copy all Maven modules + `.dockerignore` |

### Events (this phase only)

| Topic | Type | Producer | Consumer(s) | Payload (as implemented) |
|-------|------|----------|-------------|---------------------------|
| `orders.events` | `order.created` | order-service (outbox) | inventory-service, payment-service | `items`, `amountCents`, `paymentToken` |
| `inventory.events` | `inventory.reserved` | inventory-service (outbox) | order-service, payment-service | `items[{sku,qty}]` only |
| `payments.events` | `payment.succeeded` | payment-service (outbox) | order-service | `amountCents` |

### Architecture contracts (document if touched this phase)

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Transactional outbox | State + `outbox` row in one `@Transactional`; `OutboxPublisher` ~200ms, key = `orderId` |
| Idempotent consumers (`processed_events`) | Insert `event_id` in same txn as effect; skip if duplicate |
| `paymentToken` only on `order.created` + payment DB | `PaymentApplicationService` on `order.created`; charge on `inventory.reserved` from `payments` row |
| Redis `idem:{key}` after order DB commit | `OrderCreator` / transaction sync `afterCommit` |
| `inventory.reserved` stock-only payload | No token or amount on inventory event |
| Cancel vs in-flight charge | `payments.cancel_requested` column present; full behavior in Phase 2 |

---

## Happy path (as implemented)

1. `OrderController` accepts `POST /api/orders` with `Idempotency-Key` and body `{items, paymentToken}`.
2. `OrderCreator` loads prices from `InventoryClient` → `GET /internal/skus`, inserts `PENDING` order + `order_events` + outbox `order.created` in one transaction; Redis idem key in `afterCommit`.
3. `OutboxPublisher` (order-service) publishes to `orders.events` (key = `orderId`).
4. `OrderEventConsumer` (inventory) idempotently reserves stock (`ReservationService`, atomic `UPDATE … WHERE available >= qty`), writes outbox `inventory.reserved` with payload `{items:[{sku,qty}]}` only.
5. `PaymentEventConsumers` (payment): on `order.created` inserts `payments` row `PENDING` with `amount_cents` + `payment_token`; on `inventory.reserved` runs `PaymentGateway.charge` and outbox `payment.succeeded` (retries if payment row not ready yet).
6. `OrderEventConsumers` (order): `inventory.reserved` → `RESERVED`; `payment.succeeded` → `CONFIRMED` (ignores late confirm if already `CANCELLED`).

---

## Failure / edge paths (if in scope for this phase)

- Not implemented: `inventory.rejected`, `payment.failed`, cancel/refund, DLT/retry, rate limit, SSE, storefront.
- Out-of-stock during reserve rolls back consumer txn (no `inventory.rejected` yet — Phase 2).
- Token `fail` declines payment but no `payment.failed` event yet (Phase 2).

---

## How to verify (copy from phase doc + what you actually ran)

```bash
./mvnw -q verify

docker compose up --build

# Place order
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"items":[{"sku":"MILK","qty":1}],"paymentToken":"ok"}'

# Poll until CONFIRMED (or watch status field)
curl -s http://localhost:8080/api/orders/<orderId>

# Outbox published (orders DB example)
docker compose exec postgres psql -U shop -d orders -c \
  "SELECT event_id, topic, published_at IS NOT NULL AS published FROM outbox;"
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (2026-10-15) |
| `docker compose up --build` | **PASS** (user, 2026-10-15) |
| Manual checks from `docs/03-phases.md` | **PASS** — `POST` → `PENDING`; `GET` → `CONFIRMED` with `inventory.reserved` + `payment.succeeded` in history (order `a9b6ba6f-4c05-4a72-b90b-b2fb8fd0c920`) |

---

## Key files (entry points)

| Path | Role |
|------|------|
| `pom.xml` | Parent BOM, Spring Boot 3.5.16 |
| `docker-compose.yml` | Infra + three services |
| `common-events/.../EventJson.java` | Envelope serialize/deserialize |
| `order-service/.../OrderController.java` | REST edge |
| `order-service/.../OrderCreator.java` | Create order + outbox |
| `order-service/.../messaging/OutboxPublisher.java` | Outbox → Kafka |
| `inventory-service/.../ReservationService.java` | Atomic reserve + outbox |
| `payment-service/.../PaymentApplicationService.java` | Record + charge |
| `*/src/main/resources/db/migration/V1__schema.sql` | Per-service schema |

---

## Not built yet (explicit gaps)

- Failure/compensation saga paths, state-machine guards, DLT/retry (Phase 2).
- Full idempotency hash/422, rate limit, products API + Redis stock cache, SSE, cancel/ship/deliver (Phase 3+).
- Next.js `web/`, ELK, k6, CI (later phases).

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md`
