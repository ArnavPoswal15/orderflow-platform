const { log, pool, publish, subscribe } = require('./common');

// Simulated gateway: token "fail" always declines; ~10% random declines otherwise.
const gateway = async (token) => token !== 'fail' && Math.random() > 0.1;

async function charge({ orderId, amountCents, paymentToken }) {
  const prev = await pool.query('SELECT status FROM payments WHERE order_id=$1', [orderId]);
  let status = prev.rows[0]?.status;
  if (!status) {                                   // never charge twice for one order
    status = (await gateway(paymentToken)) ? 'SUCCEEDED' : 'FAILED';
    await pool.query('INSERT INTO payments(order_id,status,amount_cents) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING',
      [orderId, status, amountCents]);
    log.info({ orderId, status }, 'payment processed');
  }
  await publish(status === 'SUCCEEDED' ? 'payment.succeeded' : 'payment.failed', { orderId });
}

async function refund(orderId) {
  const r = await pool.query("UPDATE payments SET status='REFUNDED' WHERE order_id=$1 AND status='SUCCEEDED' RETURNING 1", [orderId]);
  return r.rowCount > 0;
}

function startPaymentWorker() { subscribe('payment-svc', { 'inventory.reserved': charge }); }

module.exports = { startPaymentWorker, refund };
