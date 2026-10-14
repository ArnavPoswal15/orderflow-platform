# Changelog
(The agent appends one entry per completed phase.)

## Pre-Phase 1 — architecture contract fixes (docs)
- Payment token stored by payment-service on `order.created`; `inventory.reserved` payload is stock facts only (`items`).
- Documented cancel vs in-flight charge handling (FR-16a, architecture §5.5).
- Order idempotency: Redis `idem:{key}` set after DB commit (architecture §5.4, FR-4).

## Phase 1 — Foundations + happy-path saga (2026-10-15)
- Maven multi-module parent (Spring Boot **3.5.16**, Java 21): `common-events`, `order-service`, `inventory-service`, `payment-service`.
- `docker-compose.yml`: Kafka 3.8 KRaft, Postgres 16 (init creates `orders` / `inventory` / `payments` DBs), Redis 7, three services.
- Flyway V1 schemas per architecture §4; seed SKUs MILK, EGGS, LASTITEM (stock 1).
- Happy path: `POST /api/orders` → `order.created` → reserve → `inventory.reserved` → charge → `payment.succeeded` → `CONFIRMED`.
- Transactional outbox + idempotent consumers (`processed_events`) in all three services.
- Payment: token/amount on `order.created`; charge on `inventory.reserved`; `cancel_requested` column on `payments`.
- Order edge: `Idempotency-Key` required; Redis `idem:{key}` after DB commit; internal price lookup via `inventory-service` `GET /internal/skus`.
