-- =============================================================================
-- Flyway Migration V8: Synthetic Scrutiny & Re-Scrutiny Demonstration Data
-- Platform: SAGE — Institutional Approval & Compliance Platform (SIH26262)
-- Description: Seeds realistic synthetic test dossier for Extension of Approval (EoA)
--              demonstrating automated scrutiny, deficiency notice, and re-scrutiny diffs.
-- =============================================================================

-- Ensure demonstration institution exists
INSERT INTO institutions (institutional_identifier, name, region)
VALUES ('INST-1025', 'ABC Institute of Technology', 'Northern Region')
ON CONFLICT (institutional_identifier) DO UPDATE SET region = EXCLUDED.region;

-- Ensure application APP-2026-1042 is linked to INST-1025
INSERT INTO applications (institution_id, application_reference, application_type, academic_year, status, current_stage, owner_name, owner_id, due_date, observations, version)
SELECT id, 'APP-2026-1042', 'Extension of Approval (EoA)', '2026-2027', 'Returned', 'Re-scrutiny', 'B. Singh (Scrutiny Officer)', 'USR-SO-01', CURRENT_DATE + INTERVAL '12 days', 'Deficiency notice issued for expired Fire NOC and address discrepancy in Affidavit 2.', 1
FROM institutions WHERE institutional_identifier = 'INST-1025'
ON CONFLICT (application_reference) DO UPDATE
SET current_stage = 'Re-scrutiny',
    status = 'Returned',
    deficiencies_json = '["Fire Safety Certificate expired on 31/12/2025. Mandatory renewal required under APH 2026-27 Clause 2.14.","Material address discrepancy between Application dossier (Plot 4, Knowledge Park III) and Affidavit 2 (Sector 62, Noida)."]';

-- 1. Seed Synthetic Application Documents (v1 Original Submissions)
INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_FIRE_SAFETY', 'Fire Safety Certificate', 'fire_noc_v1_exp.pdf', 'Fire_Safety_NOC_2024.pdf', 'sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/fire_noc_v1_exp.pdf', 145200, 'application/pdf', 'hash_fire_v1_exp', 1, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_FIRE_SAFETY' AND d.version = 1);

INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_AFFIDAVIT_2', 'Affidavit 2 (Format 2)', 'affidavit_2_v1.pdf', 'Affidavit_2_Registered.pdf', 'sage-application-documents/APP-2026-1042/DOC_AFFIDAVIT_2/affidavit_2_v1.pdf', 210400, 'application/pdf', 'hash_affidavit_v1', 1, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_AFFIDAVIT_2' AND d.version = 1);

INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_OCCUPANCY_CERT', 'Occupancy / Completion Certificate (Form D)', 'occupancy_form_d.pdf', 'Municipal_Occupancy_Form_D.pdf', 'sage-application-documents/APP-2026-1042/DOC_OCCUPANCY_CERT/occupancy_form_d.pdf', 312000, 'application/pdf', 'hash_occupancy_v1', 1, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_OCCUPANCY_CERT' AND d.version = 1);

INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_STRUCTURAL_STABILITY', 'Structural Stability Certificate', 'structural_stability.pdf', 'Structural_Stability_Engineer_Cert.pdf', 'sage-application-documents/APP-2026-1042/DOC_STRUCTURAL_STABILITY/structural_stability.pdf', 188500, 'application/pdf', 'hash_struct_v1', 1, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_STRUCTURAL_STABILITY' AND d.version = 1);

INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_SATELLITE_MAP', 'Satellite Map with Coordinates', 'satellite_map.pdf', 'Geo_Tagged_Satellite_Survey_Map.pdf', 'sage-application-documents/APP-2026-1042/DOC_SATELLITE_MAP/satellite_map.pdf', 524100, 'application/pdf', 'hash_sat_v1', 1, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_SATELLITE_MAP' AND d.version = 1);

-- 2. Seed Corrected Document Revision (v2 Renewed Fire NOC)
INSERT INTO application_documents (application_id, requirement_code, document_name, file_name, original_file_name, storage_path, file_size, content_type, file_hash, version, status, uploaded_by)
SELECT a.id, 'DOC_FIRE_SAFETY', 'Fire Safety Certificate', 'fire_noc_v2_renewed.pdf', 'Fire_Safety_Renewed_NOC_2026_27.pdf', 'sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/fire_noc_v2_renewed.pdf', 156300, 'application/pdf', 'hash_fire_v2_renewed', 2, 'UPLOADED', 'USR-INST-01'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
AND NOT EXISTS (SELECT 1 FROM application_documents d WHERE d.application_id = a.id AND d.requirement_code = 'DOC_FIRE_SAFETY' AND d.version = 2);

