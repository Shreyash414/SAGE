-- =============================================================================
-- Flyway Migration V4: Persistent User Directory and Administrative Audit Trail
-- Platform: SAGE — Institutional Approval & Compliance Platform
-- Description: Establishes persistent user directory, account lifecycle statuses,
--              enhances audit event metadata, and seeds canonical platform identities.
-- =============================================================================

-- 1. Canonical Users Directory
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    region VARCHAR(64),
    institution_id VARCHAR(64),
    institution_name VARCHAR(255),
    designation VARCHAR(128),
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_user_id UNIQUE (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_full_name ON users (full_name);
CREATE INDEX idx_users_created_at ON users (created_at);

-- 2. Extend Audit Events with outcome status and correlation/request tracking
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS outcome VARCHAR(32) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_outcome ON audit_events (outcome);

-- 3. Seed Canonical Platform Identities (Pass: SAGE@Admin2026! for admin, DemoUser@2026! for staff)
INSERT INTO users (
    user_id, full_name, email, password_hash, role, status, region,
    institution_id, institution_name, designation, created_at, updated_at
) VALUES
(
    'USR-ADM-01',
    'System Administrator',
    'admin@aicte-india.gov.in',
    '$2a$10$GlkGp/BrUnoLScicCP6pc.eFLP77ySct/SRmyl70/w6SKlQhJEpuq',
    'Administrator',
    'ACTIVE',
    'Platform-wide',
    NULL,
    NULL,
    'Technical Platform Administrator',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-INST-01',
    'Dr. Ramesh Kumar',
    'principal@abctech.edu.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'Institution Representative',
    'ACTIVE',
    'Northern Region',
    'INST-1025',
    'ABC Institute of Technology',
    'Principal / Authorized Representative',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-RO-01',
    'A. Sharma',
    'ro.north@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'Regional Officer',
    'ACTIVE',
    'Northern Region',
    NULL,
    NULL,
    'Regional Officer (North)',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-SO-01',
    'B. Singh',
    'scrutiny.north@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'Scrutiny Officer',
    'ACTIVE',
    'Northern Region',
    NULL,
    NULL,
    'Scrutiny / Bureau Desk Officer',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-EVC-01',
    'C. Verma',
    'evc.coord@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'EVC Coordinator',
    'ACTIVE',
    'Multi-Region',
    NULL,
    NULL,
    'EVC Operations Coordinator',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-CR-01',
    'D. Patel',
    'compliance.rev@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'Compliance Reviewer',
    'ACTIVE',
    'Western Region',
    NULL,
    NULL,
    'Compliance & Evidence Reviewer',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'USR-MGT-01',
    'Prof. H. R. Adhikari',
    'advisor.approval@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'Senior Management',
    'ACTIVE',
    'All India',
    NULL,
    NULL,
    'Competent Authority / Advisor Approval',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (user_id) DO NOTHING;

-- 4. Log initial system provisioning audit record
INSERT INTO audit_events (event_type, actor_id, target_entity_type, target_entity_id, outcome, metadata_json)
VALUES (
    'SYSTEM_INITIALIZATION',
    'SYSTEM',
    'SYSTEM',
    'SAGE-PLATFORM',
    'SUCCESS',
    '{"description":"SAGE User Directory and Administrative Subsystem initialized via V4 migration."}'
);
