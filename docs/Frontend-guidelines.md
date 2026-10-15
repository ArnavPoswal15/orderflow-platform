# Frontend guidelines (`web/`)

Authoritative rules for the Next.js storefront. Agents and humans should follow this file for any change under `web/`. Stack and API contracts live in `docs/02-architecture.md` §8 and §6.

---

## Stack (do not substitute)

| Piece | Version / choice | Notes |
|-------|------------------|--------|
| Next.js | **16.x** (App Router) | Breaking changes vs older Next — read `web/node_modules/next/dist/docs/` before using unfamiliar APIs |
| React | **19.x** | Server Components by default |
| TypeScript | 5.x | Strict; shared view types in `web/lib/types.ts` |
| Styling | **Tailwind CSS 4** | `@tailwindcss/turbopack` in dev; tokens in `app/globals.css` |
| Backend | order-service REST + SSE | No direct calls to inventory/payment from the browser |

Confirm framework APIs against current docs when unsure (`web/AGENTS.md`).

---

## App structure

```
web/
├── app/              # Routes (App Router)
│   ├── page.tsx      # Product grid
│   ├── cart/, checkout/, orders/, orders/[id]/
│   └── api/stream/   # SSE proxy (not rewritten — see below)
├── components/       # UI building blocks
├── lib/
│   ├── api.ts        # HTTP client + problem+json
│   ├── types.ts      # DTOs matching order-service JSON
│   ├── cart.tsx      # Client cart context
│   └── format.ts     # Money, dates
└── next.config.ts    # Rewrites + SSE-related settings
```

**Naming:** PascalCase components, kebab or descriptive route folders, `@/` imports from project root.

---

## Data fetching

### Server vs client

| Use case | Where | How |
|----------|--------|-----|
| Initial product list, order detail (first paint) | Server Component | `getProducts()`, `getOrder()` → `ORDER_SERVICE_URL` |
| Checkout POST, cancel, client refresh | Client Component | `fetchProducts()`, `postOrder()`, `cancelOrder()` → **relative** `/api/*` |
| Live order status | Client | `EventSource` on `/api/stream/orders/{id}` |

**Rule:** Browser code never calls `localhost:8080` directly. Use `/api/...` so rewrites and Docker networking stay correct.

**Rule:** Server-only helpers use `getOrderServiceUrl()` (`ORDER_SERVICE_URL`, default `http://localhost:8080`). Set in compose for the `web` service.

### Caching

- Use `cache: "no-store"` on order/catalog fetches unless a route explicitly needs static caching.
- Do not cache SSE or mutation responses.

---

## API proxy (`next.config.ts`)

- **Rewrites** forward `/api/products` and `/api/orders` (+ subpaths) to order-service.
- **Do not** add a rewrite for `/api/stream/*`. Streaming is handled by `app/api/stream/[...path]/route.ts` so Next does not buffer the body.
- Keep `compress: false` and a long `proxyTimeout` while SSE is in use (see comments in `next.config.ts`).

---

## Errors and edge cases

- Backend errors are **RFC 7807** `application/problem+json`. Parse with `parseProblem()`; surface via `ApiProblem` and `ProblemAlert`.
- **429:** show `detail`, respect `Retry-After` when present (`ProblemDetail.retryAfterSec`).
- **409:** illegal transition (e.g. cancel not allowed) — show message, do not retry blindly.
- **422:** idempotency key reused with different body — tell user to use a new key or fix the cart.

Checkout must send **`Idempotency-Key`** on every `POST /api/orders` (generate a new UUID per checkout attempt; reuse only when retrying the *same* attempt).

---

## Money and display

- Prices and totals are **integer cents** (`priceCents`, `totalCents`). Format in UI with `Money` / `format.ts` — never `float` math for money.
- Stock: show `available` from API; use `StockBadge` patterns for low/zero stock.

---

## Real-time (SSE)

- Order detail: subscribe in a client component (`OrderLivePanel` / `OrderTimeline`); update status when events arrive.
- Parse payloads as `EventEnvelope` when typing events; tolerate unknown `type` values.
- Close `EventSource` on unmount and when navigating away.
- Ops dashboard (`/ops`, Phase 5): global feed on `/api/stream/events` — same proxy rules as per-order stream.

---

## Cart and checkout

- Cart state is **client-only** (`CartProvider`); no server session.
- Checkout collects `paymentToken` (demo: `ok` vs magic `fail`).
- After successful POST, redirect to `/orders/[id]` for the live timeline.

---

## UI and accessibility

- Layout: `SiteHeader`, `PageHeader`, max width `max-w-5xl`, readable contrast on `bg-slate-50`.
- Prefer semantic HTML (`button`, `nav`, headings), focus states on interactive elements.
- Loading: `Suspense` at layout boundaries where appropriate; explicit loading copy on client actions.
- Keep components small; one concern per file under `components/`.

---

## Docker and local dev

```bash
cd web && npm run dev          # UI on :3000, API via rewrites to :8080
docker compose up --build      # web service uses ORDER_SERVICE_URL=http://order-service:8080
```

Production image: `output: "standalone"` in `next.config.ts`; build via `web/Dockerfile`.

---

## What not to do

- Do not add a second API client library unless there is a strong reason; extend `lib/api.ts`.
- Do not put secrets or service URLs in client bundles (only `ORDER_SERVICE_URL` on server).
- Do not bypass idempotency or omit `Accept: application/problem+json` on writes.
- Do not add `/api/stream` to `rewrites()` — it breaks live updates.

---

## Phase scope (storefront vs ops)

| Phase | Routes / features |
|-------|-------------------|
| 4 | `/`, `/cart`, `/checkout`, `/orders`, `/orders/[id]` — FR-23–25 |
| 5 | `/ops` — live feed, stats, failure-rate, flash sale — FR-26–29 |

Implement only what the current phase in `docs/03-phases.md` asks for; do not ship `/ops` during Phase 4-only work.

---

## Related docs

| File | Purpose |
|------|---------|
| `docs/02-architecture.md` | REST paths, SSE endpoints |
| `docs/01-requirements.md` | FR-20–29 (edge + UI) |
| `docs/03-phases.md` | Phase 4 / 5 prompts |
| `.cursor/rules/frontend.mdc` | Cursor rule for `web/**` |
