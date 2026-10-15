# Phase 5: Ops dashboard

> **Status:** Complete  
> **Completed:** 2026-10-15  
> **Spec:** `docs/03-phases.md` — Phase 5

---

## One-screen picture

```
 Browser :3000 /ops
      │
      ▼
 web (Next.js)
      ├── rewrite /api/admin/* ──────────► order-service :8080
      ├── rewrite /api/products ─────────► order-service :8080
      └── route  /api/stream/events ─────► order-service SSE (existing)
                    │
                    │  GET/PUT /api/admin/failure-rate  {percent}
                    │  POST    /api/admin/flash-sale    {sku, concurrentOrders}
                    │  GET     /api/admin/stats
                    ▼
            AdminController
              │                         │
              │ PaymentClient           │ FlashSaleService
              ▼                         ├── PUT /internal/skus/{sku}/stock
     payment-service :8082              │     inventory DB  available = 1
     FailureRateController              ├── DEL stock:{sku}   (Redis)
     PaymentFailureRate                 └── N × OrderApplicationService.createOrder
     (AtomicInteger)                          order DB + outbox  (same transaction)
                                              existing Kafka saga
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | No new modules. payment-service failure rate is mutable. order-service admin flash sale and failure-rate proxy. `web/` `/ops` page. |
| REST (public) | `GET`/`PUT /api/admin/failure-rate` body `{ "percent" }`. `POST /api/admin/flash-sale` request `{ "sku", "concurrentOrders" }`, response `{ "confirmed", "cancelled", "concurrentOrders", "sku", "orders": [{ "orderId", "status", "cancelReason" }] }`. `GET /api/admin/stats` already existed; the page reads `ordersByStatus`, `outboxBacklog`, `dltCounts`. |
| REST (internal) | inventory-service `PUT /internal/skus/{sku}/stock` body `{ "available" }`. Unknown SKU is 404 problem+json. |
| Kafka consume / produce | No new topics or event types. Flash sale does not publish to Kafka. Each order goes through `OrderApplicationService.createOrder`, which still writes the outbox in the order transaction. |
| DB tables (per service) | No new tables. `InventoryRepository.setAvailable` is `UPDATE inventory SET available = :available, version = version + 1 WHERE sku = :sku`. |
| Redis keys | Deletes existing `stock:{sku}` before the burst so the catalog cache cannot serve a stale count for up to 5 seconds. No new key. |
| Tests | `PaymentGatewayTest` (rate 0 accepts `ok`, rate 100 rejects `ok`, `fail` rejects at 0, percent outside 0–100 throws). `AcceptanceFlashSaleTest` (profile `acceptance` only). |
| Docker / compose | payment-service env `PAYMENT_FAILURE_RATE: 0`. order-service env `PAYMENT_SERVICE_URL: http://payment-service:8082`. |

### Events (this phase only)

No new Kafka events. The ops feed reads the existing global SSE stream. A flash sale at failure rate 0 produces the existing saga types (`order.created`, `inventory.reserved` or `inventory.rejected`, `payment.succeeded`, `order.cancelled`).

`GET /api/admin/stats` sums the latest offsets of `orders.events.DLT`, `inventory.events.DLT`, and `payments.events.DLT`. Those topics were created in earlier phases.

### Architecture contracts (document if touched this phase)

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Transactional outbox | `FlashSaleService` calls `OrderApplicationService.createOrder`. It does not publish to Kafka. |
| Idempotent consumers (`processed_events`) | Unchanged. Each flash-sale order uses a new idempotency key `flash-{uuid}`. |
| `paymentToken` only on `order.created` + payment DB | Unchanged. Flash-sale orders send token `ok` through the existing create path. |
| Redis `idem:{key}` after order DB commit | Unchanged. Still written by `createOrder` after the order insert commits. |
| `inventory.reserved` stock-only payload | Unchanged. |
| Cancel vs in-flight charge | Unchanged. The endpoint does not override the failure rate. At 100% the reserved order can still be declined and stock released. |
| Stock decrement | Unchanged atomic conditional `UPDATE`. The flash-sale reset is a separate `UPDATE ... SET available = :available` on that SKU only, committed before the burst starts. |
| Money | Unchanged integer cents. Flash sale does not introduce a new price field. |

---

## Happy path (as implemented)

