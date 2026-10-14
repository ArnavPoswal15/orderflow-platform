CREATE TABLE inventory (
  sku TEXT PRIMARY KEY,
  price_cents INT NOT NULL,
  available INT NOT NULL CHECK (available >= 0),
  version INT NOT NULL DEFAULT 0
);
CREATE TABLE orders (
  id UUID PRIMARY KEY,
  idempotency_key TEXT UNIQUE NOT NULL,
  status TEXT NOT NULL,
  total_cents INT NOT NULL,
  items JSONB NOT NULL,
  payment_token TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE order_events (            -- append-only history (event-sourcing lite)
  id BIGSERIAL PRIMARY KEY,
  order_id UUID NOT NULL,
  type TEXT NOT NULL,
  data JSONB,
  at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE reservations (
  order_id UUID PRIMARY KEY,
  status TEXT NOT NULL,                -- RESERVED | RELEASED
  items JSONB NOT NULL
);
CREATE TABLE payments (
  order_id UUID PRIMARY KEY,           -- one charge per order = idempotent
  status TEXT NOT NULL,                -- SUCCEEDED | FAILED
  amount_cents INT NOT NULL
);
INSERT INTO inventory VALUES ('MILK', 399, 50, 0), ('EGGS', 549, 20, 0), ('LASTITEM', 1999, 1, 0);
