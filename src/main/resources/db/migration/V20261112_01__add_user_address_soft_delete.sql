-- Migration: Add soft-delete support columns and active-address index to user_address (IAM-25)
-- Compatible with Supabase PostgreSQL (execute in Supabase SQL Editor or pgAdmin; idempotent if re-run).

ALTER TABLE user_address
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_user_address_customer_active
    ON user_address (customer_id, is_deleted, is_default DESC, updated_at DESC NULLS LAST, created_at DESC, address_id DESC);

-- Rollback (execute manually only if reverting IAM-25):
-- DROP INDEX IF EXISTS idx_user_address_customer_active;
-- ALTER TABLE user_address
--     DROP COLUMN IF EXISTS deleted_at,
--     DROP COLUMN IF EXISTS is_deleted;
