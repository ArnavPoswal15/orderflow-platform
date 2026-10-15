# Phase 7: Proof and polish

> **Status:** Complete  
> **Completed:** 2026-10-16  
> **Spec:** `docs/03-phases.md` — Phase 7

---

## One-screen picture

```
 k6 loadtest/orders.js
      │ setup: PUT failure-rate 0, PUT stock N (inventory :8081)
      │ race: POST /api/orders LASTITEM (unique X-Forwarded-For per VU)
      │ teardown: poll stock + admin stats → OVERSOLD_CHECK
      ▼
 order-service :8080 ──► saga (unchanged) ──► inventory / payment
      ▲
 GitHub Actions CI: ./mvnw -q verify (Testcontainers, no acceptance profile)

 acceptance-tests AcceptanceA9Test: create → POST /ship → 409, still PENDING

 docs: benchmarks template, DEMO.md, assets placeholders; README badge + Mermaid ops/ELK
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | No new runtime services. `loadtest/orders.js`, `.github/workflows/ci.yml`, docs only. |
| REST (public) | Unchanged. k6 and acceptance tests exercise existing endpoints. |
| Kafka consume / produce | Unchanged. |
| DB tables (per service) | Unchanged. |
| Redis keys | Unchanged. k6 avoids shared rate-limit bucket via per-VU `X-Forwarded-For`. |
| Tests | `AcceptanceA9Test` (ship while `PENDING` → 409). Existing `OrderShipApiTest` in order-service. |
| Docker / compose | Unchanged. k6 expects compose ports 8080/8081. |

### Events (this phase only)

No new Kafka event types or topics.

### Architecture contracts (document if touched this phase)

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Transactional outbox | Unchanged; k6 teardown validates inventory + confirmed counts, not publish path. |
| Idempotent consumers | Unchanged; race load uses unique `Idempotency-Key` per VU. |
| `paymentToken` only on `order.created` + payment DB | Unchanged. |
| Redis `idem:{key}` after order DB commit | Unchanged. |
| `inventory.reserved` stock-only payload | Unchanged. |
| Cancel vs in-flight charge | Unchanged. |
| Atomic stock / no oversell | Proven under k6 concurrent `POST` on `LASTITEM` with post-run `OVERSOLD_CHECK`. |

---

## Happy path (as implemented)

1. **k6 `setup()`** — `PUT /api/admin/failure-rate` `{ "percent": 0 }`; `PUT /internal/skus/LASTITEM/stock` `{ "available": INITIAL_STOCK }`; snapshot `GET /api/admin/stats` → `ordersByStatus.CONFIRMED` baseline.
2. **k6 default** — each VU `POST /api/orders` with `paymentToken: "ok"`, `Idempotency-Key` unique, `X-Forwarded-For: 10.0.<vu>.0`.
3. Saga runs as in Phase 1–2: at most `INITIAL_STOCK` orders reach `CONFIRMED`; others `CANCELLED` (e.g. `out_of_stock`).
4. **k6 `teardown()`** — poll up to 120s; fail run if `available < 0` or `confirmedDelta > initialStock`; log `OVERSOLD_CHECK …`.
5. **CI** — push/PR to `main` runs `./mvnw -q verify` on Ubuntu with JDK 21.
6. **A9 acceptance** — `HttpTestClient.createOrder` then immediate `postShip` → 409; `GET` order still `PENDING`.

---

## Failure / edge paths (if in scope for this phase)

- k6 thresholds: expect HTTP 202 on create; teardown failure exits non-zero if oversell invariant breaks.
- A9: illegal ship on `PENDING` returns RFC 7807 conflict (409) without changing status.
- Rate limit: documented bypass for load test only (unique `X-Forwarded-For` per VU); production clients share real client identity.

---

## How to verify (copy from phase doc + what you actually ran)

```bash
./mvnw -q verify
./mvnw -pl acceptance-tests -Pacceptance test
docker compose up --build -d
k6 run loadtest/orders.js
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (exit 0, 2026-10-16). |
| `./mvnw -pl acceptance-tests -Pacceptance test` | **PASS** (10 tests, includes `AcceptanceA9Test`; `AcceptanceA1Test` uses per-thread `X-Forwarded-For`). |
| `docker compose up --build -d` | **PASS** (stack already healthy on 8080/8081). |
| `k6 run loadtest/orders.js` | **PASS** — `OVERSOLD_CHECK pass=true available=0 confirmedDelta=1 initialStock=1` (k6 v2.3.0). |

---

## Key files (entry points)

| Path | Role |
|------|------|
| `loadtest/orders.js` | k6 setup/race/teardown, `handleSummary`, `OVERSOLD_CHECK` |
| `loadtest/README.md` | One-paragraph how to run |
| `.github/workflows/ci.yml` | CI badge workflow |
| `acceptance-tests/.../AcceptanceA9Test.java` | End-to-end A9 |
| `acceptance-tests/.../HttpTestClient.java` | Optional `X-Forwarded-For` on create (A1 racers) |
| `acceptance-tests/.../AcceptanceA1Test.java` | Per-VU `10.0.<idx>.0` forwarded-for |
| `docs/benchmarks.md` | Template for real k6 numbers |
| `docs/DEMO.md` | 3-minute demo script |
| `docs/assets/README.md` | Screenshot capture list |
| `README.md` | Badge, load test / CI / demo sections, Mermaid ops + ELK |

---

## Not built yet (explicit gaps)

- Prometheus/Grafana scrape service in compose (Actuator `/actuator/prometheus` still manual).
- Committed benchmark numbers in `docs/benchmarks.md` (intentionally empty until you paste a local run).
- Binary screenshots under `docs/assets/` (README uses placeholders).

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md`
- Previous phase map: `phase-06-observability.md`
