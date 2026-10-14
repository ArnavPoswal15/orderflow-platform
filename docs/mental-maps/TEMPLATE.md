# Phase N: <title>

> **Status:** Not started | In progress | Complete  
> **Completed:** YYYY-MM-DD (omit if not complete)  
> **Spec:** `docs/03-phases.md` — Phase N

Use this file as the **mental map of what actually exists in the repo** after this phase.
Update it when the phase is done. Do not describe future phases or aspirational design.

**Agent rules (from `.cursor/rules/project.mdc`):** fill this file before marking the phase complete; set **Status: Complete** and date; update the status column in [README.md](./README.md).

---

## One-screen picture

```
(ASCII diagram: services, databases, Kafka topics, and the paths added in THIS phase only.)
```

---

## What this phase added

| Area | What exists now |
|------|-----------------|
| Modules / services | |
| REST (public) | |
| Kafka consume / produce | |
| DB tables (per service) | |
| Redis keys | |
| Tests | |
| Docker / compose | |

### Events (this phase only)

| Topic | Type | Producer | Consumer(s) | Payload (as implemented) |
|-------|------|----------|-------------|---------------------------|
| | | | | |

### Architecture contracts (document if touched this phase)

Only list items that **exist in code** after this phase—not the full target spec.

| Contract | How it is implemented in this phase |
|----------|-------------------------------------|
| Transactional outbox | |
| Idempotent consumers (`processed_events`) | |
| `paymentToken` only on `order.created` + payment DB | |
| Redis `idem:{key}` after order DB commit | |
| `inventory.reserved` stock-only payload | |
| Cancel vs in-flight charge (`cancel_requested`, etc.) | |

---

## Happy path (as implemented)

1. Step-by-step flow using **real** class names, topics, and endpoints from the codebase.
2. …

---

## Failure / edge paths (if in scope for this phase)

- If not in scope, say so explicitly and point to the phase that will add them.
- …

---

## How to verify (copy from phase doc + what you actually ran)

Record **pass/fail from real runs**—do not mark Docker or manual steps pass unless you or the user ran them.

```bash
# commands that passed
```

| Check | Result |
|-------|--------|
| `./mvnw -q verify` | |
| `docker compose up --build` | |
| Manual checks from `docs/03-phases.md` | |

---

## Key files (entry points)

| Path | Role |
|------|------|
| | |

---

## Not built yet (explicit gaps)

- Bullet list of requirements **not** covered until later phases (stops readers from assuming they exist).

---

## Links

- Changelog entry: `docs/CHANGELOG.md`
- Architecture spec: `docs/02-architecture.md`
- Previous phase map: `phase-0(N-1)-….md` (if any)
