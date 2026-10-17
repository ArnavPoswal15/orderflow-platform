# Demo assets (placeholders)

Add images here for the README. Until then, links in the root README will 404 intentionally.

| File | Capture |
|------|---------|
| `storefront.png` | Storefront `/` with product grid and LASTITEM low-stock indicator. |
| `order-timeline.png` | Order detail page mid-saga or at CONFIRMED with timeline visible. |
| `ops-flash-sale.png` | `/ops` after flash sale showing 1 CONFIRMED / N−1 CANCELLED. |
| `kibana-trace.png` | Kibana **Discover** (`shop-logs*` data view) filtered on one `orderId`, `@timestamp` ascending (ELK profile). |
| `kibana-dashboard.png` | Kibana **Dashboard** [order-platform](http://localhost:5601/app/dashboards#/view/order-platform): orders by status, failure reasons, average latency (last 24h or after demo traffic). Start stack with `docker compose --profile elk up --build`; dashboard is imported by `kibana-setup` from `observability/order-platform.ndjson`. |
