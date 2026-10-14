const { Pool } = require('pg');
const Redis = require('ioredis');
const pino = require('pino');

const log = pino({ level: process.env.LOG_LEVEL || 'info' }); // JSON on stdout -> ship to ELK via Filebeat
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const redis = new Redis(process.env.REDIS_URL);

const STREAM = 'events';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function publish(type, data) {
  await redis.xadd(STREAM, '*', 'type', type, 'data', JSON.stringify(data));
  log.info({ type, orderId: data.orderId }, 'event published');
}

// At-least-once consumer group. Handlers MUST be idempotent.
async function subscribe(group, handlers) {
  const r = new Redis(process.env.REDIS_URL);
  try { await r.xgroup('CREATE', STREAM, group, '0', 'MKSTREAM'); }
  catch (e) { if (!/BUSYGROUP/.test(e.message)) throw e; }
  let cursor = '0'; // first drain our own un-acked messages, then switch to new ones
  (async () => {
    for (;;) {
      try {
        const res = await r.xreadgroup('GROUP', group, `${group}-1`, 'COUNT', 10, 'BLOCK', 5000, 'STREAMS', STREAM, cursor);
        const entries = res ? res[0][1] : [];
        if (cursor === '0' && entries.length === 0) { cursor = '>'; continue; }
        for (const [id, f] of entries) {
          const type = f[1], data = JSON.parse(f[3]);
          if (handlers[type]) {
            try { await handlers[type](data); }
            catch (e) { log.error({ err: e.message, type, group }, 'handler failed, left pending'); continue; }
          }
          await r.xack(STREAM, group, id);
        }
      } catch (e) { log.error({ err: e.message, group }, 'consumer error'); await sleep(1000); }
    }
  })();
}

module.exports = { log, pool, redis, publish, subscribe };
