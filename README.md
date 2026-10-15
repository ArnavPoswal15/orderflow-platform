# Order Platform

Event-driven microservice backend for grocery-style orders: **order**, **inventory**, and **payment** services each own a database and coordinate through **Kafka** using a choreographed saga (no central orchestrator).

**Status:** Phases 1–2 implemented (happy path + failure/cancel/refund). Storefront, SSE, and ops UI are planned in later phases.

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
    API["HTTP clients\n(API & tests)"]:::clients
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
  API -->|HTTP| OS
  OS -->|writes| PG
  OS -->|cache| R

  OS <-->|pub / cons| K
  K --> IS
  K --> PS

  IS -->|reads/writes| PG
  PS -->|reads/writes| PG

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
