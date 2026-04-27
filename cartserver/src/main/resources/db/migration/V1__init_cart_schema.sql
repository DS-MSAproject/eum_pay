CREATE TABLE IF NOT EXISTS cart (
    cart_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

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
ALTER TABLE cart_item ADD COLUMN IF NOT EXISTS is_selected BOOLEAN;

UPDATE cart_item
   SET is_selected = TRUE
 WHERE is_selected IS NULL;

ALTER TABLE cart_item
    ALTER COLUMN is_selected SET DEFAULT TRUE;

ALTER TABLE cart_item
    ALTER COLUMN is_selected SET NOT NULL;

ALTER TABLE cart_item DROP COLUMN IF EXISTS unit_price;
ALTER TABLE cart_item DROP COLUMN IF EXISTS item_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS option_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS snapshot_json;

CREATE INDEX IF NOT EXISTS idx_cart_item_cart
    ON cart_item (cart_id);

ALTER TABLE cart_item
    DROP CONSTRAINT IF EXISTS chk_cart_item_quantity_positive;

ALTER TABLE cart_item
    ADD CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0);
