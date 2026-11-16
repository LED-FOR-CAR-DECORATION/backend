-- Migration: Support multiple evidence image URLs for warranty tickets via JSONB array (DEV-14)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'warranty_ticket'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'warranty_ticket' AND column_name = 'evidence_media_urls'
        ) THEN
            ALTER TABLE warranty_ticket ADD COLUMN evidence_media_urls JSONB NOT NULL DEFAULT '[]'::jsonb;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'warranty_ticket' AND column_name = 'evidence_media_url'
        ) THEN
            UPDATE warranty_ticket 
            SET evidence_media_urls = jsonb_build_array(evidence_media_url)
            WHERE evidence_media_url IS NOT NULL 
              AND (evidence_media_urls IS NULL OR evidence_media_urls = '[]'::jsonb);

            ALTER TABLE warranty_ticket DROP COLUMN evidence_media_url;
        END IF;
    END IF;
END $$;
