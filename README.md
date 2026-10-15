# Order Platform

Event-driven microservice backend for grocery-style orders: **order**, **inventory**, and **payment** services each own a database and coordinate through **Kafka** using a choreographed saga (no central orchestrator). The design targets correct stock under concurrency, at-least-once messaging without double effects, and explicit compensation when a step fails.

**Status:** Phases 1–2 implemented (happy path + failure/cancel/refund). Storefront, SSE, and ops UI are planned in later phases.

## Architecture

```mermaid
flowchart LR
  subgraph clients [Clients]
    API["HTTP clients / tests"]
  end

  subgraph edge [Edge]
    OS["order-service\nREST API"]
  end

  subgraph backbone [Messaging]
    K["Kafka\norders · inventory · payments\n+ DLT topics"]
  end

  subgraph services [Services]
    IS["inventory-service"]
    PS["payment-service"]
  end

  subgraph data [Data]
    PG[("PostgreSQL\norders · inventory · payments")]
    R[("Redis")]
  end

  API --> OS
  OS --> PG
  IS --> PG
  PS --> PG
  OS --> R
  OS <-->|"publish / consume"| K
  IS <-->|"publish / consume"| K
  PS <-->|"publish / consume"| K
```

**Core patterns:** transactional outbox (state + event in one DB transaction), idempotent consumers (`processed_events`), per-order Kafka keys, atomic stock updates, and compensation events (`inventory.rejected`, `payment.failed`, `payment.refunded`, `inventory.released`).

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
