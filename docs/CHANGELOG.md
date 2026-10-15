# Changelog
(The agent appends one entry per completed phase.)

## Pre-Phase 1 — architecture contract fixes (docs)
- Payment token stored by payment-service on `order.created`; `inventory.reserved` payload is stock facts only (`items`).
- Documented cancel vs in-flight charge handling (FR-16a, architecture §5.5).
- Order idempotency: Redis `idem:{key}` set after DB commit (architecture §5.4, FR-4).

## Phase 1 — Foundations + happy-path saga (2026-10-15)
- Maven multi-module parent (Spring Boot **3.5.16**, Java 21): `common-events`, `order-service`, `inventory-service`, `payment-service`.
- `docker-compose.yml`: Kafka 3.8 KRaft, Postgres 16 (init creates `orders` / `inventory` / `payments` DBs), Redis 7, three services.
- Flyway V1 schemas per architecture §4; seed SKUs MILK, EGGS, LASTITEM (stock 1).
- Happy path: `POST /api/orders` → `order.created` → reserve → `inventory.reserved` → charge → `payment.succeeded` → `CONFIRMED`.
- Transactional outbox + idempotent consumers (`processed_events`) in all three services.
- Payment: token/amount on `order.created`; charge on `inventory.reserved`; `cancel_requested` column on `payments`.
- Order edge: `Idempotency-Key` required; Redis `idem:{key}` after DB commit; internal price lookup via `inventory-service` `GET /internal/skus`.

## Phase 2 — Failure paths + reliability (2026-10-15)
- Saga failures: `inventory.rejected`, `payment.failed` → stock release (`inventory.released`), cancel/refund (`order.cancelled`, `payment.refunded`).
- Order state machine (`OrderStateMachine`) with guarded Kafka transitions and REST `409` on illegal commands.
- REST: `POST /api/orders/{id}/cancel`, `POST /api/orders/{id}/ship`.
- Kafka `DefaultErrorHandler`: exponential backoff (3 attempts) + `DeadLetterPublishingRecoverer` → `<topic>.DLT`.
- `acceptance-tests` module: scenarios A1, A2, A4, A5, A6, A7 (A9 in `order-service` `OrderShipApiTest`); enable with `-Pacceptance`.

## Phase 3 — Edge API (2026-10-15)
- Redis sliding-window rate limit on `POST /api/orders` (`rl:{clientId}`, Lua script, 429 + `Retry-After` + `X-RateLimit-*`).
- `GET /api/products` with `stock:{sku}` Redis cache (TTL 5s) and invalidation on inventory stock events.
- `GET /api/orders` paginated list; `POST /api/orders/{id}/deliver`; ship/deliver enqueue `order.shipped` / `order.delivered` via outbox.
- SSE: `SseHub` + `SseFeedKafkaConsumer` (`sse-feed-<instance>` group) → `GET /api/stream/events` and `/api/stream/orders/{id}`.
- `GET /api/admin/stats` (orders by status, outbox backlog, DLT offset sums).
- `ApiExceptionHandler` (RFC 7807 problem+json); inventory `GET /internal/products`.
- Acceptance A3 (parallel idempotency) and A8 (rate-limit burst); hardened concurrent idempotency replay (`REQUIRES_NEW` create + fetch join).
