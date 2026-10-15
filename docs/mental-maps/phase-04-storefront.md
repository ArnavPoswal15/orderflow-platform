# Phase 4: Storefront (Next.js)

> **Status:** Complete  
> **Completed:** 2026-10-15  
> **Spec:** `docs/03-phases.md` — Phase 4

---

## One-screen picture

```
 Browser :3000
      │
      ▼
 web (Next.js standalone, node server.js)
      │
      ├── RSC fetch ──────────► ORDER_SERVICE_URL (compose: http://order-service:8080)
      ├── rewrite /api/products, /api/orders* ──► order-service :8080
      └── route /api/stream/* (stream, no 30s rewrite proxy)
                                      │
                                      ▼
                              order-service edge (Phase 3)
                              products, orders, cancel, SSE
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | `web/` Next.js 16 App Router storefront. No new Java modules. |
| REST (public) | Unchanged order-service API. The browser uses same-origin `/api/products`, `/api/orders`, `/api/orders/{id}/cancel`. |
| Kafka consume / produce | No new topics or consumers. The order page reads existing SSE frames and refetches the order. |
| DB tables (per service) | Unchanged |
| Redis keys | Unchanged |
| Tests | No new JUnit tests. Browser checks below. |
| Docker / compose | `web` service, port 3000, build arg `ORDER_SERVICE_URL=http://order-service:8080`. Service Dockerfiles copy `acceptance-tests` so the parent POM resolves. |

### Events (this phase only)

No new Kafka events. The order page reads existing SSE frames (`order.created`, `inventory.reserved`, `payment.succeeded`, `payment.failed`, `order.cancelled`, and the other saga types) and refetches `GET /api/orders/{id}`. While status is `PENDING` or `RESERVED` it also refetches every 400ms, because `payment.succeeded` can be published before the browser is subscribed and that stream does not replay.

### Architecture contracts (document if touched this phase)

Saga contracts (outbox, idempotent consumers, `paymentToken` placement, Redis `idem:{key}`, stock payload, cancel-vs-charge) are unchanged. The storefront only calls the Phase 3 edge.

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Edge idempotency | Checkout stores `{fingerprint, key}` in `sessionStorage` (`shop.checkoutAttempt`). The fingerprint is the JSON of `{items, paymentToken}`. The same key is reused until that body changes. The key is removed only after a successful POST. |
| Rate limit | `parseProblem` reads `Retry-After` and `X-RateLimit-*`. Checkout shows `ProblemAlert` and a retry countdown on 429. |

---

## Happy path (as implemented)

1. `app/page.tsx` server-renders `GET /api/products` and passes the list to `ProductGrid`, which polls `GET /api/products` every 5s. Add to cart is disabled when `available === 0`.
2. `CartProvider` (`web/lib/cart.tsx`) keeps lines in `localStorage` (`shop.cart`). `/cart` adjusts qty and shows a subtotal from cached `priceCents`.
3. `/checkout` posts `{ items, paymentToken }` to `POST /api/orders` with the stored `Idempotency-Key`, then `router.push(/orders/{id})` and clears the cart.
4. `app/orders/[id]/page.tsx` loads the order and mounts `OrderLivePanel`. That fetches `GET /api/stream/orders/{id}` (`Accept: text/event-stream`) and refetches the order on connect, on each parsed frame, and every 400ms while the status is `PENDING` or `RESERVED`. A snapshot with an older `updatedAt` is ignored. `OrderTimeline` highlights the current status.
5. Cancel is shown for `PENDING`, `RESERVED`, and `CONFIRMED`. It posts `{ reason: "customer_requested" }`.

---

## Failure / edge paths (if in scope for this phase)

- Token `fail` ends `CANCELLED` with history `payment.failed` and cancel reason `declined`.
- 429 renders `ProblemAlert` plus a countdown from `Retry-After`. Not exercised in the browser this session.
- Illegal cancel is meant to show the 409 problem `detail`. Not produced in the browser: `OrderStateMachine` treats a same-status transition as legal, so cancelling an already `CANCELLED` order returned **202**, not 409.
- `/ops` is not in this phase.

