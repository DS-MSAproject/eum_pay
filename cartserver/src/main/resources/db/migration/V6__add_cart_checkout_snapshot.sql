CREATE TABLE IF NOT EXISTS cart_checkout_snapshot (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT    NOT NULL UNIQUE,
    user_id    BIGINT    NOT NULL,
    items      TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cart_checkout_snapshot_order_id
    ON cart_checkout_snapshot (order_id);
