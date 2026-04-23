-- cartserver cart dedup cleanup
-- 목적: 사용자별 cart 중복 데이터를 정리하고 운영 검증 쿼리를 제공합니다.
-- 순서:
-- 1) user_id 기준 중복 cart 대상을 식별합니다.
-- 2) 사용자별로 남길 keeper cart_id 1개를 정합니다.
-- 3) duplicate cart의 cart_item을 keeper cart로 병합합니다.
-- 4) duplicate cart와 duplicate cart_item을 제거합니다.
-- 5) uq_cart_user 인덱스 존재 여부를 다시 확인합니다.
--
-- 주의:
-- - 운영 반영 중에는 cart/cart_item 쓰기를 멈추거나 최소화한 뒤 실행하는 것을 권장합니다.
-- - 본 스크립트는 duplicate cart의 item을 keeper cart로 "이동"합니다.
-- - item 병합 시 quantity는 합산하고, is_selected는 OR, created_at은 MIN, updated_at은 MAX로 정리합니다.
-- - uq_cart_user 인덱스가 없어도 정리 자체는 수행되도록, keeper/duplicate cart의 item을 전부 재집계해서 다시 적재합니다.

BEGIN;

LOCK TABLE cart IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE cart_item IN SHARE ROW EXCLUSIVE MODE;

DROP TABLE IF EXISTS tmp_cart_dedup_target;
DROP TABLE IF EXISTS tmp_cart_dedup_scope;
DROP TABLE IF EXISTS tmp_cart_dedup_item;

CREATE TEMP TABLE tmp_cart_dedup_target AS
WITH ranked AS (
    SELECT
        c.cart_id,
        c.user_id,
        c.created_at,
        ROW_NUMBER() OVER (
            PARTITION BY c.user_id
            ORDER BY c.created_at ASC, c.cart_id ASC
        ) AS rn
    FROM cart c
),
keepers AS (
    SELECT
        r.user_id,
        r.cart_id AS keeper_cart_id
    FROM ranked r
    WHERE r.rn = 1
)
SELECT
    r.user_id,
    k.keeper_cart_id,
    r.cart_id AS duplicate_cart_id
FROM ranked r
JOIN keepers k
  ON k.user_id = r.user_id
WHERE r.rn > 1;

-- 1) 정리 대상 확인
SELECT
    user_id,
    keeper_cart_id,
    duplicate_cart_id
FROM tmp_cart_dedup_target
ORDER BY user_id, duplicate_cart_id;

-- keeper cart와 duplicate cart를 함께 재집계할 범위
CREATE TEMP TABLE tmp_cart_dedup_scope AS
SELECT
    c.user_id,
    t.keeper_cart_id,
    c.cart_id
FROM cart c
JOIN (
    SELECT DISTINCT user_id, keeper_cart_id
    FROM tmp_cart_dedup_target
) t
  ON t.user_id = c.user_id;

-- 2) keeper/duplicate cart 전체 item 재집계
CREATE TEMP TABLE tmp_cart_dedup_item AS
SELECT
    s.keeper_cart_id AS cart_id,
    ci.item_id,
    ci.option_id,
    SUM(ci.quantity) AS quantity,
    BOOL_OR(ci.is_selected) AS is_selected,
    MIN(ci.created_at) AS created_at,
    MAX(ci.updated_at) AS updated_at
    FROM tmp_cart_dedup_scope s
JOIN cart_item ci
  ON ci.cart_id = s.cart_id
GROUP BY s.keeper_cart_id, ci.item_id, ci.option_id;

-- 3) keeper/duplicate cart item 전체 삭제 후 재적재
DELETE FROM cart_item
WHERE cart_id IN (
    SELECT cart_id
    FROM tmp_cart_dedup_scope
);

INSERT INTO cart_item (cart_id, item_id, option_id, quantity, is_selected, created_at, updated_at)
SELECT
    cart_id,
    item_id,
    option_id,
    quantity,
    is_selected,
    created_at,
    updated_at
FROM tmp_cart_dedup_item;

-- keeper cart의 updated_at 보정
UPDATE cart
   SET updated_at = NOW()
 WHERE cart_id IN (
    SELECT DISTINCT keeper_cart_id
    FROM tmp_cart_dedup_target
 );

DELETE FROM cart
 WHERE cart_id IN (
    SELECT duplicate_cart_id
    FROM tmp_cart_dedup_target
 );

DROP INDEX IF EXISTS uq_cart_active_user;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user
    ON cart (user_id);

COMMIT;

-- 5) 정리 결과 검증
-- 기대값: 결과 0행
SELECT
    user_id,
    COUNT(*) AS cart_count
FROM cart
GROUP BY user_id
HAVING COUNT(*) > 1;

-- 기대값: uq_cart_user 1행
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'cart'
  AND indexname = 'uq_cart_user';
