# Load test benchmarks

Record **only numbers from your own machine**. Do not commit invented timings or throughput figures.

## Prerequisites

- Full stack running: `docker compose up --build` (order-service `:8080`, inventory-service `:8081`).
- [k6](https://k6.io/docs/get-started/installation/) installed locally.

Each VU sends a unique `X-Forwarded-For` (`10.0.<vu>.0`) so the 20/min rate limit on `POST /api/orders` does not throttle the race (see `RateLimitFilter`).

## Command

```bash
k6 run loadtest/orders.js
```

Optional overrides:

```bash
BASE_URL=http://localhost:8080 INVENTORY_URL=http://localhost:8081 SKU=LASTITEM INITIAL_STOCK=1 VUS=50 k6 run loadtest/orders.js
```

## Run record (fill after a local run)

| Field | Value |
|-------|-------|
| Date | |
| Host / OS | |
| k6 version (`k6 version`) | |
| `INITIAL_STOCK` | |
| `VUS` | |

### k6 summary (paste full text summary from stdout)

```
(paste here)
```

### Oversell verification

```
(paste OVERSOLD_CHECK line from teardown)
```

### Notes

- 