-- 3. Seed Preliminary Scrutiny Assessment (Cycle 1)
INSERT INTO scrutiny_assessments (assessment_reference, application_id, institution_id, assessment_type, cycle_number, status, summary_outcome, rules_version, assessed_by, total_checks, passed_checks, deficiency_checks, review_checks, summary_notes)
SELECT 'SCR-APP-2026-1042-01', a.id, a.institution_id, 'PRELIMINARY_SCRUTINY', 1, 'COMPLETED', 'POTENTIAL_DEFICIENCIES', 'APH_2024_27_V1', 'SYSTEM_AUTOMATION', 5, 3, 2, 0, 'Preliminary automated scrutiny completed via PDFBox text analysis and Tess4J OCR engine. Two potential regulatory deficiencies flagged: expired Fire NOC and cross-document address mismatch.'
FROM applications a WHERE a.application_reference = 'APP-2026-1042'
ON CONFLICT (assessment_reference) DO NOTHING;

-- 4. Seed Extractions for Cycle 1
INSERT INTO scrutiny_document_extractions (assessment_id, application_id, document_id, requirement_code, document_name, document_version, extraction_method, extracted_text_snippet, extracted_fields_json, confidence_score, ocr_status)
SELECT s.id, a.id, d.id, 'DOC_FIRE_SAFETY', 'Fire Safety Certificate', 1, 'APACHE_PDFBOX_TEXT', 'GOVERNMENT OF UTTAR PRADESH FIRE & RESCUE SERVICES DEPARTMENT. NOC No: NOC/FIRE/GBN/2024/4910. Date of Issue: 10/01/2024. Valid Up To / Expiry: 31/12/2025. ABC Institute of Technology.', '{"expiryDate":"2025-12-31","issueDate":"2024-01-10","certificateNumber":"NOC/FIRE/GBN/2024/4910","institutionName":"ABC Institute of Technology"}', 0.98, 'SUCCESS'
FROM scrutiny_assessments s
JOIN applications a ON a.id = s.application_id
JOIN application_documents d ON d.application_id = a.id AND d.requirement_code = 'DOC_FIRE_SAFETY' AND d.version = 1
WHERE s.assessment_reference = 'SCR-APP-2026-1042-01'
AND NOT EXISTS (SELECT 1 FROM scrutiny_document_extractions e WHERE e.assessment_id = s.id AND e.requirement_code = 'DOC_FIRE_SAFETY' AND e.document_version = 1);

INSERT INTO scrutiny_document_extractions (assessment_id, application_id, document_id, requirement_code, document_name, document_version, extraction_method, extracted_text_snippet, extracted_fields_json, confidence_score, ocr_status)
SELECT s.id, a.id, d.id, 'DOC_AFFIDAVIT_2', 'Affidavit 2 (Format 2)', 1, 'APACHE_PDFBOX_TEXT', 'AFFIDAVIT UNDER FORMAT 2. I Dr. R. K. Sharma on behalf of ABC Institute of Technology located at C-45, Sector 62, Noida, Gautam Buddha Nagar, Uttar Pradesh 201301 do hereby solemnly affirm.', '{"issueDate":"2026-01-14","institutionName":"ABC Institute of Technology","address":"C-45, Sector 62, Noida, Gautam Buddha Nagar, Uttar Pradesh 201301","pinCode":"201301"}', 0.96, 'SUCCESS'
FROM scrutiny_assessments s
JOIN applications a ON a.id = s.application_id
JOIN application_documents d ON d.application_id = a.id AND d.requirement_code = 'DOC_AFFIDAVIT_2' AND d.version = 1
WHERE s.assessment_reference = 'SCR-APP-2026-1042-01'
AND NOT EXISTS (SELECT 1 FROM scrutiny_document_extractions e WHERE e.assessment_id = s.id AND e.requirement_code = 'DOC_AFFIDAVIT_2' AND e.document_version = 1);

-- 5. Seed Preliminary Findings for Cycle 1
INSERT INTO scrutiny_findings (assessment_id, application_id, document_id, requirement_code, rule_code, rule_name, rule_reference, finding_type, outcome, severity, expected_condition, actual_value, explanation, recommended_action, officer_decision, officer_remarks, reviewed_by, reviewed_at)
SELECT s.id, a.id, d.id, 'DOC_FIRE_SAFETY', 'RULE_CERT_VALIDITY_EXPIRED_DOC_FIRE_SAFETY', 'Certificate Validity: Fire Safety Certificate', 'APH 2026-27 Chapter II Clause 2.14', 'CERTIFICATE_VALIDITY', 'POTENTIAL_DEFICIENCY', 'BLOCKING', 'Certificate must be valid on assessment date (Effective through at least 2026-03-01)', '2025-12-31', 'The extracted expiry date for Fire Safety Certificate is 31 December 2025, which is earlier than the statutory assessment threshold.', 'Verify document validity and issue formal deficiency notice to obtain renewed certificate.', 'CONFIRMED', 'Expired Fire Safety NOC confirmed. Mandatory blocking deficiency.', 'B. Singh (Scrutiny Officer)', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM scrutiny_assessments s
JOIN applications a ON a.id = s.application_id
JOIN application_documents d ON d.application_id = a.id AND d.requirement_code = 'DOC_FIRE_SAFETY' AND d.version = 1
WHERE s.assessment_reference = 'SCR-APP-2026-1042-01'
AND NOT EXISTS (SELECT 1 FROM scrutiny_findings f WHERE f.assessment_id = s.id AND f.rule_code = 'RULE_CERT_VALIDITY_EXPIRED_DOC_FIRE_SAFETY');

