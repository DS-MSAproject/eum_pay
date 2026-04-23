-- orderserver schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(255),
    amount BIGINT NOT NULL,
    payment_method VARCHAR(255),
    paid_amount BIGINT,
    receiver_name VARCHAR(255),
    receiver_phone VARCHAR(255),
    receiver_addr VARCHAR(255),
    delete_yn VARCHAR(1) NOT NULL DEFAULT 'N',
    time TIMESTAMP,
    order_state VARCHAR(50),
    failed_reason VARCHAR(500),
    failed_at TIMESTAMP
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS user_name VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_amount BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failed_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_user_time
    ON orders (user_id, time);

UPDATE orders
   SET order_state = 'ORDER_COMPLETED'
 WHERE order_state = 'INVENTORY_DEDUCTED';

CREATE TABLE IF NOT EXISTS order_details (
    detail_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    item_id BIGINT,
    option_id BIGINT,
    price BIGINT,
    amount BIGINT,
    total_price BIGINT,
    snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    order_id BIGINT NOT NULL REFERENCES orders(order_id)
);

ALTER TABLE order_details ADD COLUMN IF NOT EXISTS option_id BIGINT;
ALTER TABLE order_details ADD COLUMN IF NOT EXISTS snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

DO $$
DECLARE
    has_item_name BOOLEAN;
    has_option_name BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'order_details'
           AND column_name = 'item_name'
    ) INTO has_item_name;

    SELECT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'order_details'
           AND column_name = 'option_name'
    ) INTO has_option_name;

    IF has_item_name AND has_option_name THEN
        EXECUTE $migrate$
            UPDATE order_details
               SET snapshot_json = jsonb_build_object(
                       'productId', item_id,
                       'optionId', option_id,
                       'productName', COALESCE(item_name, ''),
                       'optionName', option_name,
                       'unitPrice', COALESCE(price, 0),
                       'totalPrice', COALESCE(total_price, 0)
                   )
             WHERE snapshot_json = '{}'::jsonb
        $migrate$;
    ELSIF has_item_name THEN
        EXECUTE $migrate$
            UPDATE order_details
               SET snapshot_json = jsonb_build_object(
                       'productId', item_id,
                       'optionId', option_id,
                       'productName', COALESCE(item_name, ''),
                       'optionName', NULL,
                       'unitPrice', COALESCE(price, 0),
                       'totalPrice', COALESCE(total_price, 0)
                   )
             WHERE snapshot_json = '{}'::jsonb
        $migrate$;
    END IF;
END $$;

ALTER TABLE order_details DROP COLUMN IF EXISTS item_name;
ALTER TABLE order_details DROP COLUMN IF EXISTS option_name;

CREATE INDEX IF NOT EXISTS idx_order_details_order_id
    ON order_details (order_id);

CREATE INDEX IF NOT EXISTS idx_order_details_user_id
    ON order_details (user_id);

CREATE TABLE IF NOT EXISTS order_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

DROP INDEX IF EXISTS idx_order_outbox_status_created;
DROP INDEX IF EXISTS idx_order_outbox_next_retry;

ALTER TABLE order_outbox DROP COLUMN IF EXISTS status;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS retry_count;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS last_error;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS next_retry_at;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS sent_at;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS locked_by;
ALTER TABLE order_outbox DROP COLUMN IF EXISTS locked_at;

CREATE TABLE IF NOT EXISTS order_event_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_event_key
    ON order_event_log (event_key);

CREATE INDEX IF NOT EXISTS idx_order_event_log_type
    ON order_event_log (event_type);
