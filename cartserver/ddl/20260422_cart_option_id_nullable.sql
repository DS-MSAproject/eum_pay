-- cartserver option_id nullable migration
-- 목적: 옵션이 없는 상품을 기본 option_id 없이도 장바구니에 담을 수 있도록 허용합니다.
-- 주의: 20260421_cart_option_id_not_null.sql의 대체 전략입니다. nullable 전략을 선택했다면 20260421을 재실행하지 않습니다.

ALTER TABLE cart_item
    ALTER COLUMN option_id DROP NOT NULL;

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
