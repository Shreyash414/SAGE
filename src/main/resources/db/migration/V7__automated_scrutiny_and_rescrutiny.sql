-- =============================================================================
-- Flyway Migration V7: Automated Scrutiny, Document Extraction & Deficiency Management
-- Platform: SAGE — Institutional Approval & Compliance Platform
-- Description: Creates persistent entities for automated scrutiny assessments,
--              OCR/text document extraction results, automated rule findings,
--              deficiency management lifecycle, and re-scrutiny comparisons.
-- =============================================================================

-- 1. Scrutiny Assessments Table
CREATE TABLE IF NOT EXISTS scrutiny_assessments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_reference VARCHAR(64) NOT NULL UNIQUE,
    application_id BIGINT NOT NULL,
    institution_id BIGINT,
    assessment_type VARCHAR(32) NOT NULL DEFAULT 'PRELIMINARY_SCRUTINY',
    cycle_number INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    summary_outcome VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rules_version VARCHAR(32) NOT NULL DEFAULT 'APH_2024_27_V1',
    assessed_by VARCHAR(128) NOT NULL DEFAULT 'SYSTEM_AUTOMATION',
    total_checks INT NOT NULL DEFAULT 0,
    passed_checks INT NOT NULL DEFAULT 0,
    deficiency_checks INT NOT NULL DEFAULT 0,
    review_checks INT NOT NULL DEFAULT 0,
    summary_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrutiny_assessments_application FOREIGN KEY (application_id)
        REFERENCES applications (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_scrutiny_assessments_app_id ON scrutiny_assessments (application_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_assessments_status ON scrutiny_assessments (status);
CREATE INDEX IF NOT EXISTS idx_scrutiny_assessments_created_at ON scrutiny_assessments (created_at);

-- 2. Scrutiny Document Extractions Table (OCR & Searchable PDF Results)
CREATE TABLE IF NOT EXISTS scrutiny_document_extractions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    document_id BIGINT,
    requirement_code VARCHAR(64) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_version INT NOT NULL DEFAULT 1,
    extraction_method VARCHAR(64) NOT NULL DEFAULT 'PDFBOX_SEARCHABLE_TEXT',
    extracted_text_snippet TEXT,
    extracted_fields_json TEXT,
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ocr_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrutiny_extractions_assessment FOREIGN KEY (assessment_id)
        REFERENCES scrutiny_assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_scrutiny_extractions_document FOREIGN KEY (document_id)
        REFERENCES application_documents (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_scrutiny_extractions_assessment ON scrutiny_document_extractions (assessment_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_extractions_app_id ON scrutiny_document_extractions (application_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_extractions_req_code ON scrutiny_document_extractions (requirement_code);

-- 3. Scrutiny Findings Table (Individual Automated Check Results)
CREATE TABLE IF NOT EXISTS scrutiny_findings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    document_id BIGINT,
    requirement_code VARCHAR(64),
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_reference VARCHAR(128) NOT NULL,
    finding_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL DEFAULT 'NOT_EVALUATED',
    severity VARCHAR(32) NOT NULL DEFAULT 'BLOCKING',
    expected_condition TEXT NOT NULL,
    actual_value TEXT,
    explanation TEXT NOT NULL,
    recommended_action TEXT,
    officer_decision VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    officer_remarks TEXT,
    reviewed_by VARCHAR(128),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrutiny_findings_assessment FOREIGN KEY (assessment_id)
        REFERENCES scrutiny_assessments (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_scrutiny_findings_assessment ON scrutiny_findings (assessment_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_findings_app_id ON scrutiny_findings (application_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_findings_outcome ON scrutiny_findings (outcome);
CREATE INDEX IF NOT EXISTS idx_scrutiny_findings_officer_decision ON scrutiny_findings (officer_decision);

-- 4. Scrutiny Deficiencies Table (Official Confirmed Deficiencies)
CREATE TABLE IF NOT EXISTS scrutiny_deficiencies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deficiency_reference VARCHAR(64) NOT NULL UNIQUE,
    application_id BIGINT NOT NULL,
    assessment_id BIGINT,
    finding_id BIGINT,
    requirement_code VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    rule_reference VARCHAR(128) NOT NULL,
    associated_document_id BIGINT,
    associated_document_version INT NOT NULL DEFAULT 1,
    original_evidence_text TEXT,
    officer_observation TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    blocking BOOLEAN NOT NULL DEFAULT TRUE,
    due_date DATE,
    institution_explanation TEXT,
    corrected_document_id BIGINT,
    corrected_document_version INT,
    reviewer_decision VARCHAR(32),
    reviewer_remarks TEXT,
    issued_by VARCHAR(128),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrutiny_deficiencies_application FOREIGN KEY (application_id)
        REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_scrutiny_deficiencies_assessment FOREIGN KEY (assessment_id)
        REFERENCES scrutiny_assessments (id) ON DELETE SET NULL,
    CONSTRAINT fk_scrutiny_deficiencies_finding FOREIGN KEY (finding_id)
        REFERENCES scrutiny_findings (id) ON DELETE SET NULL,
    CONSTRAINT fk_scrutiny_deficiencies_doc FOREIGN KEY (associated_document_id)
        REFERENCES application_documents (id) ON DELETE SET NULL,
    CONSTRAINT fk_scrutiny_deficiencies_corr_doc FOREIGN KEY (corrected_document_id)
        REFERENCES application_documents (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_scrutiny_deficiencies_app_id ON scrutiny_deficiencies (application_id);
CREATE INDEX IF NOT EXISTS idx_scrutiny_deficiencies_status ON scrutiny_deficiencies (status);
CREATE INDEX IF NOT EXISTS idx_scrutiny_deficiencies_blocking ON scrutiny_deficiencies (blocking);

-- 5. Scrutiny Re-Scrutiny Comparisons Table (Side-by-side Version Diffs)
CREATE TABLE IF NOT EXISTS scrutiny_rescrutiny_comparisons (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL,
    deficiency_id BIGINT NOT NULL,
    original_document_id BIGINT,
    original_document_version INT NOT NULL DEFAULT 1,
    original_extracted_value TEXT,
    corrected_document_id BIGINT,
    corrected_document_version INT NOT NULL DEFAULT 2,
    corrected_extracted_value TEXT,
    comparison_status VARCHAR(64) NOT NULL DEFAULT 'RECTIFIED_PENDING_CONFIRMATION',
    comparison_summary TEXT NOT NULL,
    remaining_issues TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rescrutiny_comp_assessment FOREIGN KEY (assessment_id)
        REFERENCES scrutiny_assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_rescrutiny_comp_deficiency FOREIGN KEY (deficiency_id)
        REFERENCES scrutiny_deficiencies (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rescrutiny_comp_assessment ON scrutiny_rescrutiny_comparisons (assessment_id);
CREATE INDEX IF NOT EXISTS idx_rescrutiny_comp_deficiency ON scrutiny_rescrutiny_comparisons (deficiency_id);
