-- cartserver option_id required migration
-- 주의: option_id nullable 전략(20260422_cart_option_id_nullable.sql)을 채택한 환경에서는 이 스크립트를 실행하지 않습니다.
-- 전제: productserver가 옵션 없는 상품에도 기본 option_id를 발급해야 합니다.
-- 선택 사항: 운영 반영 전에 product_id -> default_option_id 매핑용
-- staging_product_default_option(product_id bigint primary key, default_option_id bigint not null)
-- 테이블을 채워두면 아래 스크립트가 NULL option_id를 자동 백필합니다.

-- 1) 선택적 백필
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = current_schema()
           AND table_name = 'staging_product_default_option'
    ) THEN
        UPDATE cart_item ci
           SET option_id = m.default_option_id
          FROM staging_product_default_option m
         WHERE ci.item_id = m.product_id
           AND ci.option_id IS NULL;
    END IF;
END
$$;

-- 2) NULL 잔존 여부 검증
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM cart_item
         WHERE option_id IS NULL
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'cart_item.option_id 백필이 완료되지 않아 NOT NULL 제약을 적용할 수 없습니다.',
            DETAIL = 'SELECT COUNT(*) FROM cart_item WHERE option_id IS NULL; 로 잔여 데이터를 확인하세요.',
            HINT = 'staging_product_default_option 매핑을 채운 뒤 UPDATE 후 재실행하세요.';
    END IF;
END
$$;

-- 3) 백필 이후 중복 business key 검증
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM cart_item
         GROUP BY cart_id, item_id, option_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'cart_item(cart_id, item_id, option_id) 중복 데이터가 존재해 유니크 인덱스를 생성할 수 없습니다.',
            DETAIL = 'SELECT cart_id, item_id, option_id, COUNT(*) FROM cart_item GROUP BY cart_id, item_id, option_id HAVING COUNT(*) > 1; 로 중복을 정리하세요.',
            HINT = '중복 행을 병합 또는 삭제한 뒤 마이그레이션을 재실행하세요.';
    END IF;
END
$$;

-- 4) 검증 통과 후 제약/인덱스 적용
ALTER TABLE cart_item
    ALTER COLUMN option_id SET NOT NULL;

DROP INDEX IF EXISTS uq_cart_item_base_product;
DROP INDEX IF EXISTS uq_cart_item_product_option;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_item_product_option
    ON cart_item (cart_id, item_id, option_id);

DROP INDEX IF EXISTS uq_cart_active_user;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user
    ON cart (user_id);
