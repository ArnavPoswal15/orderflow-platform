# Phase 2: Failure paths + reliability hardening

> **Status:** Complete  
> **Completed:** 2026-10-15  
> **Spec:** `docs/03-phases.md` — Phase 2

---

## One-screen picture

```
  Fail OOS: order.created → inventory.rejected → order CANCELLED
  Fail pay: inventory.reserved → payment.failed → order CANCELLED + inventory.released
  Cancel:   POST /cancel → order.cancelled → payment.refunded + inventory.released (if paid/reserved)
  Kafka:    3× backoff → <topic>.DLT
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Events | `inventory.rejected`, `inventory.released`, `payment.failed`, `payment.refunded`, `order.cancelled` payloads in `common-events` |
| Order-service | `OrderStateMachine`, cancel/ship APIs, consumers for `inventory.rejected` / `payment.failed`, `OrderView.cancelReason` |
| Inventory | OOS → `inventory.rejected` (no throw); `ReleaseService` on `payment.failed` + `order.cancelled` |
| Payment | `payment.failed` outbox; `order.cancelled` + refund; charge/cancel race (FR-16a) |
| Reliability | `KafkaListenerConfig` + DLT topic beans in all three services |
| Tests | `acceptance-tests` (A1–A7, profile `acceptance`); `OrderShipApiTest` (A9); `OrderStateMachineTest` |

---

## Verify results

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | **PASS** (2026-10-15; acceptance skipped by default) |
| `./mvnw -pl acceptance-tests -Pacceptance test` | Run locally with Docker (heavy; one scenario per class) |

---

## Key files

- `order-service/.../OrderStateMachine.java`, `OrderApplicationService.java`, `OrderController.java`
- `inventory-service/.../ReservationService.java`, `ReleaseService.java`
- `payment-service/.../PaymentApplicationService.java`
- `acceptance-tests/src/test/java/com/shop/acceptance/AcceptanceA*.java`

---

## Not built yet (later phases)

- Storefront UI, ops flash-sale / failure-rate controls (Phase 4–5)
- Full observability stack (Phase 6)