1. `web/app/ops/page.tsx` renders `OpsDashboard`. `FailureRateControl` loads `GET /api/admin/failure-rate`. `AdminController.failureRate` forwards through `PaymentClient` to payment-service `FailureRateController.get`, which returns `FailureRateResponse` `{ "percent" }` from `PaymentFailureRate`.
2. Moving the slider PUTs `{ "percent" }` after a 300ms debounce. `FailureRateController.put` calls `PaymentFailureRate.setPercent`. `PaymentGateway.charge` reads that value on every charge. Compose and the acceptance harness both set `PAYMENT_FAILURE_RATE` (the placeholder in `payment-service` `application.yml`).
3. `OpsLiveFeed` fetches `GET /api/stream/events` (`Accept: text/event-stream`) through the existing stream route. Rows show type, `orderId` (link to `/orders/{id}`), and time. Newest first, cap 100. The stream does not replay history.
4. Every 2 seconds `OpsDashboard` polls `GET /api/products` and `GET /api/admin/stats`. `OpsStockTable` shows the catalog. `OpsStatusPanel` shows counts for `PENDING`, `RESERVED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, the outbox backlog, one DLT total, and the three topic counts.
5. Flash sale: `FlashSalePanel` POSTs `{ sku, concurrentOrders }` (default `LASTITEM`, N=50, range 1–100). `AdminController.flashSale` rejects a blank SKU or a count outside 1–100 with 400. `FlashSaleService.run` calls `InventoryClient.setAvailable` (`PUT /internal/skus/{sku}/stock`), then `StockCacheService.invalidate`. A fixed pool calls `OrderApplicationService.createOrder` once per order (qty 1, token `ok`, its own idempotency key). It polls those ids until every status is `CONFIRMED`, `CANCELLED`, `SHIPPED`, or `DELIVERED`, or 120 seconds. The JSON record is `FlashSaleResult(confirmed, cancelled, concurrentOrders, sku, orders)`.
6. With failure rate 0 and stock reset to 1, one order is reserved and charged; the rest are `CANCELLED` with reason `out_of_stock`. The result panel shows the two counts in large type and the line “Exactly 1 confirmed and N-1 cancelled.”

---

## Failure / edge paths (if in scope for this phase)

- Percent outside 0–100: `PaymentFailureRate.InvalidPercentException` → payment-service 400 problem+json (`percent must be between 0 and 100`). The order-service proxy returns that status and body.
- Unknown SKU on the stock reset: inventory-service 404. `InventoryClient` throws `UnknownSkuException`. order-service `ApiExceptionHandler` returns 404.
- `concurrentOrders` outside 1–100, or a blank SKU: `OrderApplicationService.BadRequestException` → 400.
- Failure rate 100% and token `ok`: `PaymentGateway.charge` returns false. Observed in the browser: the order became `CANCELLED` with cancel reason `declined`, the ops feed showed `payment.failed` and `inventory.released`, and MILK available returned to the count from before the order.
- Token `fail` at rate 0 still declines (`PaymentGatewayTest`). Not re-checked in the browser this session.
- The HTTP rate limit (20/min on `POST /api/orders`) does not apply to flash sale, because those orders are created in-process. `AcceptanceA1Test` still posts 50 orders over HTTP and received 429 on this run. That limit is Phase 3. It was not changed here.

---

## How to verify (copy from phase doc + what you actually ran)

```bash
./mvnw -q verify
./mvnw -q verify -Pacceptance
docker compose up --build -d
# open http://localhost:3000/ops
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (exit 0, 2026-10-15). An earlier attempt in a sandbox that could not open the Docker socket or attach a JVM agent failed in `ReservationServiceTest` before any assertion. The unsandboxed rerun passed. |
| `./mvnw -q verify -Pacceptance` | **FAIL** (exit 1). Surefire: 9 tests, 0 failures, 2 errors. `AcceptanceFlashSaleTest` **PASS** in 14.15s (1 `CONFIRMED`, 9 `CANCELLED`, 10 rows, `LASTITEM` stock 0). Also **PASS**: A2 18.01s, A3 17.78s, A5 24.83s, A6 24.72s, A7 18.13s, A8 18.31s. `AcceptanceA4Test` **ERROR** in the suite: inventory-service did not log a listen port within 120s (class elapsed 256.4s) while Hibernate was still starting. Isolated rerun `./mvnw -q -pl acceptance-tests -Pacceptance test -Dtest=AcceptanceA4Test` **PASS** in 21.42s. `AcceptanceA1Test` **ERROR**: `create order failed: 429` `Rate limit exceeded for POST /api/orders` (50 HTTP posts, limit 20/min). Not a Phase 5 change. |
| `docker compose up --build -d` | **PASS**. After the rebuild, `GET /actuator/health` on ports 8080, 8081, and 8082 returned `{"status":"UP"}`. `GET http://localhost:3000/ops` returned 200. |
| Ops page render | **PASS**. Live feed, stock table (LASTITEM, MILK, EGGS), status counters, outbox backlog, and DLT total with `orders.events.DLT`, `inventory.events.DLT`, `payments.events.DLT` all rendered. |
| Failure-rate slider | **PASS**. Loaded at 0%. Setting it to 100% made `GET /api/admin/failure-rate` and `GET http://localhost:8082/admin/failure-rate` return `{"percent":100}`. Setting it back to 0% made payment-service return `{"percent":0}`. |
| 100% failure, one shop order | **PASS**. MILK was 48 available before the order. Checkout of MILK × 1, token `ok`, at 100%: order `7560bf7c-15d2-45ce-879e-3223b689ebc5` showed `CANCELLED`, history `payment.failed`, cancel reason `declined`. The ops feed (Live) showed `payment.failed` and `inventory.released` for that id. MILK returned to 48. An earlier order in the same session, `0065317d-f85b-44b3-8a71-74e6492f54b6`, had the same outcome; its events were already published before that ops view subscribed, so they were not replayed. |
| Flash sale N=50, rate 0, SKU LASTITEM | **PASS**. The result panel showed `LASTITEM · 50 orders`, **CONFIRMED 1**, **CANCELLED 49**, and “Exactly 1 confirmed and 49 cancelled.” The confirmed row was `83febd4f-bb50-40f8-9727-123eda6e30d1` (feed: `payment.succeeded`). Cancelled rows showed reason `out_of_stock`. `GET /api/products` then had LASTITEM `available` 0; the stock table updated to “Out of stock”. `GET /api/admin/stats` was `CONFIRMED` 3, `CANCELLED` 58 (the two manual declines plus the flash-sale orders, on top of the orders already in this database). |
| Narrow viewport (~390px) | **PASS** for overflow. `documentElement.scrollWidth` was 390. Header wraps: brand on the first row, Shop / Cart / Orders / Ops on the next, all inside the viewport. Flash-sale heading right edge was 357. A screenshot capture timed out; this check used layout metrics and the accessibility tree. |

