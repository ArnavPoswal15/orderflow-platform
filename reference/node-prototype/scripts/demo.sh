#!/usr/bin/env bash
# Usage: ./scripts/demo.sh   (stack must be up)
H='Content-Type: application/json'; U=http://localhost:3000
order() { curl -s -X POST $U/orders -H "$H" -H "Idempotency-Key: $1" -d "$2"; echo; }

echo "== 2. Race: two orders, one LASTITEM (fired in parallel)"
order race-a '{"items":[{"sku":"LASTITEM","qty":1}],"paymentToken":"ok"}' &
order race-b '{"items":[{"sku":"LASTITEM","qty":1}],"paymentToken":"ok"}' &
wait

echo "== 1. Idempotency: same key twice -> same order (200 on replay)"
order idem-1 '{"items":[{"sku":"MILK","qty":1}],"paymentToken":"ok"}'
order idem-1 '{"items":[{"sku":"MILK","qty":1}],"paymentToken":"ok"}'

echo "== 3. Payment fails -> stock auto-released (EGGS stays 20)"
order fail-1 '{"items":[{"sku":"EGGS","qty":3}],"paymentToken":"fail"}'
sleep 2; curl -s $U/inventory/EGGS; echo

echo "== 5. Rate limit: burst of 25 -> expect 429s"
for i in $(seq 1 25); do curl -s -o /dev/null -w "%{http_code} " -X POST $U/orders -H "$H" -H "Idempotency-Key: burst-$i" -d '{"items":[{"sku":"MILK","qty":1}],"paymentToken":"ok"}'; done; echo
