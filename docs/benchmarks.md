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
| Date | 2026-10-16 |
| Host / OS | MacBook Pro (Apple M5 Pro), 16 GB RAM, macOS, darwin/arm64 |
| k6 version (`k6 version`) | v2.3.0 (go1.27.1) |
| `INITIAL_STOCK` | 1 |
| `VUS` | 50 |

### k6 summary (paste full text summary from stdout)

```
     execution: local
        script: loadtest/orders.js

     scenarios: (100.00%) 1 scenario, 50 max VUs, 3m30s max duration (incl. graceful stop):
              * race: 1 iterations for each of 50 VUs (maxDuration: 3m0s, gracefulStop: 30s)

     ✓ order accepted (202)

     █ setup

       ✓ failure-rate reset
       ✓ stock reset
       ✓ baseline stats

   ✓ checks.........................: 100.00% ✓ 53       ✗ 0
     data_received..................: 30 kB   4.4 kB/s
     data_sent......................: 15 kB   2.1 kB/s
     http_req_blocked...............: avg=209.45µs min=2µs      med=177µs    max=718µs    p(90)=448µs    p(95)=583µs
     http_req_connecting............: avg=169.6µs  min=0s       med=135µs    max=602µs    p(90)=365µs    p(95)=470µs
     http_req_duration..............: avg=234.73ms min=4.65ms   med=267.52ms max=321.42ms p(90)=318.49ms p(95)=320.1ms
       { expected_response:true }...: avg=234.73ms min=4.65ms   med=267.52ms max=321.42ms p(90)=318.49ms p(95)=320.1ms
   ✓ http_req_failed................: 0.00%   ✓ 0        ✗ 61
     http_req_receiving.............: avg=318.86µs min=17µs     med=119µs    max=3.79ms   p(90)=678µs    p(95)=1.04ms
     http_req_sending...............: avg=51.11µs  min=5µs      med=23µs     max=311µs    p(90)=94µs     p(95)=169µs
     http_req_waiting...............: avg=234.36ms min=4.5ms    med=266.89ms max=321.3ms  p(90)=317.58ms p(95)=319.99ms
     http_reqs......................: 61      8.967662/s
     iteration_duration.............: avg=278.98ms min=237.15ms med=279.58ms max=324.59ms p(90)=321.77ms p(95)=322.05ms
     iterations.....................: 50      7.350543/s
     vus............................: 0       min=0      max=0

running (0m06.8s), 00/50 VUs, 50 complete and 0 interrupted iterations
race ✓ [======================================] 50 VUs  0m00.3s/3m0s  50/50 iters, 1 per VU
```

### Oversell verification

```
OVERSOLD_CHECK pass=true available=0 confirmedDelta=1 baselineConfirmed=10 confirmedNow=11 initialStock=1
```

### Notes

- Stack: `docker compose up` on the same machine (Kafka, Postgres, Redis, three JVM services, optional `web`).
- This script is a **correctness** load test (50-way race for 1 unit), not a max-throughput soak. The headline metric is `OVERSOLD_CHECK pass=true`, not requests/sec.
- `baselineConfirmed` / `confirmedNow` include orders already in the DB from earlier runs; only `confirmedDelta` is compared to `initialStock`.
- Wall clock ~6.8s includes setup, 50 concurrent `POST /api/orders` (all `202`), and teardown polling until saga outcomes settle.
