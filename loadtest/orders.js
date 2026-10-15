import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const INVENTORY_URL = __ENV.INVENTORY_URL || 'http://localhost:8081';
const SKU = __ENV.SKU || 'LASTITEM';
const INITIAL_STOCK = parseInt(__ENV.INITIAL_STOCK || '1', 10);
const VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
  scenarios: {
    race: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

function jsonHeaders(extra = {}) {
  return { 'Content-Type': 'application/json', ...extra };
}

function confirmedCount(statsBody) {
  const byStatus = statsBody.ordersByStatus || {};
  return byStatus.CONFIRMED || 0;
}

function skuAvailable(skusBody) {
  const list = skusBody.skus || [];
  const row = list.find((s) => s.sku === SKU);
  return row ? row.available : null;
}

export function setup() {
  const fr = http.put(
    `${BASE_URL}/api/admin/failure-rate`,
    JSON.stringify({ percent: 0 }),
    { headers: jsonHeaders() },
  );
  check(fr, { 'failure-rate reset': (r) => r.status === 200 });

  const stock = http.put(
    `${INVENTORY_URL}/internal/skus/${SKU}/stock`,
    JSON.stringify({ available: INITIAL_STOCK }),
    { headers: jsonHeaders() },
  );
  check(stock, { 'stock reset': (r) => r.status === 200 });

  const statsRes = http.get(`${BASE_URL}/api/admin/stats`);
  check(statsRes, { 'baseline stats': (r) => r.status === 200 });
  const baselineConfirmed = confirmedCount(statsRes.json());

  return {
    baselineConfirmed,
    initialStock: INITIAL_STOCK,
    sku: SKU,
    vus: VUS,
  };
}

export default function () {
  const idem = `k6-${__VU}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const body = JSON.stringify({
    items: [{ sku: SKU, qty: 1 }],
    paymentToken: 'ok',
  });
  const res = http.post(`${BASE_URL}/api/orders`, body, {
    headers: jsonHeaders({
      'Idempotency-Key': idem,
      'X-Forwarded-For': `10.0.${__VU}.0`,
    }),
  });
  check(res, {
    'order accepted (202)': (r) => r.status === 202,
  });
}

export function teardown(data) {
  const deadline = Date.now() + 120_000;
  let available = null;
  let confirmedNow = null;
  let settled = false;
  let lastSnap = null;
  let stablePolls = 0;

  while (Date.now() < deadline) {
    const skusRes = http.get(`${INVENTORY_URL}/internal/skus?sku=${data.sku}`);
    const statsRes = http.get(`${BASE_URL}/api/admin/stats`);
    if (skusRes.status === 200 && statsRes.status === 200) {
      available = skuAvailable(skusRes.json());
      confirmedNow = confirmedCount(statsRes.json());
      const confirmedDelta = confirmedNow - data.baselineConfirmed;
      const invariantsOk =
        available !== null && available >= 0 && confirmedDelta >= 0 && confirmedDelta <= data.initialStock;
      const processingStarted = available < data.initialStock || confirmedDelta > 0;
      const stockAccountingOk = available + confirmedDelta === data.initialStock;
      const snap = `${available}:${confirmedDelta}`;
      if (invariantsOk && processingStarted && stockAccountingOk) {
        if (snap === lastSnap) {
          stablePolls += 1;
        } else {
          stablePolls = 0;
        }
        lastSnap = snap;
        if (stablePolls >= 2) {
          settled = true;
          break;
        }
      } else {
        stablePolls = 0;
        lastSnap = null;
      }
    }
    sleep(2);
  }

  const confirmedDelta =
    confirmedNow !== null ? confirmedNow - data.baselineConfirmed : -1;
  const availableOk = available !== null && available >= 0;
  const deltaOk = confirmedDelta >= 0 && confirmedDelta <= data.initialStock;
  const pass = settled && availableOk && deltaOk;

  const line =
    `OVERSOLD_CHECK pass=${pass} available=${available} confirmedDelta=${confirmedDelta} ` +
    `baselineConfirmed=${data.baselineConfirmed} confirmedNow=${confirmedNow} initialStock=${data.initialStock}`;
  console.log(line);

  if (!pass) {
    fail(line);
  }
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
