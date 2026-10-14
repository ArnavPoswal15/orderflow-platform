# Interview prep

## 60-second pitch
"I built an event-driven order platform with Java, Spring Boot, Kafka and Postgres. Orders, inventory and payments are
independent services that communicate only through events. I used a choreographed saga for the order flow, a transactional
outbox so state changes and events can't diverge, and idempotent consumers because Kafka is at-least-once. Concurrent
orders for the last item can't oversell because stock is decremented with an atomic conditional update. If payment fails,
stock is released automatically. There's a Next.js storefront and an ops dashboard where I can fire a flash sale and watch
exactly one order win. I load-tested it and verified zero oversells."

## Questions you should be able to answer
1. **Why Kafka, and what does the key do?** Durable log, replay, consumer groups. Key = orderId keeps one order's events in order within a partition.
2. **Exactly-once?** Not really. We get at-least-once delivery plus idempotent processing, which gives effectively-once effects.
3. **What is the dual-write problem?** Writing to DB and Kafka separately can leave them inconsistent if you crash between them. The outbox fixes it with one transaction.
4. **What if the outbox publisher crashes after sending but before marking?** Event is sent twice. Consumers dedupe on `eventId`.
5. **How do you prevent overselling?** Atomic `UPDATE ... WHERE available >= qty`; the row lock serializes racers. Alternatives: optimistic version check, `SELECT FOR UPDATE`, Redis `DECR`.
6. **Choreography vs orchestration?** Chose choreography for decoupling. Downside is implicit flow, mitigated by event history and centralized logs. Orchestration (e.g. Temporal) would be the pick for long, complex workflows.
7. **What happens if payment-service is down?** Events wait in Kafka; the order stays `RESERVED`. Add a timeout/expiry job as a next step to release stale reservations.
7b. **Cancel while payment is in flight?** `order.cancelled` and `payment.succeeded` are on different topics — no ordering. Payment-service flags cancel and refunds if charge wins; order-service ignores `payment.succeeded` when already `CANCELLED`. Payment token never rides on `inventory.reserved`; it is stored on `order.created`.
8. **How would you scale this?** More partitions + consumer instances; hot SKUs need sharded counters or a Redis reservation pre-check; read models/CQRS for order lists; Debezium instead of polling.
9. **How do you handle poison messages?** Bounded retries with backoff, then dead-letter topic plus alerting.
10. **How is the API idempotent?** Idempotency-Key header, Redis fast path, DB unique constraint as the backstop, request hash to catch key reuse.
11. **What would you do differently in production?** Real payment provider with its own idempotency, Schema Registry (Avro/Protobuf), auth, multi-broker Kafka with replication 3, tracing with OpenTelemetry, a reservation-expiry job.

## Resume bullet templates (fill with YOUR measured numbers)
- Built an event-driven order platform (Java 21, Spring Boot, Kafka, PostgreSQL, Redis, Next.js) with a choreographed saga across order, inventory and payment services.
- Implemented transactional outbox and idempotent consumers to guarantee no lost or duplicated order events across [N] fault-injection tests.
- Eliminated overselling with atomic conditional stock updates; verified [0] oversells across [N] concurrent orders in k6 load tests at [X] orders/sec.
- Added saga compensation (stock release, refunds), dead-letter handling and Redis sliding-window rate limiting.
- Shipped structured JSON logging to ELK with per-order tracing, plus a live ops dashboard (SSE) for event monitoring and flash-sale simulation.

> Only use numbers your own runs produced.
