# Event-Driven Order Platform (Java · Kafka · Postgres · Next.js)

A grocery-style order system where **orders, inventory and payments are separate services that
only talk through Kafka events**. It is built to show the hard parts of distributed backends:
no overselling under concurrency, no lost or duplicated events, and automatic recovery when
a step fails (saga compensation). A storefront and an ops dashboard make all of that visible live.

> Decision note: Next.js and Vite are alternatives, so this spec uses **Next.js (App Router) + Tailwind**.

---

## Mental map (read this first)

```
 BROWSER                 EDGE                     BACKBONE                SERVICES (own DB each)
┌──────────────┐   ┌─────────────────┐      ┌───────────────┐       ┌──────────────────────┐
│ Storefront   │   │ order-service   │      │    KAFKA      │       │ order-service        │
│  cart/checkout│──▶│  REST + SSE     │─────▶│ orders.events │◀─────▶│  orders, outbox      │
│ Ops dashboard│◀──│  rate limit     │      │ inventory.ev. │       ├──────────────────────┤
│  live events │SSE│  idempotency key│      │ payments.ev.  │◀─────▶│ inventory-service    │
└──────────────┘   └───────┬─────────┘      │ *.DLT (dead)  │       │  stock, reservations │
                           │                └───────────────┘       ├──────────────────────┤
                           ▼                                         │ payment-service      │
                       ┌───────┐   Redis: rate limit, idempotency,   │  payments, refunds   │
                       │ Redis │          stock read-cache           └──────────────────────┘
                       └───────┘
   Logs (JSON) ──▶ Filebeat ──▶ Elasticsearch ──▶ Kibana          Postgres: 1 container, 3 databases
```

### The five ideas everything hangs on
1. **Choreographed saga**: no central brain. Each service reacts to events and emits new ones.
2. **Transactional outbox**: a state change and its event are written in ONE DB transaction; a
   publisher relays the outbox to Kafka. No "DB committed but event lost".
3. **Idempotent consumers**: Kafka delivers at-least-once, so every handler records `eventId`
   and ignores repeats.
4. **Atomic stock decrement**: `UPDATE ... WHERE available >= qty` under a row lock. The loser fails cleanly.
5. **Compensation**: if payment fails (or an order is cancelled after paying), stock is released / money refunded via events.

### Order lifecycle
```
Happy:   PENDING ─▶ RESERVED ─▶ CONFIRMED ─▶ SHIPPED ─▶ DELIVERED
Fail 1:  PENDING ─▶ CANCELLED            (inventory.rejected: out of stock)
Fail 2:  PENDING ─▶ RESERVED ─▶ CANCELLED (payment.failed → stock released)
Fail 3:  CONFIRMED ─▶ CANCELLED          (user cancels → refund + stock released)
```

## Docs
| File | What it answers |
|---|---|
| `.cursor/rules/project.mdc` | Always-on rules for Cursor: stack, invariants, working rules |
| `docs/01-requirements.md` | What must be built, with acceptance criteria |
| `docs/02-architecture.md` | How it works: services, events, data, APIs, decisions |
| `docs/03-phases.md` | Build order + a ready-to-paste prompt for each phase |
| `docs/04-interview-prep.md` | Talking points, likely questions, resume bullet templates |

## How to use with Cursor
1. Create an empty repo folder and unzip this pack into it (keep the hidden `.cursor/` folder).
2. Open the folder in Cursor. Check **Settings > Rules** shows `project` as an always-on rule.
3. Open a new **Agent** chat. Paste **Phase 1's prompt** from `docs/03-phases.md`. Cursor will read the `@docs/...` files it names.
4. Review the diff, run the verify steps, then start a **new chat** for the next phase.
5. Never skip the verify step at the end of a phase. Understand it before moving on.

Tip: for a big phase, switch the chat to Plan mode first so the agent proposes a plan before editing.

## Target repo layout
```
order-platform/
├── .cursor/rules/project.mdc     # agent rules
├── docker-compose.yml            # kafka, postgres, redis, 3 services, web (+ elk profile)
├── pom.xml                       # Maven parent (multi-module)
├── common-events/                # event envelope + event record types (shared contract only)
├── order-service/                # REST, SSE, rate limit, order state machine, outbox
├── inventory-service/            # stock, reservations, outbox
├── payment-service/              # simulated gateway, refunds, failure-rate config, outbox
├── web/                          # Next.js storefront + /ops dashboard
├── loadtest/                     # k6 scripts
├── observability/                # filebeat.yml, kibana export
├── reference/node-prototype/     # earlier Node version (read-only reference)
└── docs/
```
# order-platform
