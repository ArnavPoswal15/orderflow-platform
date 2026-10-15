# Acceptance tests (Phase 2)

Requires **Docker**. Each scenario starts Testcontainers (Postgres, Kafka, Redis) plus three service processes on random ports, each limited to 256 MB. Logs land in `acceptance-tests/target/acceptance-logs/`.

Default `./mvnw verify` **skips** this module (memory). Enable explicitly:

```bash
# All acceptance scenarios (slow; ~10+ minutes, high RAM)
./mvnw -pl acceptance-tests -Pacceptance test

# One scenario
./mvnw -pl acceptance-tests -Pacceptance test -Dtest=AcceptanceA2Test
```

A9 (illegal ship on `PENDING`) runs in `order-service`:

```bash
./mvnw -pl order-service test -Dtest=OrderShipApiTest
```
