const crypto = require('crypto');
const { log, pool, redis, publish, subscribe } = require('./common');

// State machine
const TRANSITIONS = {
  PLACED: ['RESERVED', 'CANCELLED'],
  RESERVED: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED'],
  SHIPPED: ['DELIVERED'],
};

async function transition(orderId, to, evt, data = {}) {
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const { rows } = await c.query('SELECT status FROM orders WHERE id=$1 FOR UPDATE', [orderId]);
    if (!rows.length) throw new Error('order not found');
    const from = rows[0].status;
    if (from === to) { await c.query('ROLLBACK'); return { ok: true, status: from }; } // redelivered event
    if (!(TRANSITIONS[from] || []).includes(to)) {
      await c.query('ROLLBACK');
      log.warn({ orderId, from, to }, 'illegal transition ignored');
      return { ok: false, status: from };
    }
    await c.query('UPDATE orders SET status=$2, updated_at=now() WHERE id=$1', [orderId, to]);
    await c.query('INSERT INTO order_events(order_id,type,data) VALUES ($1,$2,$3)', [orderId, evt, { from, to, ...data }]);
    await c.query('COMMIT');
    log.info({ orderId, from, to }, 'order transition');
    return { ok: true, status: to };
  } catch (e) { await c.query('ROLLBACK'); throw e; } finally { c.release(); }
}

async function createOrder(key, items, paymentToken) {
  const cached = await redis.get(`idem:${key}`);               // fast path
  if (cached) return { order: await getOrder(cached), replay: true };

  const skus = items.map((i) => i.sku);
  const { rows: prices } = await pool.query('SELECT sku, price_cents FROM inventory WHERE sku = ANY($1)', [skus]);
  const price = Object.fromEntries(prices.map((p) => [p.sku, p.price_cents]));
  if (skus.some((s) => price[s] == null)) throw Object.assign(new Error('unknown sku'), { status: 400 });
  const total = items.reduce((s, i) => s + price[i.sku] * i.qty, 0);

  const id = crypto.randomUUID();
  const ins = await pool.query(
    `INSERT INTO orders(id,idempotency_key,status,total_cents,items,payment_token)
     VALUES ($1,$2,'PLACED',$3,$4,$5) ON CONFLICT (idempotency_key) DO NOTHING RETURNING id`,
    [id, key, total, JSON.stringify(items), paymentToken || null]); // DB unique = the backstop
  if (!ins.rowCount) {
    const { rows } = await pool.query('SELECT id FROM orders WHERE idempotency_key=$1', [key]);
    await redis.set(`idem:${key}`, rows[0].id, 'EX', 86400);
    return { order: await getOrder(rows[0].id), replay: true };
  }
  await redis.set(`idem:${key}`, id, 'EX', 86400);
  await pool.query('INSERT INTO order_events(order_id,type,data) VALUES ($1,$2,$3)', [id, 'order.placed', { items, total }]);
  await publish('order.placed', { orderId: id, items, amountCents: total, paymentToken });
  return { order: await getOrder(id), replay: false };
}

async function getOrder(id) {
  const { rows } = await pool.query('SELECT * FROM orders WHERE id=$1', [id]);
  if (!rows.length) return null;
  const ev = await pool.query('SELECT type,data,at FROM order_events WHERE order_id=$1 ORDER BY id', [id]);
  return { ...rows[0], history: ev.rows };
}

function startOrderWorker() {
  subscribe('order-svc', {
    'inventory.reserved': (d) => transition(d.orderId, 'RESERVED', 'inventory.reserved'),
    'inventory.failed': (d) => transition(d.orderId, 'CANCELLED', 'inventory.failed', { reason: d.reason }),
    'payment.succeeded': (d) => transition(d.orderId, 'CONFIRMED', 'payment.succeeded'),
    'payment.failed': (d) => transition(d.orderId, 'CANCELLED', 'payment.failed', { reason: 'payment declined' }),
  });
}

module.exports = { createOrder, getOrder, transition, startOrderWorker };
