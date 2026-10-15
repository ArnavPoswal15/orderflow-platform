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
- `acceptance-tests` module: scenarios A1, A2, A4, A5, A6, A7; enable with `-Pacceptance` (A9 added in Phase 7 acceptance module; also `OrderShipApiTest` in order-service).

## Phase 3 — Edge API (2026-10-15)
- Redis sliding-window rate limit on `POST /api/orders` (`rl:{clientId}`, Lua script, 429 + `Retry-After` + `X-RateLimit-*`).
- `GET /api/products` with `stock:{sku}` Redis cache (TTL 5s) and invalidation on inventory stock events.
- `GET /api/orders` paginated list; `POST /api/orders/{id}/deliver`; ship/deliver enqueue `order.shipped` / `order.delivered` via outbox.
- SSE: `SseHub` + `SseFeedKafkaConsumer` (`sse-feed-<instance>` group) → `GET /api/stream/events` and `/api/stream/orders/{id}`.
- `GET /api/admin/stats` (orders by status, outbox backlog, DLT offset sums).
- `ApiExceptionHandler` (RFC 7807 problem+json); inventory `GET /internal/products`.
- Acceptance A3 (parallel idempotency) and A8 (rate-limit burst); hardened concurrent idempotency replay (`REQUIRES_NEW` create + fetch join).

## Phase 4 — Storefront (2026-10-15)
- Next.js storefront in `web/` (App Router, TypeScript, Tailwind): `/`, `/cart`, `/checkout`, `/orders`, `/orders/[id]`. No `/ops`.
- Browser calls same-origin `/api`. REST is rewritten to `ORDER_SERVICE_URL` (default `http://localhost:8080`). SSE `GET /api/stream/*` is a streaming route handler so the rewrite proxy does not buffer or cut the event stream at 30s.
- Docker Compose `web` service on port 3000, build arg `ORDER_SERVICE_URL=http://order-service:8080`. Standalone image runs `node server.js`.
- Service Dockerfiles also copy the `acceptance-tests` module so `docker compose up --build` can read the parent POM.
- Checkout keeps one `Idempotency-Key` in `sessionStorage` (`shop.checkoutAttempt`) until the cart or payment token changes, and drops it only after a successful POST.
- The order page reads SSE with `fetch` and refetches every 400ms while the status is `PENDING` or `RESERVED`, so `CONFIRMED` appears without waiting for a later event.

## Phase 5 — Ops dashboard (2026-10-15)
- `/ops` page: live SSE feed (`GET /api/stream/events`), stock table, order-status and DLT counters, a 0–100 failure-rate slider, and a flash-sale result panel. Ops is linked from the site header. `/api/admin` is rewritten to order-service.
- payment-service `GET`/`PUT /admin/failure-rate` with `{ "percent" }`. `PaymentFailureRate` is an `AtomicInteger` seeded from `PAYMENT_FAILURE_RATE` (default 0). `PaymentGateway.charge` reads it on every call. Out of range is RFC 7807 400. Token `fail` still declines at 0%.
- order-service forwards `GET`/`PUT /api/admin/failure-rate` to `PAYMENT_SERVICE_URL`. `POST /api/admin/flash-sale` resets one SKU to stock 1 (`PUT /internal/skus/{sku}/stock`), drops `stock:{sku}`, then places N orders through `OrderApplicationService.createOrder` (outbox, not a direct Kafka publish) and waits until each order is terminal.
- Flash-sale JSON is `{ confirmed, cancelled, concurrentOrders, sku, orders[{ orderId, status, cancelReason }] }`. The acceptance harness sets `PAYMENT_FAILURE_RATE`, the name `application.yml` reads.
- `AcceptanceFlashSaleTest` (profile `acceptance`): N=10 on `LASTITEM` expects 1 `CONFIRMED`, 9 `CANCELLED`, stock 0.

## Phase 6 — Observability (ELK) (2026-10-16)
- Structured JSON logs (Logstash encoder) with `orderId`, `eventId`, `traceId`, `eventType`, `orderStatus`, `failureReason`, `latencyMs` in MDC via `common-events` `TraceContext` and shared `logback-spring.xml`.
- order-service `TraceIdFilter` sets/echoes `X-Trace-Id`; Kafka producers/consumers propagate trace via record headers (`TraceRecordInterceptor`, `OutboxPublisher`).
- Compose profile `elk`: Elasticsearch 8.17, Kibana, Filebeat (tails `/var/log/shop/*.json` when `SPRING_PROFILES_ACTIVE=elk-file`), index template `shop-logs`, imported dashboard `observability/order-platform.ndjson` (`kibana-setup`).
- Micrometer: `shop.outbox.backlog`, `shop.consumer.lag`, `shop.dlt.messages` on all three services; `shop.orders.created` / `shop.orders.cancelled` on order-service (Actuator `/actuator/metrics/...`, `prometheus`).
- Operator docs: `observability/README.md`. Kibana NDJSON export omits legacy `migrationVersion` for Kibana 8.17 import.

## Phase 7 — Proof and polish (2026-10-16)
- k6 load test `loadtest/orders.js`: resets failure rate and `LASTITEM` stock, concurrent `POST /api/orders` with unique `X-Forwarded-For` per VU, teardown `OVERSOLD_CHECK` (no negative stock, confirmed delta ≤ initial stock), `handleSummary` to stdout.
- GitHub Actions `.github/workflows/ci.yml`: JDK 21, `./mvnw -q verify` on push/PR to `main` (acceptance module still requires `-Pacceptance`).
- Acceptance `AcceptanceA9Test`: ship on `PENDING` order returns 409, status unchanged; `AcceptanceA1Test` passes unique `X-Forwarded-For` per racer (`HttpTestClient` optional header).
- Docs: `docs/benchmarks.md` (empty template), `docs/DEMO.md` (3-minute script), `docs/assets/README.md`; README CI badge, Mermaid ops/ELK, load test and demo sections.
