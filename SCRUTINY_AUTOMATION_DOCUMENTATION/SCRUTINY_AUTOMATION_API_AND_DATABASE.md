# SAGE Scrutiny Automation — API & Database Specification

**Platform:** SAGE — Institutional Approval & Compliance Platform (SIH 26262)  
**File Location:** [`SCRUTINY_AUTOMATION_DOCUMENTATION/SCRUTINY_AUTOMATION_API_AND_DATABASE.md`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/SCRUTINY_AUTOMATION_DOCUMENTATION/SCRUTINY_AUTOMATION_API_AND_DATABASE.md)  
**Date:** October 28, 2026  

---

## 1. REST API Inventory (`ScrutinyController.java`)

All endpoints are hosted under the prefix:  
`/api/v1/applications/{applicationId}`  
Source: [`ScrutinyController.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/controller/ScrutinyController.java)

| # | HTTP Method | Endpoint Path | Required Roles | Request Body / Params | Response Payload | Description |
|---|---|---|---|---|---|---|
| **1** | `POST` | `/scrutiny/run` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMINISTRATOR`, `ADMIN` | None | `PreliminaryScrutinyReportResponse` | Executes PDFBox/Tess4J extraction, evaluates statutory rules, saves findings, and returns summary metrics. |
| **2** | `GET` | `/scrutiny/report` | Authenticated | None | `PreliminaryScrutinyReportResponse` | Retrieves latest scrutiny assessment report, including consolidated findings and extractions. |
| **3** | `GET` | `/scrutiny/assessments` | Authenticated | None | `List<ScrutinyAssessmentResponse>` | Lists historical assessment cycles for the application (e.g., Cycle #1, Cycle #2). |
| **4** | `GET` | `/scrutiny/findings` | Authenticated | `assessmentId` (optional), `consolidated` (boolean, default: false) | `List<ScrutinyFindingResponse>` | Returns list of evaluated preliminary findings. Supports grouped view for duplicate uploads. |
| **5** | `GET` | `/scrutiny/extractions` | Authenticated | `assessmentId` (optional) | `List<ScrutinyDocumentExtractionResponse>` | Returns all persisted text and OCR extraction records, with field sources and confidence. |
| **6** | `GET` | `/scrutiny/documents/{documentId}/preview` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMIN` | None | Raw byte stream (`inline; filename=...`) | Streams uploaded document PDF bytes directly from MinIO object storage for officer inspection. |
| **7** | `POST` | `/scrutiny/findings/{findingId}/review` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMIN` | `ReviewFindingRequest` (decision, remarks, applyToAllDuplicates) | `ScrutinyFindingResponse` | Records officer decision (`CONFIRM_DEFICIENCY`, `DISMISS_FALSE_POSITIVE`, `REQUEST_MANUAL_VERIFICATION`). |
| **8** | `GET` | `/deficiencies` | Authenticated | `status` (optional: `ISSUED`, `RESPONSE_SUBMITTED`, `VERIFIED_RESOLVED`) | `List<ScrutinyDeficiencyResponse>` | Lists formal deficiencies issued to the institution. |
| **9** | `POST` | `/deficiencies/issue` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMIN` | `IssueDeficienciesRequest` (findingIds, formalNoticeTitle, officerObservation, dueDate, blocking) | `List<ScrutinyDeficiencyResponse>` | Converts confirmed preliminary findings into formal deficiency notices and returns application to Re-scrutiny stage. |
| **10** | `POST` | `/deficiencies/{deficiencyId}/documents` | Authenticated (Institution / Officer) | Multipart file: `file` | `ApplicationDocument` | Uploads a revised document revision (Version 2) in MinIO without deleting the original version. |
| **11** | `POST` | `/deficiencies/submit-responses` | Authenticated (Institution Representative) | `SubmitDeficiencyResponsesRequest` (responses: deficiencyId, explanation) | `PreliminaryScrutinyReportResponse` | Submits institution rectifications and automatically triggers re-scrutiny comparative assessment. |
| **12** | `POST` | `/deficiencies/{deficiencyId}/review` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMIN` | `ReviewDeficiencyRequest` (decision: `VERIFIED_RESOLVED` / `REJECTED`, remarks) | `ScrutinyDeficiencyResponse` | Officer reviews rectified evidence and officially resolves or re-flags the deficiency. |
| **13** | `GET` | `/scrutiny/comparison` | Authenticated | `assessmentId` (optional) | `List<RescrutinyComparisonResponse>` | Returns side-by-side comparative diffs between original v1 and corrected v2 document evidence. |
| **14** | `POST` | `/scrutiny/rescrutiny` | `SCRUTINY_OFFICER`, `REGIONAL_OFFICER`, `ADMIN` | None | `PreliminaryScrutinyReportResponse` | Manually triggers the automated re-scrutiny comparative engine. |

---

## 2. Core Backend Classes & Services Inventory

### 2.1 Extraction & OCR Subsystem
- **[`DocumentTextExtractionService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/ocr/DocumentTextExtractionService.java):**
  - **Methods:** `extractText(byte[], fileName, contentType)`, `extractFromPdf(byte[], fileName)`, `extractFromImage(byte[], fileName)`, `isOcrAvailable()`.
  - **Threshold:** Searchable text $\ge 40$ characters routes to PDFBox embedded text; $< 40$ characters invokes PDFBox 200 DPI page renderer + Tess4J OCR fallback.
- **[`TesseractOcrService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/ocr/TesseractOcrService.java):**
  - **Methods:** `isAvailable()`, `extractTextWithConfidence(BufferedImage)`, `resolveDataPath()`.
  - **Confidence Calculation:** Iterates over `Word` tokens at level `RIL_WORD`, filters $-1.0f$ sentinels, calculates arithmetic mean of confidence scores, scales to $[0.0 - 1.0]$.
- **[`DocumentFieldExtractionService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/ocr/DocumentFieldExtractionService.java):**
  - **Methods:** `extractStructuredFields(ExtractedDocumentData, requirementCode, ApplicationDocument)`, `containsUnrelatedAcademicContent(text)`, `containsRequirementContentSignals(text, requirementCode)`.
  - **Regex Parsers:** Dates (Indian DD/MM/YYYY, ISO, textual month), certificate numbers, FDR accounts, PAN numbers, PIN codes, land acreages, and academic lab keywords.

### 2.2 Scrutiny Orchestration & Rule Engine
- **[`ScrutinyValidationService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/validation/ScrutinyValidationService.java):**
  - **Methods:** `evaluateScrutiny(...)`, `detectDuplicateDocumentUploads(...)`, `evaluateFireSafetyValidity(...)`, `evaluateCorpusFundValidity(...)`, `evaluateOccupancyValidity(...)`, `evaluateStructuralStabilityValidity(...)`, `evaluateCrossDocumentConsistency(...)`.
- **[`ScrutinyService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/service/ScrutinyService.java):**
  - **Methods:** `runAutomatedScrutiny(...)`, `getLatestReport(...)`, `reviewFinding(...)`, `issueFormalDeficiencies(...)`, `uploadCorrectedDocument(...)`, `submitDeficiencyResponses(...)`.
  - **Cache Management:** Evicts stale `UNAVAILABLE` extractions when OCR becomes available and re-scans uploaded documents.
- **[`ReScrutinyService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/service/ReScrutinyService.java):**
  - **Methods:** `runAutomatedRescrutiny(Application, actorId)`.
  - **Comparison Engine:** Side-by-side extraction diffing across versions, cycle number sequencing, and 15-second double-click idempotency guard.

---

## 3. Database Schema & Flyway Migrations

The scrutiny engine schema is provisioned by Flyway migrations [V7](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/resources/db/migration/V7__automated_scrutiny_and_rescrutiny.sql) and [V9](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/resources/db/migration/V9__ocr_confidence_and_metadata.sql).

### Table 1: `scrutiny_assessments`
Represents an automated evaluation run or re-scrutiny assessment cycle.
- `id` (BIGINT, PK, Identity)
- `assessment_reference` (VARCHAR(64), UNIQUE) — e.g. `SCR-APP-2026-601169-01`
- `application_id` (BIGINT, FK -> `applications.id` ON DELETE CASCADE)
- `institution_id` (BIGINT, Nullable)
- `assessment_type` (VARCHAR(32)) — `PRELIMINARY_SCRUTINY`, `RE_SCRUTINY`
- `cycle_number` (INT) — 1 for initial scrutiny, 2+ for re-scrutiny cycles
- `status` (VARCHAR(32)) — `COMPLETED`, `IN_PROGRESS`
- `summary_outcome` (VARCHAR(32)) — `PASSED_CLEAN`, `POTENTIAL_DEFICIENCIES_DETECTED`, `NEEDS_MANUAL_REVIEW`
- `rules_version` (VARCHAR(32)) — `APH_2024_27_V1`
- `assessed_by` (VARCHAR(128)) — Username or `SYSTEM_AUTOMATION`
- `total_checks`, `passed_checks`, `deficiency_checks`, `review_checks` (INT)
- `summary_notes` (TEXT)
- `created_at`, `updated_at` (TIMESTAMPTZ)

### Table 2: `scrutiny_document_extractions`
Stores full extracted text, structured key-value fields, provenance, and optical recognition metrics.
- `id` (BIGINT, PK, Identity)
- `assessment_id` (BIGINT, FK -> `scrutiny_assessments.id` ON DELETE CASCADE)
- `application_id` (BIGINT)
- `document_id` (BIGINT, FK -> `application_documents.id` ON DELETE SET NULL)
- `requirement_code` (VARCHAR(64)) — e.g. `DOC_FIRE_SAFETY`, `DOC_CORPUS_FUND`
- `document_name` (VARCHAR(255))
- `document_version` (INT) — 1, 2, etc.
- `extraction_method` (VARCHAR(64)) — `PDFBOX_SEARCHABLE_TEXT`, `TESSERACT_OCR_SCANNED`, `TESSERACT_OCR_IMAGE`, `EMPTY_DOCUMENT`
- `extracted_text_snippet` (TEXT) — Full raw recognized character stream
- `extracted_fields_json` (TEXT) — JSON dictionary of extracted fields + `_fieldSources` provenance
- `confidence_score` (DOUBLE PRECISION, Nullable) — `NULL` for digital text; $[0.0 - 1.0]$ for Tess4J OCR
- `ocr_status` (VARCHAR(32)) — `SUCCESS`, `UNCERTAIN`, `EMPTY`, `UNAVAILABLE`, `FAILED`
- `text_character_count` (INT, added in V9)
- `page_count` (INT, added in V9)
- `validation_metric` (VARCHAR(128), added in V9) — e.g. `N/A (Embedded Text)`, `OCR_MEASURED (95.4%)`
- `error_message` (TEXT)
- `created_at` (TIMESTAMPTZ)

### Table 3: `scrutiny_findings`
Stores every individual automated check outcome.
- `id` (BIGINT, PK, Identity)
- `assessment_id` (BIGINT, FK -> `scrutiny_assessments.id` ON DELETE CASCADE)
- `application_id` (BIGINT)
- `document_id` (BIGINT, Nullable)
- `requirement_code` (VARCHAR(64))
- `rule_code` (VARCHAR(64)) — e.g. `RULE_FIRE_NOC_VALID`, `RULE_DUPLICATE_FILE_UPLOAD_DOC_CORPUS_FUND`
- `rule_name` (VARCHAR(255))
- `rule_reference` (VARCHAR(128)) — e.g. `APH 2024-27 Clause 1.6`
- `finding_type` (VARCHAR(64)) — `TECHNICAL_VALIDATION`, `STATUTORY_COMPLIANCE`, `DOCUMENT_AUTHENTICITY`, `CROSS_DOCUMENT_CONSISTENCY`
- `outcome` (VARCHAR(32)) — `PASS`, `POTENTIAL_DEFICIENCY`, `NEEDS_MANUAL_REVIEW`, `NOT_APPLICABLE`
- `severity` (VARCHAR(32)) — `BLOCKING`, `WARNING`, `ADVISORY`
- `expected_condition` (TEXT)
- `actual_value` (TEXT)
- `explanation` (TEXT)
- `recommended_action` (TEXT)
- `officer_decision` (VARCHAR(32)) — `PENDING`, `CONFIRMED`, `DISMISSED`, `REQUEST_MANUAL_VERIFICATION`, `MANUAL_VERIFIED`
- `officer_remarks` (TEXT)
- `reviewed_by` (VARCHAR(128))
- `reviewed_at` (TIMESTAMPTZ)
- `created_at` (TIMESTAMPTZ)

### Table 4: `scrutiny_deficiencies`
Official confirmed deficiencies issued to the applicant institution.
- `id` (BIGINT, PK, Identity)
- `deficiency_reference` (VARCHAR(64), UNIQUE) — e.g. `DEF-2026-601169-001`
- `application_id` (BIGINT, FK -> `applications.id` ON DELETE CASCADE)
- `assessment_id` (BIGINT, FK -> `scrutiny_assessments.id` ON DELETE SET NULL)
- `finding_id` (BIGINT, FK -> `scrutiny_findings.id` ON DELETE SET NULL)
- `requirement_code` (VARCHAR(64))
- `title` (VARCHAR(255))
- `description` (TEXT)
- `rule_reference` (VARCHAR(128))
- `associated_document_id` (BIGINT, FK -> `application_documents.id`)
- `associated_document_version` (INT)
- `original_evidence_text` (TEXT)
- `officer_observation` (TEXT)
- `status` (VARCHAR(32)) — `ISSUED`, `RESPONSE_SUBMITTED`, `VERIFIED_RESOLVED`, `CLOSED`
- `blocking` (BOOLEAN) — Default `true`
- `due_date` (DATE)
- `institution_explanation` (TEXT)
- `corrected_document_id` (BIGINT, FK -> `application_documents.id`)
- `corrected_document_version` (INT)
- `reviewer_decision` (VARCHAR(32))
- `reviewer_remarks` (TEXT)
- `issued_by` (VARCHAR(128))
- `issued_at`, `responded_at`, `resolved_at`, `created_at`, `updated_at` (TIMESTAMPTZ)

### Table 5: `scrutiny_rescrutiny_comparisons`
Stores side-by-side evidence diffs between original and revised document versions.
- `id` (BIGINT, PK, Identity)
- `assessment_id` (BIGINT, FK -> `scrutiny_assessments.id` ON DELETE CASCADE)
- `deficiency_id` (BIGINT, FK -> `scrutiny_deficiencies.id` ON DELETE CASCADE)
- `original_document_id` (BIGINT)
- `original_document_version` (INT)
- `original_extracted_value` (TEXT) — e.g. `EXPIRED_ON_2025-12-31`
- `corrected_document_id` (BIGINT)
- `corrected_document_version` (INT)
- `corrected_extracted_value` (TEXT) — e.g. `VALID_TILL_2027-09-01`
- `comparison_status` (VARCHAR(64)) — `RECTIFIED_PENDING_CONFIRMATION`, `PERSISTING_DEFICIENCY`, `VERIFIED_RESOLVED`
- `comparison_summary` (TEXT)
- `remaining_issues` (TEXT)
- `created_at` (TIMESTAMPTZ)

---

## 4. Operational Dossiers vs Synthetic Demonstration Records

It is critical for engineering teams and auditors to distinguish between test datasets:

| Characteristic | Synthetic Demonstration Dossier | Live Operational Test Dossier |
| :--- | :--- | :--- |
| **Application Reference** | `APP-2026-1042` | `APP-2026-601169` (and fresh user submissions) |
| **Origin** | Seeded automatically by Flyway migration [V8](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/resources/db/migration/V8__seed_synthetic_scrutiny_demo_data.sql). | Created through live frontend/API document upload workflows. |
| **Institution** | `INST-1025` (`ABC Institute of Technology`) | Real or test institutions created in PostgreSQL. |
| **Document State** | Static pre-seeded rows in `application_documents` and `scrutiny_assessments`. | Dynamic multipart files ingested into MinIO and registered in PostgreSQL. |
| **Assessment Cycles** | Pre-populated with Cycle 1 and re-scrutiny demonstration diffs. | Generates real cycle counts (Cycle 1 -> Cycle 2) upon officer execution. |
| **Purpose** | Immediate demonstration of UI, charts, and diff visualization upon first startup. | End-to-end verification of actual PDFBox parsing, Tess4J OCR, and file hash comparison. |
