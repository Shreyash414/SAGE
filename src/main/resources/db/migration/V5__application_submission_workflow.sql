-- =============================================================================
-- Flyway Migration V5: Complete AICTE Application Submission Workflow
-- Platform: SAGE — Institutional Approval & Compliance Platform
-- Description: Extends applications with structured form data and submitted snapshots,
--              creates application_documents registry table, and adds performance indexes.
-- =============================================================================

-- 1. Extend applications table with dynamic form data and submitted snapshot archives
ALTER TABLE applications ADD COLUMN IF NOT EXISTS form_data_json TEXT;
ALTER TABLE applications ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE applications ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(128);
ALTER TABLE applications ADD COLUMN IF NOT EXISTS submitted_snapshot_json TEXT;

-- 2. Create application_documents table for versioned, requirement-mapped document attachments
CREATE TABLE IF NOT EXISTS application_documents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    requirement_code VARCHAR(64) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by VARCHAR(128) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_docs_application FOREIGN KEY (application_id)
        REFERENCES applications (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_docs_app_id ON application_documents (application_id);
CREATE INDEX IF NOT EXISTS idx_app_docs_req_code ON application_documents (application_id, requirement_code);
CREATE INDEX IF NOT EXISTS idx_app_docs_uploaded_at ON application_documents (uploaded_at);
