-- Remove cart item display snapshot storage.
-- Cart now stores only product/option identifiers, quantity, and selection state.

ALTER TABLE cart_item DROP COLUMN IF EXISTS snapshot_json;
