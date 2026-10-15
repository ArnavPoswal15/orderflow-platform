# Phase 3: Edge API

> **Status:** Complete  
> **Completed:** 2026-10-15  
> **Spec:** `docs/03-phases.md` — Phase 3

---

## One-screen picture

```
 Browser / curl
      │
      ▼
 order-service :8080  ──REST──► Redis (idem, rl, stock cache)
      │                ──HTTP──► inventory-service /internal/products
      │                ──JPA───► orders DB
      │
      ├── RateLimitFilter (POST /api/orders only)
      ├── ProductController, OrderController (list/deliver), StreamController (SSE)
      ├── AdminController (/api/admin/stats)
      └── SseFeedKafkaConsumer (group sse-feed-<instance>, no processed_events)
              ▲
              │ orders.events, inventory.events, payments.events
           Kafka
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | `order-service` edge APIs; `inventory-service` `GET /internal/products` |
| REST (public) | `GET /api/products`, `GET /api/orders`, `POST …/deliver`, `GET /api/stream/*`, `GET /api/admin/stats`; rate limit on `POST /api/orders` |
| Kafka consume / produce | SSE feed consumer (separate group); ship/deliver publish `order.shipped` / `order.delivered` via outbox |
| DB tables (per service) | Unchanged schemas; list queries + status GROUP BY on `orders` |
| Redis keys | `rl:{clientId}` rate limit; `stock:{sku}` availability cache (5s TTL) |
| Tests | `AcceptanceA3Test`, `AcceptanceA8Test`; existing `OrderShipApiTest` (A9) |
| Docker / compose | No new compose services (same stack as Phase 1–2) |

### Events (this phase only)

| Topic | Type | Producer | Consumer(s) | Payload (as implemented) |
|-------|------|----------|-------------|---------------------------|
| `orders.events` | `order.shipped` | order-service outbox | SSE feed (fan-out) | `{}` |
| `orders.events` | `order.delivered` | order-service outbox | SSE feed (fan-out) | `{}` |

### Architecture contracts (document if touched this phase)

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Redis `rl:{clientId}` | Lua ZSET sliding window in `RateLimitService` |
| Redis `stock:{sku}` | `StockCacheService`; invalidated on `inventory.reserved` / `rejected` / `released` in `OrderEventConsumers` |
| Idempotent parallel create | `OrderCreator.createInNewTransaction` + DB unique `idempotency_key` + retry load with `findByIdempotencyKeyWithItems` |

---

## Happy path (as implemented)

1. `GET /api/products` → `ProductCatalogService` loads catalog via `InventoryClient.listAllProducts()`, merges `stock:{sku}` cache.
2. `POST /api/orders` → `RateLimitFilter` → idempotency (Redis + DB) → saga unchanged from Phase 1–2.
3. `GET /api/orders?status=&page=&size=` → paginated `OrderPageView` (summary rows, no history).
4. `POST /api/orders/{id}/ship` / `deliver` → state machine + history + outbox event.
5. `GET /api/stream/events` or `/api/stream/orders/{id}` → `SseHub` receives Kafka copies from `SseFeedKafkaConsumer`.

---

## Failure / edge paths (if in scope for this phase)

- Rate limit: 21st+ `POST /api/orders` per client per minute → **429** with `Retry-After`, problem+json, `X-RateLimit-*`.
- Parallel same `Idempotency-Key` → one row, replays return **200** with same `id` (A3).
- Illegal ship/deliver → **409** problem+json (`ApiExceptionHandler` / controller).

---

## How to verify (copy from phase doc + what you actually ran)

```bash
./mvnw -q verify
./mvnw -pl acceptance-tests -Pacceptance test -Dtest=AcceptanceA3Test,AcceptanceA8Test
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (2026-10-15) |
| Acceptance A3, A8 | **PASS** (2026-10-15) |
| `docker compose up --build` | Not re-run in this session (manual SSE / burst per phase doc) |

**SSE (with stack up):**

```bash
curl -N -H 'Accept: text/event-stream' http://localhost:8080/api/stream/events
curl -N -H 'Accept: text/event-stream' http://localhost:8080/api/stream/orders/<ORDER_UUID>
```

---

## Key files (entry points)

| Path | Role |
|------|------|
| `order-service/.../api/ratelimit/*` | Redis Lua rate limit + filter |
| `order-service/.../domain/ProductCatalogService.java` | Products + stock cache |
| `order-service/.../api/StreamController.java` | SSE endpoints |
| `order-service/.../messaging/SseHub.java`, `SseFeedKafkaConsumer.java` | Kafka → browser fan-out |
| `order-service/.../domain/AdminStatsService.java` | Admin stats |
| `order-service/.../api/ApiExceptionHandler.java` | problem+json |
| `acceptance-tests/.../AcceptanceA3Test.java`, `AcceptanceA8Test.java` | A3, A8 |

---

## Not built yet (explicit gaps)

- `POST /api/admin/flash-sale`, `PUT /api/admin/failure-rate` (Phase 5)
- Next.js storefront (Phase 4)
- ELK / Micrometer DLT metrics (Phase 6)

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md`
- Previous phase map: `phase-02-failures-reliability.md`
