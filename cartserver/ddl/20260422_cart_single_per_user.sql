-- cartserver single cart per user migration
-- 목적: status 컬럼 기반 모델을 제거하고 사용자당 cart 1개 모델로 전환합니다.
-- 전제: 중복 cart가 있는 환경이라면 20260422_deduplicate_cart_per_user.sql을 먼저 실행합니다.

DROP INDEX IF EXISTS uq_cart_active_user;
DROP INDEX IF EXISTS idx_cart_user_status;

ALTER TABLE cart
    DROP COLUMN IF EXISTS status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user
    ON cart (user_id);
