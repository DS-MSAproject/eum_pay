DROP INDEX IF EXISTS uq_cart_active_user;
DROP INDEX IF EXISTS idx_cart_user_status;

ALTER TABLE cart
    DROP COLUMN IF EXISTS status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user
    ON cart (user_id);
