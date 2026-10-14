# Event-Driven Order Processing

Node/Express API + Order, Inventory, Payment services talking over **Redis Streams**
(one consumer group per service, at-least-once, idempotent handlers). Postgres for state, Redis for
stock cache / idempotency keys / rate limiting.

## Run
    docker compose up --build
    ./scripts/demo.sh
    curl localhost:3000/orders/<id>     # status + event history

## Flow (choreographed saga)
    POST /orders -> order.placed -> [Inventory] reserve -> inventory.reserved -> [Payment] charge
        -> payment.succeeded -> order CONFIRMED -> POST /orders/:id/ship -> SHIPPED
    inventory.failed  -> order CANCELLED
    payment.failed    -> [Inventory] releases stock (compensation) + order CANCELLED

## Where each feature lives
1. Idempotency: `orders.js` createOrder (Redis fast path + Postgres UNIQUE backstop)
2. Race on last item: `inventory.js` reserve (conditional UPDATE under row lock, sorted SKUs)
3. Compensation: `inventory.js` release on `payment.failed`
4. State machine + history: `orders.js` TRANSITIONS, `order_events` table
5. Rate limit: `index.js` (sorted-set sliding window, RATE_LIMIT env, default 20/min)

## Known gaps (good interview talking points)
- No transactional outbox: a crash between DB commit and XADD can drop an event.
- Poison messages stay pending (no dead-letter stream / XAUTOCLAIM).
- Logs are JSON (pino) on stdout; add Filebeat + ELK to ship them.
- Shipping is a manual endpoint; refund() exists but isn't wired to an event.
