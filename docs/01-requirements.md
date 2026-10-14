# Requirements

## Actors
- **Shopper**: browses products, places and tracks orders, cancels orders.
- **Operator**: watches live events, ships orders, tunes failure rate, runs flash-sale demo.

## Functional requirements

### Catalog & inventory
- FR-1 List products with price and current availability (`GET /api/products`).
- FR-2 Availability reads are served from a Redis cache (TTL 5s) and invalidated on stock change.

### Ordering
- FR-3 Place an order with one or more `{sku, qty}` lines. Requires an `Idempotency-Key` header.
- FR-4 Same key + same body returns the original order (HTTP 200), never a second order. Same key + different body returns 422. The Redis idempotency cache is written only after the order row commits (DB unique constraint is the backstop).
- FR-5 Order creation returns 202 immediately; processing continues asynchronously.
- FR-6 List orders (`GET /api/orders`, paginated, filter by status) and get one with its event history.
- FR-7 Cancel an order while it is `PENDING`, `RESERVED` or `CONFIRMED`. Cancelling after payment triggers a refund.
- FR-8 Operator can mark a `CONFIRMED` order `SHIPPED`, then `DELIVERED`.

### Order lifecycle (state machine)
- FR-9 States: `PENDING, RESERVED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED`. Illegal transitions are rejected and logged (REST returns 409). Redelivered events never corrupt state.
- FR-10 Every transition appends to an immutable `order_events` history (who/what/when).

### Inventory
- FR-11 On `order.created`, reserve all lines atomically (all or nothing). Emit `inventory.reserved` or `inventory.rejected`.
- FR-12 Concurrent orders for the last unit: exactly one succeeds, the other is rejected, and stock never goes negative.
- FR-13 On `payment.failed` or `order.cancelled`, release the reservation exactly once, then emit `inventory.released`.

### Payments
- FR-14 On `order.created`, payment-service records `amount_cents` and `payment_token` for the order (idempotent on `orderId`). On `inventory.reserved`, charge via the simulated gateway using that stored data — not fields on the inventory event. One successful charge per order, no matter how many times `inventory.reserved` is delivered.
- FR-15 Gateway outcome is controlled by a configurable failure rate (default 10%) and a magic token `fail` that always declines.
- FR-16 On `order.cancelled` for a paid order, refund once and emit `payment.refunded`.
- FR-16a If `order.cancelled` arrives before charge, skip the gateway on `inventory.reserved`. If charge completes after cancel was recorded, refund exactly once (cancel beats in-flight charge). order-service does not move a `CANCELLED` order to `CONFIRMED` on a late `payment.succeeded`.

### Reliability
- FR-17 Transactional outbox in every service. A crash between commit and publish loses no events.
- FR-18 Failed messages are retried with backoff (3 attempts), then sent to a dead-letter topic `<topic>.DLT`. Operator can see DLT count.
- FR-19 Every consumer is idempotent via a `processed_events` table.

### Edge protection
- FR-20 Rate limit `POST /api/orders` per client with a Redis sliding window (default 20/min). Return 429 with `Retry-After` and `X-RateLimit-*` headers.

### Real-time
- FR-21 SSE stream per order (`/api/stream/orders/{id}`) pushes status changes to the order page.
- FR-22 SSE stream of all events (`/api/stream/events`) feeds the ops dashboard.

### UI: storefront
- FR-23 Product grid with live stock badges; cart; checkout with payment-token field (so "fail" can be demoed).
- FR-24 Order page with live status timeline (PENDING → … ) and cancellation reason when applicable.
- FR-25 Order history page.

### UI: ops dashboard (`/ops`)
- FR-26 Live event feed (type, orderId, time) with color by event type.
- FR-27 Stock levels table, orders-by-status counters, DLT counter.
- FR-28 Payment failure-rate slider (0–100%) that takes effect immediately.
- FR-29 "Flash sale" button: fires N concurrent orders at one SKU with stock 1, then shows the outcome (1 confirmed, N-1 cancelled).

### Observability
- FR-30 JSON logs with `orderId`/`eventId`/`traceId`; shipped to Elasticsearch via Filebeat; a Kibana dashboard shows order flow, failures and latency.
- FR-31 Actuator health + Micrometer metrics on every service (consumer lag, outbox backlog, DLT count).

## Non-functional requirements
- NFR-1 **Correctness over speed.** Zero oversells and zero duplicate charges under concurrent load. This is the headline claim.
- NFR-2 Each service restartable at any time without losing or duplicating effects.
- NFR-3 One-command local start (`docker compose up --build`); ELK behind an optional profile (memory heavy).
- NFR-4 Throughput and latency are **measured, not assumed**: record real k6 results in `docs/benchmarks.md`.
- NFR-5 Automated tests run in CI on every push.

## Acceptance tests (must exist as automated tests)
| # | Scenario | Expected |
|---|---|---|
| A1 | 50 concurrent orders for a SKU with stock 1 | exactly 1 `CONFIRMED`, 49 `CANCELLED`, stock = 0 |
| A2 | Order with token `fail` | ends `CANCELLED`; stock fully restored; no payment row `SUCCEEDED` |
| A3 | Same `Idempotency-Key` sent 10x in parallel | exactly 1 order row, 1 charge |
| A4 | Same Kafka event delivered twice | effect applied once (stock, payment, state) |
| A5 | Kill publisher after DB commit, restart | event still reaches Kafka (outbox) |
| A6 | Poison message | retried 3x then lands in `.DLT`; consumer keeps processing others |
| A7 | Cancel a `CONFIRMED` order | refund once, stock released once, order `CANCELLED` |
| A8 | 25 requests in a burst from one client | requests beyond limit get 429 + `Retry-After` |
| A9 | Illegal transition (ship a `PENDING` order) | 409, state unchanged |