INSERT INTO scrutiny_findings (assessment_id, application_id, document_id, requirement_code, rule_code, rule_name, rule_reference, finding_type, outcome, severity, expected_condition, actual_value, explanation, recommended_action, officer_decision, officer_remarks, reviewed_by, reviewed_at)
SELECT s.id, a.id, d.id, 'DOC_AFFIDAVIT_2', 'RULE_CONSISTENCY_ADDRESS_MISMATCH_DOC_AFFIDAVIT_2', 'Cross-Document Address Verification', 'APH 2026-27 Annexure 2 Format 2', 'CROSS_DOCUMENT_CONSISTENCY', 'POTENTIAL_DEFICIENCY', 'BLOCKING', 'Address must match registered institutional location (Plot 4, Knowledge Park III, Greater Noida)', 'C-45, Sector 62, Noida', 'Address extracted from Affidavit 2 differs materially from application dossier record.', 'Review uploaded affidavit and require institutional clarification or amended notary document.', 'CONFIRMED', 'Material address discrepancy confirmed between campus location and affidavit notary text.', 'B. Singh (Scrutiny Officer)', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM scrutiny_assessments s
JOIN applications a ON a.id = s.application_id
JOIN application_documents d ON d.application_id = a.id AND d.requirement_code = 'DOC_AFFIDAVIT_2' AND d.version = 1
WHERE s.assessment_reference = 'SCR-APP-2026-1042-01'
AND NOT EXISTS (SELECT 1 FROM scrutiny_findings f WHERE f.assessment_id = s.id AND f.rule_code = 'RULE_CONSISTENCY_ADDRESS_MISMATCH_DOC_AFFIDAVIT_2');

-- 6. Seed Formal Deficiency Records
INSERT INTO scrutiny_deficiencies (deficiency_reference, application_id, assessment_id, requirement_code, title, description, rule_reference, associated_document_id, associated_document_version, original_evidence_text, officer_observation, blocking, due_date, status, issued_by, institution_explanation, corrected_document_id, corrected_document_version)
SELECT 'DEF-2026-0001', a.id, s.id, 'DOC_FIRE_SAFETY', 'Expired Fire Safety Certificate', 'The submitted Fire Safety Certificate expired on 31 December 2025. An unexpired certificate valid for Academic Year 2026-27 is mandatory.', 'APH 2026-27 Chapter II Clause 2.14', d1.id, 1, 'Extracted Expiry Date: 2025-12-31', 'Upload renewed Fire Safety Certificate issued by the competent Fire & Rescue Authority.', true, CURRENT_DATE + INTERVAL '12 days', 'RESPONSE_SUBMITTED', 'B. Singh (Scrutiny Officer)', 'Renewed Fire Safety Certificate obtained from Chief Fire Officer Gautam Buddha Nagar valid through 31 December 2027 has been attached.', d2.id, 2
FROM applications a
JOIN scrutiny_assessments s ON s.application_id = a.id AND s.assessment_reference = 'SCR-APP-2026-1042-01'
JOIN application_documents d1 ON d1.application_id = a.id AND d1.requirement_code = 'DOC_FIRE_SAFETY' AND d1.version = 1
JOIN application_documents d2 ON d2.application_id = a.id AND d2.requirement_code = 'DOC_FIRE_SAFETY' AND d2.version = 2
WHERE a.application_reference = 'APP-2026-1042'
ON CONFLICT (deficiency_reference) DO NOTHING;

-- 7. Seed Re-Scrutiny Comparison (Original vs Corrected Evidence)
INSERT INTO scrutiny_rescrutiny_comparisons (assessment_id, deficiency_id, original_document_id, original_document_version, original_extracted_value, corrected_document_id, corrected_document_version, corrected_extracted_value, comparison_status, comparison_summary, remaining_issues)
SELECT s.id, def.id, d1.id, 1, 'Certificate Expiry Date: 31/12/2025 [EXPIRED]', d2.id, 2, 'Certificate Expiry Date: 31/12/2027 [VALID]', 'RECTIFIED_PENDING_CONFIRMATION', 'Extracted certificate expiry date has changed from 31/12/2025 to 31/12/2027 and now satisfies the statutory date-validity check. Reviewer confirmation pending.', 'Verify authenticity of renewed certificate with issuing authority and record official clearance decision.'
FROM scrutiny_assessments s
JOIN applications a ON a.id = s.application_id
JOIN scrutiny_deficiencies def ON def.application_id = a.id AND def.deficiency_reference = 'DEF-2026-0001'
JOIN application_documents d1 ON d1.id = def.associated_document_id
JOIN application_documents d2 ON d2.id = def.corrected_document_id
WHERE s.assessment_reference = 'SCR-APP-2026-1042-01'
AND NOT EXISTS (SELECT 1 FROM scrutiny_rescrutiny_comparisons c WHERE c.deficiency_id = def.id);
