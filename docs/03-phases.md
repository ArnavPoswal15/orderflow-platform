# Build phases

Work one phase at a time in Cursor (Agent mode, a fresh chat per phase). At the end of each phase: run the verify steps yourself, read the diff,
and make sure you can explain it before moving on. (The earlier Node/Redis-Streams prototype is only a reference. This is a Java rewrite.)

**After each phase (agent + you):**
1. Agent runs verification (see `.cursor/rules/project.mdc` — Phase-wise implementation).
2. Agent updates `docs/CHANGELOG.md` and the phase mental map in `docs/mental-maps/` (template: `docs/mental-maps/TEMPLATE.md`, index: `docs/mental-maps/README.md`).
3. You read that mental map to see what actually exists before starting the next phase.

---
## Phase 1: Foundations + happy-path saga
**Goal:** `POST /api/orders` → reserved → paid → `CONFIRMED`, over Kafka, in Java.

Scope: Maven multi-module skeleton, `common-events` (envelope + records), docker-compose (Kafka KRaft, Postgres with 3 DBs, Redis),
Flyway schemas, the three services with the happy path only (outbox included from day one), minimal `POST /api/orders` and `GET /api/orders/{id}`, seed 3 SKUs (one with stock 1).
Contracts (see `docs/02-architecture.md` §5.4–5.5): payment-service persists token on `order.created` and charges on `inventory.reserved`; Redis idem key after commit; cancel/charge race handling can land in Phase 2 with cancel API but payment DB schema should include `cancel_requested` from Phase 1.

**Prompt**
> Read @README.md, @docs/01-requirements.md and @docs/02-architecture.md. Implement **Phase 1 only** from docs/03-phases.md: Maven multi-module (common-events, order-service, inventory-service, payment-service), docker-compose with Kafka (KRaft), Postgres (3 databases created by init script) and Redis, Flyway migrations from docs/02-architecture.md section 4, and the happy-path saga with the transactional outbox and idempotent consumers already in place. Minimal REST: POST /api/orders (Idempotency-Key required) and GET /api/orders/{id}. Give me your plan first (<=10 lines), then build, then tell me exact commands to verify.

**Verify:** `docker compose up --build`; POST an order; GET it and watch `PENDING → RESERVED → CONFIRMED`; check `outbox.published_at` is set.

---
## Phase 2: Failure paths + reliability hardening
**Goal:** every failure ends in a consistent state; nothing is lost or doubled.

Scope: `inventory.rejected` path, `payment.failed` → stock release, cancel + refund flow (`order.cancelled`, `payment.refunded`), state machine with guarded transitions, retry/backoff + `.DLT`, duplicate-delivery safety, full atomic multi-line reservation.

**Prompt**
> Implement **Phase 2 only**: failure and compensation paths (inventory.rejected, payment.failed→release stock, cancel after payment→refund+release), the order state machine with illegal-transition protection, Kafka retry with exponential backoff (3 attempts) and dead-letter topics, and POST /api/orders/{id}/cancel. Add automated tests with Testcontainers for acceptance tests A1, A2, A4, A5, A6, A7, A9 in docs/01-requirements.md. Show me how to run them.

**Verify:** `./mvnw verify` green; manual: order with token `fail`, check stock restored; cancel a confirmed order, check refund row.

---
## Phase 3: Edge API (idempotency, rate limit, lists, SSE)
Scope: full idempotency (Redis + DB + request hash, A3), Redis sliding-window rate limit (Lua, A8), `GET /api/products` with Redis cache + invalidation, `GET /api/orders` pagination/filter, SSE per order and global, ship/deliver endpoints, admin stats, problem+json errors.

**Prompt**
> Implement **Phase 3 only**: everything in "Edge protection" and "Real-time" plus the REST table in docs/02-architecture.md section 6 (except flash-sale/failure-rate admin, which come later). Add tests for A3 and A8. Tell me how to watch the SSE stream with curl.

**Verify:** `curl -N localhost:8080/api/stream/events` while placing orders; burst test returns 429s.

---
## Phase 4: Storefront (Next.js)
Scope: product grid with live stock, cart (client state), checkout with payment-token field, order page with live timeline via SSE, order history, cancel button, error/429 handling, Tailwind styling, compose service for `web`.

**Prompt**
> Implement **Phase 4 only**: the Next.js storefront in web/ (App Router, TypeScript, Tailwind) per docs/02-architecture.md section 8: products, cart, checkout, order page with live SSE timeline, order history, cancel. Proxy /api/* to order-service via Next rewrites. Add it to docker-compose. Keep the design clean and responsive.

**Verify:** place an order in the browser and watch the timeline update without refresh; place one with token `fail`.

---
## Phase 5: Ops dashboard
Scope: `/ops` live feed, stock table, status counters, DLT counter, failure-rate slider (payment-service admin endpoint + forward), flash-sale button and result panel.

**Prompt**
> Implement **Phase 5 only**: the /ops dashboard (FR-26 to FR-29) and the backend endpoints it needs: PUT /api/admin/failure-rate (forwarding to payment-service) and POST /api/admin/flash-sale (reset a SKU to stock 1, fire N concurrent orders, return the outcome). The flash-sale result must visibly show exactly 1 CONFIRMED and N-1 CANCELLED.

**Verify:** run flash sale with N=50; drag failure-rate to 100% and watch orders cancel with stock restored.

---
## Phase 6: Observability (ELK)
Scope: JSON logging with MDC + trace id through Kafka headers, Filebeat + Elasticsearch + Kibana under compose profile `elk`, an exported Kibana dashboard (`observability/`), Micrometer metrics (outbox backlog, consumer lag, DLT count) on actuator.

**Prompt**
> Implement **Phase 6 only**: structured JSON logs with orderId/eventId/traceId (propagate traceId via Kafka headers), an `elk` compose profile (Elasticsearch, Kibana, Filebeat), a saved Kibana dashboard exported to observability/, and Micrometer metrics from FR-31. Document how to open Kibana and which searches show a single order's journey end to end.

**Verify:** `docker compose --profile elk up`; in Kibana filter by one `orderId` and see the full event trail across all three services.

---
## Phase 7: Proof and polish
Scope: k6 load test (`loadtest/orders.js`) with an oversell check, remaining tests, GitHub Actions CI (build + tests), architecture diagram in the README (Mermaid), `docs/benchmarks.md` filled **only with real run output**, demo script, screenshots/GIF placeholders, final README rewrite.

**Prompt**
> Implement **Phase 7 only**: a k6 load test that hammers a low-stock SKU and verifies zero oversells afterward, GitHub Actions CI running ./mvnw verify, a Mermaid architecture diagram and polished README, and docs/benchmarks.md with a template I fill from my own run (do not invent numbers). Also write docs/DEMO.md: a 3-minute demo script.

**Verify:** run k6 locally, paste the real numbers into `docs/benchmarks.md`; CI badge green.
