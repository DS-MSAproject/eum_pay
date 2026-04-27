LOCK TABLE cart_item IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE cart_item
    ADD COLUMN IF NOT EXISTS option_id BIGINT;

ALTER TABLE cart_item
    ADD COLUMN IF NOT EXISTS is_selected BOOLEAN;

UPDATE cart_item
   SET option_id = 0
 WHERE option_id IS NULL;

UPDATE cart_item
   SET is_selected = TRUE
 WHERE is_selected IS NULL;

DROP TABLE IF EXISTS tmp_cart_item_rebuild;

CREATE TEMP TABLE tmp_cart_item_rebuild AS
SELECT
    cart_id,
    item_id,
    COALESCE(option_id, 0) AS option_id,
    SUM(quantity) AS quantity,
    BOOL_OR(is_selected) AS is_selected,
    MIN(created_at) AS created_at,
    MAX(updated_at) AS updated_at
FROM cart_item
GROUP BY cart_id, item_id, COALESCE(option_id, 0);

DELETE FROM cart_item;

INSERT INTO cart_item (cart_id, item_id, option_id, quantity, is_selected, created_at, updated_at)
SELECT
    cart_id,
    item_id,
    option_id,
    quantity,
    is_selected,
    created_at,
    updated_at
FROM tmp_cart_item_rebuild;

ALTER TABLE cart_item
    ALTER COLUMN option_id SET DEFAULT 0;

ALTER TABLE cart_item
    ALTER COLUMN option_id SET NOT NULL;

ALTER TABLE cart_item
    ALTER COLUMN is_selected SET DEFAULT TRUE;

ALTER TABLE cart_item
    ALTER COLUMN is_selected SET NOT NULL;

ALTER TABLE cart_item
    DROP CONSTRAINT IF EXISTS chk_cart_item_quantity_positive;

ALTER TABLE cart_item
    ADD CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0);

DROP INDEX IF EXISTS uq_cart_item;
DROP INDEX IF EXISTS uq_cart_item_base_product;
DROP INDEX IF EXISTS uq_cart_item_product_option;
DROP INDEX IF EXISTS uq_cart_item_product_without_option;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_item_product_option
    ON cart_item (cart_id, item_id, option_id);

CREATE INDEX IF NOT EXISTS idx_cart_item_cart
    ON cart_item (cart_id);

ALTER TABLE cart_item DROP COLUMN IF EXISTS unit_price;
ALTER TABLE cart_item DROP COLUMN IF EXISTS item_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS option_name;
ALTER TABLE cart_item DROP COLUMN IF EXISTS snapshot_json;
