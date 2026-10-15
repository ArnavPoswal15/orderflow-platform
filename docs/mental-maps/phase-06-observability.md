# Phase 6: Observability (ELK)

> **Status:** Complete  
> **Completed:** 2026-10-16  
> **Spec:** `docs/03-phases.md` — Phase 6

---

## One-screen picture

```
 Browser / curl
      │  POST /api/orders  (X-Trace-Id in / out)
      ▼
 order-service ──JSON logs──► /var/log/shop/order-service.json
      │ outbox + Kafka headers (orderId, eventId, traceId)
      ▼
 Kafka ◄──────────────────────────────────────────────► inventory-service, payment-service
      │                                                    │ JSON logs on shop-logs volume
      ▼                                                    ▼
 (profile elk)  filebeat ──► elasticsearch:9200 ──► kibana:5601
               index shop-logs-YYYY.MM.dd          dashboard order-platform

 Actuator (each service): /actuator/metrics/shop.outbox.backlog,
   shop.consumer.lag, shop.dlt.messages; order-service also shop.orders.created|cancelled
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | No new modules. `common-events` adds `TraceContext` + shared `logback-spring.xml`. All three Java services: JSON logging, Kafka trace headers, Micrometer gauges. |
| REST (public) | `X-Trace-Id` on order-service: accept UUID inbound, generate if missing, echo on response (`TraceIdFilter`). |
| Kafka consume / produce | `TraceRecordInterceptor` binds MDC from headers (fallback: envelope JSON). `OutboxPublisher` sends `ProducerRecord` with `orderId` / `eventId` / `traceId` headers. Saga child events use `TraceContext.currentOrNew()` so hops share one trace. |
| DB tables (per service) | Unchanged. `countByPublishedAtIsNull()` on all three `OutboxRepository` implementations for metrics. |
| Redis keys | Unchanged. |
| Tests | `common-events`: `TraceContextTest`, `JsonLogFormatTest` (Tomcat port regex for acceptance harness). `order-service` context test asserts `shop.outbox.backlog` registered. |
| Docker / compose | Profile `elk`: Elasticsearch 8.17, Kibana 8.17, Filebeat 8.17, `elastic-setup`, `kibana-setup`. Volume `shop-logs`. Java services: `SPRING_PROFILES_ACTIVE=elk-file`, mount `/var/log/shop`. |

### Events (this phase only)

No new Kafka event types or topics. Observability wraps the existing saga: logs and headers on the same envelope fields (`traceId` in JSON + MDC).

### Architecture contracts (document if touched this phase)

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Transactional outbox | Unchanged. Publish path adds Kafka headers from envelope; still no direct Kafka in domain services. |
| Idempotent consumers | Unchanged. `consumed` log only after `processed_events` insert succeeds. |
| `paymentToken` only on `order.created` + payment DB | Unchanged. Not added to logs beyond existing event types. |
| Redis `idem:{key}` after order DB commit | Unchanged. |
| `inventory.reserved` stock-only payload | Unchanged. |
| Cancel vs in-flight charge | Unchanged. |

---

## Happy path (as implemented)

1. `TraceIdFilter` sets MDC `traceId` from `X-Trace-Id` or a new UUID; response includes the header.
2. `OrderCreator` builds `order.created` with `TraceContext.currentOrNew()`, enqueues outbox, logs `order status` (`PENDING`), increments `shop.orders.created`.
3. `OutboxPublisher` (each service) deserializes outbox payload, sets MDC + Kafka headers, logs `published`, sends record, clears MDC.
4. `TraceRecordInterceptor` on consume: headers → MDC, else envelope; `afterRecord` clears MDC.
5. Consumers log `consumed` after dedupe insert; inventory `ReservationService` emits `inventory.reserved` with same trace; payment `chargeOnReserved` emits `payment.succeeded`.
6. `OrderApplicationService.transition` on `payment.succeeded` sets `CONFIRMED`, logs `order status` with `latencyMs` from `orders.created_at`.
7. Filebeat ships ndjson from `/var/log/shop/*.json` to `shop-logs-*`; Kibana data view `shop-logs*`, dashboard `order-platform`.

---

## Failure / edge paths (if in scope for this phase)

- `inventory.rejected` / `payment.failed`: `failureReason` in MDC/JSON (`out_of_stock`, `declined`); searchable in Kibana.
- HTTP cancel/ship/deliver after the saga: new `X-Trace-Id` for that request; `orderId` still correlates in Discover.
- Kafka admin for lag/DLT metrics: scheduled refresh after 120s initial delay; broker errors keep last gauge value (startup safe when Kafka excluded in tests).

---

## How to verify (copy from phase doc + what you actually ran)

```bash
./mvnw -q verify
docker compose --profile elk up --build -d
# place order
IDEM=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -sS -D /tmp/h.txt -o /tmp/o.json -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEM" \
  -d '{"items":[{"sku":"MILK","qty":1}],"paymentToken":"ok"}'
# Kibana http://localhost:5601 — Discover: orderId: "<id from o.json>"
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (exit 0, 2026-10-16). |
| `docker compose --profile elk up --build -d` | **PASS** (stack healthy: ES, Kibana, Filebeat, three Java services). |
| Actuator health 8080/8081/8082 | **PASS** `{"status":"UP"}`. |
| `shop.outbox.backlog` | **PASS** (e.g. `0.0` on order-service after run). |
| Order → CONFIRMED | **PASS** order `f0e2cebd-3776-431e-a084-aa2e549372f0`, trace `e42eb012-aef5-4e24-9f30-e4b1c944c438`, CONFIRMED on poll 2. |
| ES `term` on `orderId` | **PASS** — publish/consume trail from **order-service**, **inventory-service**, **payment-service** (`order.created` → `inventory.reserved` → `payment.succeeded`); `latencyMs` **380** on CONFIRMED line. |
| Kibana dashboard `order-platform` | **PASS** — `GET /api/saved_objects/dashboard/order-platform` returns title “Order platform”. |
| `kibana-setup` import | **PASS** after removing legacy `migrationVersion` from `observability/order-platform.ndjson` (Kibana 8.17 rejects it); re-run setup container exits 0. |
| Phase 6 verify agent | **Stopped** mid-run with Cursor **usage limit** (not an app failure); ELK checks and docs finished in follow-up passes. Also verified happy path order `ff71fd23-afa0-4d3f-ab77-bd4e0aca6507` (trace `5b0032e8-a6e1-4eff-91cb-8cb17986fd6f`, `latencyMs` 1329). |

---

## Key files (entry points)

| Path | Role |
|------|------|
| `common-events/src/main/java/com/shop/events/TraceContext.java` | MDC/header names, bind/clear, `currentOrNew()`, header map from envelope |
| `common-events/src/main/resources/logback-spring.xml` | Logstash JSON console; profile `elk-file` → `/var/log/shop/${spring.application.name}.json` |
| `order-service/.../TraceIdFilter.java` | HTTP `X-Trace-Id` |
| `*/messaging/TraceRecordInterceptor.java` | Kafka consumer MDC |
| `*/messaging/OutboxPublisher.java` | Headers + `published` log |
| `*/messaging/ShopMetrics.java` | Gauges: outbox backlog, consumer lag, DLT |
| `order-service/.../OrderMetrics.java` | `shop.orders.created`, `shop.orders.cancelled` |
| `docker-compose.yml` | Profile `elk`, `shop-logs` volume, `elk-file` on Java services |
| `observability/filebeat.yml` | Filestream inputs, index `shop-logs-%{+yyyy.MM.dd}` |
| `observability/index-template.json` | Keyword fields for correlation ids |
| `observability/order-platform.ndjson` | Kibana saved objects |
| `observability/README.md` | Kibana URL, KQL examples |

---

## Not built yet (explicit gaps)

- k6 load test, GitHub Actions CI, `docs/benchmarks.md` with real numbers (Phase 7)
- Prometheus scrape wiring in compose (metrics exposed on `/actuator/prometheus`; no scraper service)

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md` §9
- Previous phase map: `phase-05-ops-dashboard.md`
