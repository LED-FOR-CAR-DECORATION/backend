-- Reference SQL migration for LED hardware catalog listing (#89)
ALTER TABLE IF EXISTS led_model
    ADD COLUMN IF NOT EXISTS product_id BIGINT,
    ADD COLUMN IF NOT EXISTS width_px INTEGER,
    ADD COLUMN IF NOT EXISTS height_px INTEGER,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_led_model_product_id
    ON led_model (product_id)
    WHERE product_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_led_model_status
    ON led_model (status);

CREATE INDEX IF NOT EXISTS idx_led_model_dimensions
    ON led_model (width_px, height_px);
