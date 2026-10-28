-- ============================================================================
-- AICTE SAGE PLATFORM: V9 MIGRATION
-- OCR Confidence Realism & Document Extraction Metadata
-- ============================================================================
-- 1. Make confidence_score nullable so native digital PDFBox extractions do not
--    fabricate artificial OCR confidence numbers.
-- 2. Add text_character_count, page_count, and validation_metric for accurate audit.
-- 3. Reset existing digital PDFBox records from fabricated confidence values to NULL.
-- ============================================================================

ALTER TABLE scrutiny_document_extractions ALTER COLUMN confidence_score DROP NOT NULL;

ALTER TABLE scrutiny_document_extractions ADD COLUMN IF NOT EXISTS text_character_count INT DEFAULT 0;
ALTER TABLE scrutiny_document_extractions ADD COLUMN IF NOT EXISTS page_count INT DEFAULT 1;
ALTER TABLE scrutiny_document_extractions ADD COLUMN IF NOT EXISTS validation_metric VARCHAR(128);

UPDATE scrutiny_document_extractions
SET confidence_score = NULL,
    validation_metric = 'N/A (Embedded Text)'
WHERE extraction_method = 'PDFBOX_SEARCHABLE_TEXT' OR extraction_method = 'APACHE_PDFBOX_TEXT';
