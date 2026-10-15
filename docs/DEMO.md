# 3-minute demo script

Rough timing for a live walkthrough. Stack: `docker compose up --build` (shop at http://localhost:3000).

| Time | Beat |
|------|------|
| **0:00–0:30** | Open the storefront `/`. Point out the product grid and live stock; highlight **LASTITEM** (low stock badge). |
| **0:30–1:15** | Add an item, checkout with payment token **`ok`**. Land on `/orders/{id}` and watch the SSE timeline move **PENDING → RESERVED → CONFIRMED** without refresh. |
| **1:15–1:45** | New checkout with token **`fail`**. Show order **CANCELLED** and stock restored (refresh `/` or `curl -s localhost:8080/api/products \| jq`). |
| **1:45–2:30** | Open **/ops**: live event feed, status counters, stock table. Run flash sale on **LASTITEM** with **N=50**; result panel should show **1 CONFIRMED** and **49 CANCELLED**. |
| **2:30–3:00** | Optional: mention proof load test (`k6 run loadtest/orders.js`) and paste results into `docs/benchmarks.md`. Optional ELK: `docker compose --profile elk up` and Kibana trace by `orderId` (see `observability/README.md`). |

Screenshots: capture paths listed in [docs/assets/README.md](./assets/README.md) for the README.
