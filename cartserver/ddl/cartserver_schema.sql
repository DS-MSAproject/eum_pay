-- cartserver schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS cart (
    cart_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE cart DROP COLUMN IF EXISTS status;

DROP INDEX IF EXISTS idx_cart_user_status;
DROP INDEX IF EXISTS uq_cart_active_user;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user
    ON cart (user_id);

CREATE TABLE IF NOT EXISTS cart_item (
    cart_item_id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES cart(cart_id),
    item_id BIGINT NOT NULL,
    option_id BIGINT,
    quantity BIGINT NOT NULL,
    is_selected BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE cart_item ADD COLUMN IF NOT EXISTS option_id BIGINT;
ALTER TABLE cart_item ADD COLUMN IF NOT EXISTS is_selected BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE cart_item DROP COLUMN IF EXISTS unit_price;
ALTER TABLE cart_item DROP COLUMN IF EXISTS item_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS option_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS snapshot_json;

CREATE INDEX IF NOT EXISTS idx_cart_item_cart
    ON cart_item (cart_id);

DROP INDEX IF EXISTS uq_cart_item;
DROP INDEX IF EXISTS uq_cart_item_base_product;
DROP INDEX IF EXISTS uq_cart_item_product_option;
DROP INDEX IF EXISTS uq_cart_item_product_without_option;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_item_product_option
    ON cart_item (cart_id, item_id, option_id)
    WHERE option_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_item_product_without_option
    ON cart_item (cart_id, item_id)
    WHERE option_id IS NULL;

ALTER TABLE cart_item
    DROP CONSTRAINT IF EXISTS chk_cart_item_quantity_positive;

ALTER TABLE cart_item
    ADD CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0);
