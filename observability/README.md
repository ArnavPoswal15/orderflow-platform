# Observability (local ELK)

Local-only demo. Elasticsearch security is disabled. The `elk` profile needs about 4GB of RAM on top of the rest of the stack. Do not expose this profile beyond your machine.

## Start

```bash
docker compose --profile elk up --build
```

Kibana: [http://localhost:5601](http://localhost:5601)

Open **Discover** and select the data view **shop-logs\***. Filebeat writes one index per day, `shop-logs-YYYY.MM.dd`. The data view matches `shop-logs*`.

The three Java services always write JSON to the console. With `SPRING_PROFILES_ACTIVE=elk-file` (set in Compose) they also roll a file under `/var/log/shop/`: `order-service.json`, `inventory-service.json`, and `payment-service.json`. Filebeat tails those files. It does not read `/var/lib/docker/containers`.

## One order, end to end

Place an order, then in Discover run:

```text
orderId: "<uuid>"
```

Sort `@timestamp` ascending. Expect publish and consume lines from `order-service`, `inventory-service`, and `payment-service`, from `order.created` through `payment.succeeded`, or `inventory.rejected` / `payment.failed` when the saga stops early.

The create-order response header `X-Trace-Id` is the saga trace. The same hop chain is:

```text
traceId: "<X-Trace-Id>"
```

A later HTTP cancel or ship uses a new request trace. `orderId` still joins the whole journey.

## Failures and latency

Failures:

```text
failureReason: *
```

```text
eventType: "payment.failed" or eventType: "inventory.rejected"
```

Latency is milliseconds from `order.created` to `payment.succeeded`:

```text
latencyMs: *
```

## Dashboard

`kibana-setup` imports `observability/order-platform.ndjson` once Kibana is healthy:

`POST http://kibana:5601/api/saved_objects/_import?overwrite=true` with header `kbn-xsrf: true`.

Open [http://localhost:5601/app/dashboards#/view/order-platform](http://localhost:5601/app/dashboards#/view/order-platform). The dashboard embeds the saved search and three classic visualizations: orders by `orderStatus` over time, `failureReason` terms, and average `latencyMs`. The time range restores to the last 24 hours.

Saved object ids:

| type | id |
|---|---|
| index-pattern | `shop-logs` |
| search | `shop-logs-search` |
| visualization | `orders-by-status` |
| visualization | `failure-reasons` |
| visualization | `average-latency` |
| dashboard | `order-platform` |

Re-import from the repo root:

```bash
curl -fsS -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@observability/order-platform.ndjson
```

A successful import returns `"success": true`.
