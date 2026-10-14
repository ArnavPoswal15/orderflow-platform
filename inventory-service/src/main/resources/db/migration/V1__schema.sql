CREATE TABLE inventory (
    sku TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    price_cents BIGINT NOT NULL,
    available INT NOT NULL CHECK (available >= 0),
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    order_id UUID PRIMARY KEY,
    status TEXT NOT NULL,
    items JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic TEXT NOT NULL,
    key TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO inventory (sku, name, price_cents, available, version) VALUES
    ('MILK', 'Whole Milk 1L', 399, 50, 0),
    ('EGGS', 'Free Range Eggs 12pk', 549, 20, 0),
    ('LASTITEM', 'Limited Edition Jar', 1999, 1, 0);
