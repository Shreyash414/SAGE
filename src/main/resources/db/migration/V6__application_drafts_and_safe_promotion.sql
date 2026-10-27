-- =============================================================================
-- Flyway Migration V6: Persistent Application Drafts and Safe Promotion
-- Platform: SAGE — Institutional Approval & Compliance Platform
-- =============================================================================

-- 1. Create application_drafts table for staging incomplete application dossiers
CREATE TABLE IF NOT EXISTS application_drafts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    draft_reference VARCHAR(64) NOT NULL UNIQUE,
    institution_id BIGINT NOT NULL,
    applicant_id VARCHAR(64) NOT NULL,
    application_type VARCHAR(64) NOT NULL,
    academic_year VARCHAR(16) NOT NULL,
    form_data_json TEXT,
    requirements_version VARCHAR(32) NOT NULL DEFAULT 'APH_2024_27_V1',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    promoted_application_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_drafts_institution FOREIGN KEY (institution_id)
        REFERENCES institutions (id) ON DELETE CASCADE,
    CONSTRAINT fk_drafts_promoted_app FOREIGN KEY (promoted_application_id)
        REFERENCES applications (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_drafts_inst_id ON application_drafts (institution_id);
CREATE INDEX IF NOT EXISTS idx_drafts_status ON application_drafts (status);
CREATE INDEX IF NOT EXISTS idx_drafts_draft_ref ON application_drafts (draft_reference);
CREATE INDEX IF NOT EXISTS idx_drafts_updated_at ON application_drafts (updated_at);

-- 2. Adapt application_documents to associate with drafts prior to formal promotion
ALTER TABLE application_documents ALTER COLUMN application_id DROP NOT NULL;
ALTER TABLE application_documents ADD COLUMN IF NOT EXISTS draft_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_docs_draft'
    ) THEN
        ALTER TABLE application_documents
            ADD CONSTRAINT fk_app_docs_draft FOREIGN KEY (draft_id)
                REFERENCES application_drafts (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_app_docs_draft_id ON application_documents (draft_id);
CREATE INDEX IF NOT EXISTS idx_app_docs_draft_req ON application_documents (draft_id, requirement_code);
