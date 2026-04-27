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
