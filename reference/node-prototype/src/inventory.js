const { log, pool, redis, publish, subscribe } = require('./common');

class OutOfStock extends Error {}

async function reserve({ orderId, items, amountCents, paymentToken }) {
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const dup = await c.query('SELECT status FROM reservations WHERE order_id=$1', [orderId]);
    if (dup.rowCount) { await c.query('ROLLBACK'); return; } // already handled (redelivery)

    // Sorted to avoid deadlocks between concurrent multi-item orders.
    for (const { sku, qty } of [...items].sort((a, b) => a.sku.localeCompare(b.sku))) {
      // Atomic conditional decrement: the row lock serialises racing orders,
      // so the loser re-checks `available >= qty` against the committed value.
      const r = await c.query(
        'UPDATE inventory SET available = available - $2, version = version + 1 WHERE sku = $1 AND available >= $2',
        [sku, qty]);
      if (!r.rowCount) throw new OutOfStock(sku);
    }
    await c.query('INSERT INTO reservations(order_id,status,items) VALUES ($1,$2,$3)', [orderId, 'RESERVED', JSON.stringify(items)]);
    await c.query('COMMIT');
    await invalidate(items);
    await publish('inventory.reserved', { orderId, amountCents, paymentToken });
  } catch (e) {
    await c.query('ROLLBACK');
    if (e instanceof OutOfStock) {
      log.warn({ orderId, sku: e.message }, 'out of stock');
      return publish('inventory.failed', { orderId, reason: `out of stock: ${e.message}` });
    }
    throw e;
  } finally { c.release(); }
}

// Saga compensation: payment failed -> give the stock back (idempotent).
async function release({ orderId }) {
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const { rows } = await c.query(
      "UPDATE reservations SET status='RELEASED' WHERE order_id=$1 AND status='RESERVED' RETURNING items", [orderId]);
    if (!rows.length) { await c.query('ROLLBACK'); return; }
    for (const { sku, qty } of rows[0].items) {
      await c.query('UPDATE inventory SET available = available + $2, version = version + 1 WHERE sku = $1', [sku, qty]);
    }
    await c.query('COMMIT');
    await invalidate(rows[0].items);
    log.info({ orderId }, 'stock released');
    await publish('inventory.released', { orderId });
  } catch (e) { await c.query('ROLLBACK'); throw e; } finally { c.release(); }
}

const invalidate = (items) => redis.del(...items.map((i) => `stock:${i.sku}`));

async function getStock(sku) {
  const hit = await redis.get(`stock:${sku}`);
  if (hit) return { ...JSON.parse(hit), cached: true };
  const { rows } = await pool.query('SELECT sku, available, price_cents FROM inventory WHERE sku=$1', [sku]);
  if (!rows.length) return null;
  await redis.set(`stock:${sku}`, JSON.stringify(rows[0]), 'EX', 5);
  return { ...rows[0], cached: false };
}

function startInventoryWorker() {
  subscribe('inventory-svc', { 'order.placed': reserve, 'payment.failed': release });
}

module.exports = { getStock, startInventoryWorker };