---

## Key files (entry points)

| Path | Role |
|------|------|
| `payment-service/src/main/java/com/shop/payment/domain/PaymentFailureRate.java` | Mutable percent, 0–100 |
| `payment-service/src/main/java/com/shop/payment/domain/PaymentGateway.java` | Reads the rate on each `charge` |
| `payment-service/src/main/java/com/shop/payment/api/FailureRateController.java` | `GET`/`PUT /admin/failure-rate` |
| `payment-service/src/main/resources/application.yml` | `payment.failure-rate-percent: ${PAYMENT_FAILURE_RATE:0}` |
| `order-service/src/main/java/com/shop/order/api/AdminController.java` | Stats, failure-rate proxy, flash sale |
| `order-service/src/main/java/com/shop/order/domain/PaymentClient.java` | RestClient to payment-service |
| `order-service/src/main/java/com/shop/order/domain/FlashSaleService.java` | Reset, N creates, poll, result record |
| `order-service/src/main/java/com/shop/order/domain/InventoryClient.java` | `PUT /internal/skus/{sku}/stock` |
| `order-service/src/main/java/com/shop/order/domain/AdminStatsService.java` | `ordersByStatus`, `outboxBacklog`, `dltCounts` |
| `inventory-service/src/main/java/com/shop/inventory/api/InternalInventoryController.java` | Stock reset endpoint |
| `inventory-service/src/main/java/com/shop/inventory/persistence/InventoryRepository.java` | `setAvailable` |
| `acceptance-tests/src/test/java/com/shop/acceptance/AcceptanceFlashSaleTest.java` | N=10 LASTITEM |
| `acceptance-tests/src/test/java/com/shop/acceptance/PlatformTestHarness.java` | `PAYMENT_FAILURE_RATE=0` |
| `web/app/ops/page.tsx` | Ops route |
| `web/components/OpsDashboard.tsx` | Poll, layout |
| `web/components/OpsLiveFeed.tsx` | SSE feed |
| `web/components/FailureRateControl.tsx` | Slider |
| `web/components/FlashSalePanel.tsx` | Flash-sale form and result |
| `web/lib/api.ts` | `fetchAdminStats`, `fetchFailureRate`, `putFailureRate`, `postFlashSale` |
| `web/next.config.ts` | `/api/admin` rewrites |
| `docker-compose.yml` | `PAYMENT_FAILURE_RATE`, `PAYMENT_SERVICE_URL` |

---

## Not built yet (explicit gaps)

- ELK (`docker compose --profile elk`), trace id through Kafka headers, Kibana dashboard, Micrometer outbox/lag/DLT metrics (Phase 6)
- k6 load test, GitHub Actions CI, benchmark numbers (Phase 7)
- `AcceptanceA1Test` is still 50 HTTP `POST /api/orders` calls. With the Phase 3 limit of 20/min that suite case returns 429. Flash sale is the path that places the burst without that HTTP limit.

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md`
- Previous phase map: `phase-04-storefront.md`
