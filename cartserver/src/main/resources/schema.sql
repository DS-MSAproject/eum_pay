-- cart.user_id: required for ON CONFLICT (user_id) DO NOTHING in CartRepository
CREATE UNIQUE INDEX IF NOT EXISTS idx_cart_user_id
    ON cart (user_id);

-- cart_item: partial unique indexes required for UPSERT conflict targets
-- ON CONFLICT (cart_id, item_id, option_id) WHERE option_id IS NOT NULL
CREATE UNIQUE INDEX IF NOT EXISTS idx_cart_item_with_option
    ON cart_item (cart_id, item_id, option_id)
    WHERE option_id IS NOT NULL;

-- ON CONFLICT (cart_id, item_id) WHERE option_id IS NULL
CREATE UNIQUE INDEX IF NOT EXISTS idx_cart_item_without_option
    ON cart_item (cart_id, item_id)
    WHERE option_id IS NULL;
