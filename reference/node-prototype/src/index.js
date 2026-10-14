const express = require('express');
const { log, redis } = require('./common');
const orders = require('./orders');
const inventory = require('./inventory');
const payment = require('./payment');

const services = (process.env.SERVICES || 'api,order,inventory,payment').split(',');
if (services.includes('order')) orders.startOrderWorker();
if (services.includes('inventory')) inventory.startInventoryWorker();
if (services.includes('payment')) payment.startPaymentWorker();

if (services.includes('api')) {
  const app = express();
  app.use(express.json());

  // Rate limit: Redis sorted-set sliding window
  const WINDOW_MS = 60_000, LIMIT = Number(process.env.RATE_LIMIT || 20);
  app.use('/orders', async (req, res, next) => {
    const key = `rl:${req.ip}`, now = Date.now();
    const r = await redis.multi()
      .zremrangebyscore(key, 0, now - WINDOW_MS)
      .zadd(key, now, `${now}-${Math.random()}`)
      .zcard(key)
      .pexpire(key, WINDOW_MS).exec();
    if (r[2][1] > LIMIT) return res.set('Retry-After', '10').status(429).json({ error: 'rate limit exceeded' });
    next();
  });

  const wrap = (fn) => (req, res) => fn(req, res).catch((e) => {
    log.error({ err: e.message }, 'request failed');
    res.status(e.status || 500).json({ error: e.message });
  });

  app.post('/orders', wrap(async (req, res) => {
    const key = req.get('Idempotency-Key');
    const { items, paymentToken } = req.body || {};
    if (!key) return res.status(400).json({ error: 'Idempotency-Key header required' });
    if (!Array.isArray(items) || !items.length || items.some((i) => !i.sku || !(i.qty > 0)))
      return res.status(400).json({ error: 'items: [{sku, qty>0}] required' });
    const { order, replay } = await orders.createOrder(key, items, paymentToken);
    res.status(replay ? 200 : 202).json(order); // 202: processing continues asynchronously
  }));

  app.get('/orders/:id', wrap(async (req, res) => {
    const o = await orders.getOrder(req.params.id);
    o ? res.json(o) : res.status(404).json({ error: 'not found' });
  }));

  app.post('/orders/:id/ship', wrap(async (req, res) => {
    const r = await orders.transition(req.params.id, 'SHIPPED', 'order.shipped');
    res.status(r.ok ? 200 : 409).json(r);
  }));

  app.get('/inventory/:sku', wrap(async (req, res) => {
    const s = await inventory.getStock(req.params.sku);
    s ? res.json(s) : res.status(404).json({ error: 'not found' });
  }));

  app.listen(3000, () => log.info('API on :3000'));
}