---

## How to verify (copy from phase doc + what you actually ran)

```bash
docker compose up --build -d
# open http://localhost:3000
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | Not run. No Java source changes. |
| `docker compose up --build` | **FAIL** on the first attempt (2026-10-15): service images could not read the parent POM because `acceptance-tests` was not copied. **PASS** after adding `COPY acceptance-tests` to the three service Dockerfiles. `web` came up on port 3000. `GET http://localhost:3000/api/products` returned MILK, EGGS, LASTITEM. |
| Shop, cart, checkout | **PASS**. Product grid showed live stock. Cart subtotal used `priceCents`. Checkout hint mentions token `fail`. Cart was empty after a successful submit. |
| Happy path timeline | **PASS** (2026-10-15), current live panel, `next start` on `127.0.0.1:3001`. Order `36c14097-35c1-47a1-8d92-c0cac4eea9fd` (MILK × 1, token `ok`) showed **CONFIRMED**, Live, and `payment.succeeded` with no later event and no reload. `GET /api/orders/{id}` on `:8080` was `CONFIRMED` with history `order.created`, `inventory.reserved`, `payment.succeeded`. An earlier build of this page stayed **RESERVED** after the API was already **CONFIRMED**; the 400ms catch-up is what this run exercised. |
| Token `fail` | **PASS** (2026-10-15), same `next start` on `127.0.0.1:3001`. Order `50b4c9f2-256f-471b-af2e-80333f9860b1` (EGGS × 1, token `fail`) showed **CANCELLED**, Live, `payment.failed`, and cancel reason `declined`. API history: `order.created`, `inventory.reserved`, `payment.failed`. |
| Order history | **PASS**. `/orders` listed id, status, total, and created time (6 orders on the running stack). |
| Mobile width | **PASS** at 390px. Header wraps (Shop / Cart / Orders). Product cards stack in one column. |
| 429 UI | Not run. |
| 409 UI | Not observed. Repeat cancel of a `CANCELLED` order via the proxy returned 202. |

The two timeline rows above used `cd web && npm run lint && npm run build && npm run start -- --hostname 127.0.0.1 --port 3001` against order-service on `127.0.0.1:8080`. `docker compose up --build -d web` then recreated `order-platform-web-1`. `GET http://127.0.0.1:3000/checkout` returned 200 and the reused-key copy.

---

## Key files (entry points)

| Path | Role |
|------|------|
| `web/next.config.ts` | `output: "standalone"`, REST rewrites, long `proxyTimeout` |
| `web/app/api/stream/[...path]/route.ts` | Streams SSE from order-service |
| `web/lib/server-api.ts` | Server-only `getOrderServiceUrl`, `getProducts`, `getOrder`, `getOrderPage` |
| `web/lib/api.ts` | Client `/api` calls, `parseProblem`, `postOrder` |
| `web/lib/cart.tsx` | `CartProvider`, `localStorage` key `shop.cart` |
| `web/components/ProductGrid.tsx` | 5s stock poll |
| `web/components/OrderLivePanel.tsx` | Fetch SSE reader, 400ms in-flight catch-up, cancel |
| `web/components/OrderTimeline.tsx` | Status rail and history |
| `web/Dockerfile` | Multi-stage `npm ci`, `next build`, `node server.js` on 3000 |
| `docker-compose.yml` | `web` service |

---

## Not built yet (explicit gaps)

- `/ops` live feed, stock table, failure-rate slider, flash sale (Phase 5)
- ELK, trace propagation, Micrometer dashboards (Phase 6)
- k6 load test, GitHub Actions, benchmark numbers (Phase 7)

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md` section 8
- Previous phase map: `phase-03-edge-api.md`
