# Order Platform

Event-driven microservice backend for grocery-style orders: **order**, **inventory**, and **payment** services each own a database and coordinate through **Kafka** using a choreographed saga (no central orchestrator).

**Status:** Phases 1–2 implemented (happy path + failure/cancel/refund). Storefront, SSE, and ops UI are planned in later phases.

## Architecture

```mermaid
flowchart LR
  %% Node style defaults
  classDef cluster fill:#f6f8ff,stroke:#3b82f6,stroke-width:2px,stroke-dasharray:0,rx:8,ry:8;
  classDef service fill:#fff7ed,stroke:#fb923c,stroke-width:1.5px,rx:6,ry:6;
  classDef data fill:#ecfeff,stroke:#06b6d4,stroke-width:1.5px,rx:6,ry:6;
  classDef edge fill:#eef2ff,stroke:#6366f1,stroke-width:1.5px,rx:6,ry:6;
  classDef msg fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,rx:6,ry:6;
  classDef clients fill:#fff1f2,stroke:#ef4444,stroke-width:1.5px,rx:6,ry:6;
  linkStyle default stroke:#94a3b8,stroke-width:1.2px;

  subgraph clients [Clients]
    direction TB
    API["HTTP clients<br/>(API & tests)"]:::clients
  end

  subgraph edge [Edge / API Gateway]
    direction TB
    OS["order-service<br/>REST API"]:::edge
  end

  subgraph backbone [Messaging / Kafka]
    direction TB
    K["Kafka<br/>orders · inventory · payments<br/>+ DLT topics"]:::msg
  end

  subgraph services [Services]
    direction LR
    IS["inventory-service"]:::service
    PS["payment-service"]:::service
  end

  subgraph data [Data stores]
    direction LR
    PG[("PostgreSQL<br/>orders · inventory · payments")]:::data
    R[("Redis<br/>idemp + caches")]:::data
  end

  %% Connections (with colored arrows)
  API -->|HTTP| OS
  OS -->|writes| PG
  OS -->|cache| R
  IS -->|reads/writes| PG
  PS -->|reads/writes| PG

  OS <-->|publish / consume| K
  IS <-->|publish / consume| K
  PS <-->|publish / consume| K

  %% Extra labels for core patterns
  pattern1["Transactional outbox<br/>(state+event tx)"]:::msg
  pattern2["Idempotent consumers<br/>(processed_events)"]:::msg
  OS --- pattern1
  IS --- pattern2

  %% Apply cluster look for boxes
  class clients,edge,backbone,services,data cluster;
```

**Core patterns:** transactional outbox (state + event in one DB transaction), idempotent consumers (`processed_events`), per-order Kafka keys, atomic stock updates, and compensation events (`inventory` compensation on failure).

| Happy path | `PENDING` → `RESERVED` → `CONFIRMED` |
| Failure | OOS → `CANCELLED`; payment decline → `CANCELLED` + stock released |
| Cancel | `POST /api/orders/{id}/cancel` → refund (if paid) + release stock |

## Stack

Java 21 · Spring Boot 3 · Maven · Kafka (KRaft) · PostgreSQL 16 · Redis 7 · Flyway · Testcontainers

## Quick start

**Prerequisites:** Docker, JDK 21.

```bash
# Build and unit/integration tests
./mvnw verify

# Full stack (Kafka, Postgres, Redis, three services)
docker compose up --build

# Acceptance scenarios A1–A7 (Docker; forks JVM services)
./mvnw -pl acceptance-tests -Pacceptance test
```

**API (order-service, default port 8080):**

- `POST /api/orders` — create order (`Idempotency-Key` header required; payment token `ok` or `fail`)
- `GET /api/orders/{id}`
- `POST /api/orders/{id}/cancel`

## Repository layout

| Path | Role |
|------|------|
| `common-events/` | Shared event envelope and payload types |
| `order-service/` | Public REST API, order state, outbox |
| `inventory-service/` | Stock and reservations |
| `payment-service/` | Simulated gateway, charges and refunds |
| `acceptance-tests/` | End-to-end scenarios (profile `acceptance`) |
| `docs/` | Requirements, architecture, phased build plan |
| `reference/node-prototype/` | Earlier Node reference (read-only) |

## Documentation

| Document | Contents |
|----------|----------|
| [docs/01-requirements.md](docs/01-requirements.md) | Functional requirements and acceptance criteria |
| [docs/02-architecture.md](docs/02-architecture.md) | Services, events, data model, invariants |
| [docs/03-phases.md](docs/03-phases.md) | Implementation phases and verify steps |
| [docs/mental-maps/](docs/mental-maps/) | What is implemented after each phase |
