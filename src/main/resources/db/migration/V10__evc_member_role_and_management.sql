-- =============================================================================
-- Flyway Migration V10: EVC Member Role, Profiles, and Inspection Assignments
-- Platform: SAGE — Institutional Approval & Compliance Platform
-- Description: Creates evc_member_profiles and evc_member_assignments tables,
--              seeds canonical EVC Member platform identity, and links initial assignments.
-- =============================================================================

-- 1. Canonical EVC Member Profiles Directory
CREATE TABLE IF NOT EXISTS evc_member_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(128) NOT NULL,
    designation VARCHAR(128) NOT NULL,
    organization VARCHAR(255) NOT NULL,
    expertise VARCHAR(255) NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED',
    availability_status VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evc_member_profiles_user_id ON evc_member_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_evc_member_profiles_verification ON evc_member_profiles(verification_status);
CREATE INDEX IF NOT EXISTS idx_evc_member_profiles_availability ON evc_member_profiles(availability_status);
CREATE INDEX IF NOT EXISTS idx_evc_member_profiles_expertise ON evc_member_profiles(expertise);

-- 2. EVC Member Committee Visit Assignments
CREATE TABLE IF NOT EXISTS evc_member_assignments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evc_visit_id BIGINT NOT NULL REFERENCES evc_visits(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES evc_member_profiles(id) ON DELETE CASCADE,
    committee_role VARCHAR(64) NOT NULL DEFAULT 'MEMBER',
    notes VARCHAR(255),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_evc_assignment_visit_member UNIQUE (evc_visit_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_evc_member_assign_visit ON evc_member_assignments(evc_visit_id);
CREATE INDEX IF NOT EXISTS idx_evc_member_assign_member ON evc_member_assignments(member_id);

-- 3. Seed Canonical EVC Member Identity
INSERT INTO users (
    user_id, full_name, email, password_hash, role, status, region,
    institution_id, institution_name, designation, created_at, updated_at
) VALUES (
    'USR-EVCM-01',
    'Dr. Arvind Saxena',
    'member.evc@aicte-india.gov.in',
    '$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai',
    'EVC Member',
    'ACTIVE',
    'Northern Region',
    NULL,
    NULL,
    'Professor of Computer Science & Engineering',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (user_id) DO NOTHING;

-- 4. Seed Canonical EVC Member Profile linked to USR-EVCM-01
INSERT INTO evc_member_profiles (
    member_id, user_id, full_name, designation, organization, expertise,
    verification_status, availability_status, phone, created_at, updated_at
)
SELECT
    'MEM-EVCM-01',
    u.id,
    'Dr. Arvind Saxena',
    'Professor of Computer Science & Engineering',
    'Indian Institute of Technology Delhi',
    'Computer Science & Engineering, Artificial Intelligence',
    'VERIFIED',
    'AVAILABLE',
    '+91 98765 43210',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM users u
WHERE u.user_id = 'USR-EVCM-01'
ON CONFLICT (member_id) DO NOTHING;

-- 5. Seed initial committee assignments for existing EVC visits
INSERT INTO evc_member_assignments (
    evc_visit_id, member_id, committee_role, notes, assigned_at
)
SELECT
    v.id,
    m.id,
    'SUBJECT_SPECIALIST',
    'Assigned as primary subject specialist for technical verification',
    CURRENT_TIMESTAMP
FROM evc_visits v
CROSS JOIN evc_member_profiles m
WHERE m.member_id = 'MEM-EVCM-01'
LIMIT 2
ON CONFLICT (evc_visit_id, member_id) DO NOTHING;

-- 6. Record System Audit Log for V7 Migration
INSERT INTO audit_events (
    event_type, actor_id, target_entity_type, target_entity_id, outcome, metadata_json
) VALUES (
    'SYSTEM_INITIALIZATION',
    'SYSTEM',
    'SYSTEM',
    'EVC-MEMBER-SUBSYSTEM',
    'SUCCESS',
    '{"description":"SAGE EVC Member role, profile repository, and assignment schema initialized via V10 migration."}'
);

