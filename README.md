# Orderflow Platform

[![CI](https://github.com/ArnavPoswal15/orderflow-platform/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/ArnavPoswal15/orderflow-platform/actions?query=branch%3Amaster)

Event-driven order platform for grocery-style orders: **order**, **inventory**, and **payment** services each own a database and coordinate through **Kafka** using a choreographed saga (no central orchestrator).

**Status:** Phases 1–7 complete (saga, storefront, ops, ELK observability, k6 proof load test, GitHub Actions CI, demo docs).

## Architecture

```mermaid
flowchart TB
  %% Improved contrast + spacing to avoid overlap
  %% Global class defs (explicit text color and font size for readability)
  classDef cluster fill:#f8fafc,stroke:#0f172a,stroke-width:2px,rx:8,ry:8,color:#0f172a,font-size:14px;
  classDef service fill:#fff7ed,stroke:#b45309,stroke-width:1.5px,rx:6,ry:6,color:#0f172a,font-size:14px;
  classDef data fill:#ecfeff,stroke:#075985,stroke-width:1.5px,rx:6,ry:6,color:#0f172a,font-size:14px;
  classDef edge fill:#eef2ff,stroke:#3730a3,stroke-width:1.5px,rx:6,ry:6,color:#0f172a,font-size:14px;
  classDef msg fill:#f0fdf4,stroke:#166534,stroke-width:1.5px,rx:6,ry:6,color:#0f172a,font-size:14px;
  classDef clients fill:#fff1f2,stroke:#b91c1c,stroke-width:1.5px,rx:6,ry:6,color:#0f172a,font-size:14px;
  linkStyle default stroke:#94a3b8,stroke-width:1.2px;

  %% Top: clients -> edge -> messaging -> services -> data (vertical flow to reduce overlaps)
  subgraph clients [Clients]
    direction TB
    WEB["Storefront\nNext.js :3000"]:::clients
    OPS["Ops dashboard\n/ops"]:::clients
    API["HTTP clients\n(API, k6, tests)"]:::clients
  end

  subgraph edge [Edge / API Gateway]
    direction TB
    OS["order-service\nREST API"]:::edge
  end

  subgraph backbone [Messaging / Kafka]
    direction TB
    K["Kafka\norders · inventory · payments\n+ DLT topics"]:::msg
  end

  subgraph services [Services]
    direction LR
    IS["inventory-service"]:::service
    PS["payment-service"]:::service
  end

  subgraph data [Data stores]
    direction LR
    PG[("PostgreSQL\norders · inventory · payments")]:::data
    R[("Redis\nidemp + caches")]:::data
  end

  %% Connections (spaced out, with labels kept short)
  WEB -->|same-origin /api| OS
  OPS -->|/api/admin, SSE| OS
  API -->|HTTP| OS
  OS -->|writes| PG
  OS -->|cache| R

  OS <-->|pub / cons| K
  K --> IS
  K --> PS

  IS -->|reads/writes| PG
  PS -->|reads/writes| PG

  subgraph observability [Observability optional profile elk]
    direction LR
    FB["Filebeat"]:::data
    ES[("Elasticsearch")]:::data
    KB["Kibana :5601"]:::edge
    FB --> ES --> KB
  end

  OS -.->|JSON logs volume| FB
  IS -.->|JSON logs volume| FB
  PS -.->|JSON logs volume| FB

  %% Move patterns to the right in their own group so they don't cover nodes
  subgraph patterns [Core patterns]
    direction TB
    pattern1["Transactional outbox\n(state + event in one tx)"]:::msg
    pattern2["Idempotent consumers\n(processed_events)"]:::msg
  end

  %% Connect patterns with dashed lines to avoid visual overlap
  OS -.-> pattern1
  IS -.-> pattern2

  %% Explicit node styles to enforce dark text (fixes light-text on light backgrounds)
  style WEB fill:#fff1f2,stroke:#b91c1c,color:#071133,font-size:14px
  style OPS fill:#fff1f2,stroke:#b91c1c,color:#071133,font-size:14px
  style API fill:#fff1f2,stroke:#b91c1c,color:#071133,font-size:14px
  style OS  fill:#eef2ff,stroke:#3730a3,color:#071133,font-size:14px
  style K   fill:#f0fdf4,stroke:#166534,color:#071133,font-size:14px
  style IS  fill:#fff7ed,stroke:#b45309,color:#071133,font-size:14px
  style PS  fill:#fff7ed,stroke:#b45309,color:#071133,font-size:14px
  style PG  fill:#ecfeff,stroke:#075985,color:#071133,font-size:14px
  style R   fill:#ecfeff,stroke:#075985,color:#071133,font-size:14px
  style pattern1 fill:#f8fafc,stroke:#064e3b,color:#071133,font-size:13px
  style pattern2 fill:#f8fafc,stroke:#064e3b,color:#071133,font-size:13px

  %% Make sure cluster boxes are applied
  class clients,edge,backbone,services,data,observability cluster;
```

**Core patterns:** transactional outbox (state + event in one DB transaction), idempotent consumers (`processed_events`), per-order Kafka keys, atomic stock updates, and compensation events (`inventory-service` + payment cancellation).

| Happy path | `PENDING` → `RESERVED` → `CONFIRMED` |
| Failure | OOS → `CANCELLED`; payment decline → `CANCELLED` + stock released |
| Cancel | `POST /api/orders/{id}/cancel` → refund (if paid) + release stock |

## Stack

Java 21 · Spring Boot 3 · Maven · Kafka (KRaft) · PostgreSQL 16 · Redis 7 · Flyway · Testcontainers · Next.js · Tailwind · structured JSON logs (Logstash encoder) · Micrometer / Actuator metrics.

## Quick start

**Prerequisites:** Docker, JDK 21. Node.js is only needed to run the storefront outside Compose.

```bash
# Build and unit/integration tests
./mvnw verify

# Full stack (Kafka, Postgres, Redis, three services, storefront)
docker compose up --build

# Optional ELK (~4GB extra RAM) — observability/README.md; Kibana http://localhost:5601, Discover shop-logs*, orderId: "<uuid>"
docker compose --profile elk up --build

# Acceptance scenarios A1–A9 (Docker; forks JVM services)
./mvnw -pl acceptance-tests -Pacceptance test

# Load test — stack must be up; unique X-Forwarded-For per VU avoids rate limit
k6 run loadtest/orders.js

# Storefront dev server (order-service on localhost:8080)
cd web && npm run dev
```

Shop UI: [http://localhost:3000](http://localhost:3000). `npm run dev` uses that same port, so stop the Compose `web` service before starting it. order-service stays on port 8080.

**Storefront (`web/`):**

- `/` product grid; stock refreshes every 5 seconds
- `/cart` in `localStorage` (`shop.cart`); money is integer cents
- `/checkout` posts `{ items, paymentToken }` with `Idempotency-Key`. The same key is reused until the cart or payment token changes, then dropped after a successful POST. Token `ok` succeeds. Token `fail` triggers a simulated payment failure.
- `/orders` and `/orders/{id}` — the order page reads SSE from `/api/stream/orders/{id}` and refetches while the status is `PENDING` or `RESERVED`, so the timeline reaches `CONFIRMED` without a late race.

The browser calls same-origin `/api`. REST (`/api/products`, `/api/orders`) is rewritten to `ORDER_SERVICE_URL` (Compose: `http://order-service:8080`, local default `http://localhost:8080`). `/api/stream` and `/api/admin` are proxied similarly.

**API (order-service, default port 8080):**

- `GET /api/products`
- `POST /api/orders` — create order (`Idempotency-Key` header required; payment token `ok` or `fail`)
- `GET /api/orders` and `GET /api/orders/{id}`
- `POST /api/orders/{id}/cancel`
- `GET /api/stream/orders/{id}` — SSE for one order
- Ops UI at `/ops` (flash sale, failure-rate slider, live feed); admin APIs under `/api/admin/*`

## Load testing

With `docker compose up --build` healthy, run `k6 run loadtest/orders.js`. The script sets payment failure rate to 0%, resets **LASTITEM** stock, fires concurrent orders (default 50 VUs), then asserts oversell protection.

Record your machine’s numbers in [docs/benchmarks.md](docs/benchmarks.md) (template only in git—do not commit invented figures). See [loadtest/README.md](loadtest/README.md).

## CI

GitHub Actions workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on push/PR to `master`: JDK 21 (Temurin), Docker available for Testcontainers, `./mvnw -q verify`. Acceptance tests are optional and can be run with the `acceptance` profile.

## Demo

Timed walkthrough: [docs/DEMO.md](docs/DEMO.md) (~3 minutes: happy path, `fail` token, flash sale, optional k6/ELK mention).

### Screenshots

![Storefront product grid](docs/assets/storefront.svg)

![Order timeline SSE](docs/assets/order-timeline.svg)

![Ops flash sale result](docs/assets/ops-flash-sale.svg)

![Kibana trace by orderId](docs/assets/kibana-trace.svg)

Capture instructions: [docs/assets/README.md](docs/assets/README.md).

## Repository layout

| Path | Role |
|------|------|
| `common-events/` | Shared event envelope and payload types |
| `order-service/` | Public REST API, order state, outbox |
| `inventory-service/` | Stock and reservations |
| `payment-service/` | Simulated gateway, charges and refunds |
| `acceptance-tests/` | End-to-end scenarios (profile `acceptance`) |
| `loadtest/` | k6 race script (`orders.js`) and oversell checks |
| `.github/workflows/` | CI (`./mvnw verify`) |
| `web/` | Next.js storefront (shop, cart, checkout, live order page, `/ops`) |
| `observability/` | ELK config, Kibana dashboard export, operator docs |
| `docs/benchmarks.md` | Paste real k6 output from your runs |
| `docs/DEMO.md` | 3-minute demo script |
| `docs/assets/` | Screenshot placeholders for README |
| `docs/` | Requirements, architecture, phased build plan |
| `reference/node-prototype/` | Earlier Node reference (read-only) |

## Documentation

| Document | Contents |
|----------|----------|
| [docs/01-requirements.md](docs/01-requirements.md) | Functional requirements and acceptance criteria |
| [docs/02-architecture.md](docs/02-architecture.md) | Services, events, data model, invariants |
| [docs/03-phases.md](docs/03-phases.md) | Implementation phases and verify steps |
| [docs/mental-maps/](docs/mental-maps/) | What is implemented after each phase |
| [docs/benchmarks.md](docs/benchmarks.md) | Load test results template |
| [docs/DEMO.md](docs/DEMO.md) | Live demo script |
| [docs/Frontend-guidelines.md](docs/Frontend-guidelines.md) | Next.js storefront conventions (`web/`) |
