# Reference: Node.js prototype

A working Node/Express + Redis Streams + Postgres version of the core saga (reserve stock, charge, compensate,
idempotency key, sliding-window rate limit). It is **reference only**:

- Use it to see the intended behavior and the SQL for the atomic stock update.
- Do NOT copy its structure, modify it, or port it line by line. The Java platform adds Kafka, the
  transactional outbox, idempotent consumers via `processed_events`, DLTs, refunds and a UI.
- It is not part of the build, the Maven modules or docker-compose.
