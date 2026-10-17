# Demo assets

PNG screenshots used in the root [README](../../README.md) **Screenshots** section. Replace a file in place to refresh the README visuals.

| File | What it shows | How to recapture |
|------|----------------|------------------|
| `storefront.png` | Storefront `/` — product grid with **LASTITEM** low-stock indicator. | `docker compose up --build` → http://localhost:3000/ |
| `order-timeline.png` | Order detail `/orders/{id}` — saga timeline (mid-flow or **CONFIRMED**). | Checkout with payment token `ok`; keep the order page open while SSE updates. |
| `ops-flash-sale.png` | `/ops` — flash sale result (**1 CONFIRMED**, **N−1 CANCELLED**). | Open `/ops`, set failure rate **0%**, run flash sale on **LASTITEM** with **N=50**. |
| `kibana-trace.png` | Kibana **Discover** — `shop-logs*` data view, query `orderId: "<uuid>"`, **@timestamp** ascending. | `docker compose --profile elk up --build` → place an order → http://localhost:5601 (see [observability/README.md](../../observability/README.md)). |
| `kibana-dashboard.png` | Kibana **Dashboard** **order-platform** — orders by status, failure reasons, average latency. | Same ELK stack; generate demo traffic, then open [order-platform dashboard](http://localhost:5601/app/dashboards#/view/order-platform) (imported by `kibana-setup` from `observability/order-platform.ndjson`). Adjust time range (e.g. last 24h) if panels are empty. |

**ELK:** The `elk` profile needs roughly **4GB extra RAM** on top of the default stack. Dashboard and Discover docs: [observability/README.md](../../observability/README.md).

**Demo script:** Step-by-step timing for the shop and ops shots: [docs/DEMO.md](../DEMO.md).
