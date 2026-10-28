# SAGE — Complete Automated Scrutiny & Re-Scrutiny Implementation Report

**Project:** SAGE — Institutional Approval & Compliance Platform  
**Smart India Hackathon 2026 Problem Statement:** SIH26262 — Integrated AICTE Approval Workflow Automation, Compliance Monitoring, and Alert Management System  
**Implementation Scope:** Complete Automated Scrutiny, Document OCR/Extraction, Deficiency Management, and Re-Scrutiny Engine  
**Execution Date:** 2026-10-27  

---

## Executive Summary

The SAGE Automated Scrutiny & Re-Scrutiny System has been fully implemented, tested, and integrated into the existing Spring Boot + React + PostgreSQL + MinIO architecture. 

The system automates the preliminary examination of uploaded regulatory documents (PDFs and scanned certificates) using **Apache PDFBox** for searchable digital extraction and **Tesseract OCR (via Tess4J)** for scanned imagery. Extracted data (dates, certificate numbers, entity names, PIN codes, PAN) are parsed by a rule engine that validates document completeness, certificate expiry, and cross-document entity consistency against the AICTE Approval Process Handbook (APH 2024–2027) norms.

Human regulatory authority remains sovereign: automated scrutiny outcomes are preliminary findings that require authorized Scrutiny Officer confirmation before formal deficiency notices are generated. Submissions of rectifications preserve full version history in MinIO, feed into an automated side-by-side re-scrutiny comparative engine, and enforce safety guards that block workflow advancement if outstanding blocking deficiencies remain unresolved.

---

## 1. Existing Scrutiny Functionality Discovered

Before starting implementation, a comprehensive audit of the extracted repository identified:

- **Application Lifecycle & Workflow Engine:** State machine in `WorkflowService.java` supporting `RETURN_FOR_CORRECTION`, `SUBMIT_CORRECTIONS`, and `COMPLETE_SCRUTINY`.
- **Pre-existing Flaw Discovered & Resolved:** `WorkflowService.java` line 116 previously wiped `application.setDeficienciesJson(null)` upon `SUBMIT_CORRECTIONS`, erasing all historical deficiency notices and observations. This has been corrected.
- **Application Catalogue:** `ApplicationTypeCatalogue.java` defining required statutory document categories for Extension of Approval (EoA) and New Institution Approval.
- **Document Management:** Initial `ApplicationDocument` entities and `MinioStorageService.java` uploading files with version metadata.
- **UI Structure:** Baseline `ScrutinyOfficerDashboard.jsx` and `ApplicationDetailPage.jsx` featuring static checklists without automated OCR or field validation.

---

## 2. Newly Implemented Features

1. **Document Text & Image Processing Pipeline:**
   - Multi-mode extraction prioritizing **Apache PDFBox** for searchable text and falling back to scanned page rendering via `PDFRenderer` into **Tess4J (Tesseract OCR)**.
   - Externalized OCR datapath detection with graceful fallback to `NEEDS_MANUAL_REVIEW` when native binaries are absent, preventing false PASS results.

2. **Structured Regulatory Field Extraction:**
   - Context-aware regex and NLP heuristic extraction for issue dates, validity/expiry dates (supporting ISO, DD/MM/YYYY, text month formats), certificate/NOC registration numbers, PAN identifiers, PIN codes, and institution names.

3. **Application-Specific Rule Validation Engine:**
   - **Document Completeness:** Verifies mandatory documents under APH catalogue clauses; flags missing files or empty files (< 100 bytes) as `BLOCKING` deficiencies.
   - **Certificate Validity:** Compares extracted expiry dates against statutory evaluation dates; flags expired certificates with citation of applicable APH clauses.
   - **Cross-Document Consistency:** Normalizes and compares extracted institution names and campus addresses across documents (e.g. Fire NOC vs. Affidavit 2 vs. Application Dossier).

4. **Human-in-the-Loop Deficiency Management:**
   - Officer reviews automated findings (`CONFIRMED`, `DISMISSED`, `MANUAL_VERIFIED`).
   - Batch issuance of formal deficiency notices with custom observations and statutory deadlines.

5. **Multi-Version Document Storage & Institution Responses:**
   - Dedicated deficiency upload endpoint preserving original version 1 and versioning revisions (v2, v3).
   - Immutable audit trail linking revisions to specific deficiency records.

6. **Automated Re-Scrutiny Engine:**
   - Automatically triggered upon institution rectification submission.
   - Performs side-by-side diff comparison between original extracted values and newly extracted values.
   - Evaluates whether renewal dates satisfy validity thresholds (`RECTIFIED_PENDING_CONFIRMATION` or `DEFICIENCY_PERSISTS`).

7. **Completion Safeguard:**
   - Strictly enforces that `COMPLETE_SCRUTINY` cannot advance to EVC or Council approval while blocking deficiencies remain open.

---

## 3. Backend Files Created or Modified

### Created Files
| File Path | Description |
|---|---|
| `src/main/resources/db/migration/V7__automated_scrutiny_and_rescrutiny.sql` | Schema migration for assessments, extractions, findings, deficiencies, and comparisons. |
| `src/main/resources/db/migration/V8__seed_synthetic_scrutiny_demo_data.sql` | Synthetic EoA demonstration dossier pre-seeded for SIH presentations. |
| `src/main/java/.../scrutiny/entity/ScrutinyAssessment.java` | JPA entity tracking scrutiny attempts, cycle numbers, and summary metrics. |
| `src/main/java/.../scrutiny/entity/ScrutinyDocumentExtraction.java` | JPA entity tracking OCR text snippets, structured fields JSON, and confidence scores. |
| `src/main/java/.../scrutiny/entity/ScrutinyFinding.java` | JPA entity tracking individual rule evaluations and officer confirmation status. |
| `src/main/java/.../scrutiny/entity/ScrutinyDeficiency.java` | JPA entity managing formal deficiency notices, due dates, explanations, and revisions. |
| `src/main/java/.../scrutiny/entity/ScrutinyRescrutinyComparison.java` | JPA entity capturing side-by-side diffs between original and corrected evidence. |
| `src/main/java/.../scrutiny/repository/*.java` (5 repositories) | Spring Data JPA repositories with query helpers for lifecycle metrics and safeguards. |
| `src/main/java/.../scrutiny/ocr/ExtractedDocumentData.java` | Data holder with type-safe getters for parsed dates and structured fields. |
| `src/main/java/.../scrutiny/ocr/TesseractOcrService.java` | Tess4J wrapper with system datapath auto-detection and safe fallback. |
| `src/main/java/.../scrutiny/ocr/DocumentTextExtractionService.java` | PDFBox text extraction and scanned page image rendering. |
| `src/main/java/.../scrutiny/ocr/DocumentFieldExtractionService.java` | Heuristic and regex parser for dates, certificate numbers, and entity names. |
| `src/main/java/.../scrutiny/validation/ScrutinyValidationService.java` | Statutory compliance rule evaluation engine. |
| `src/main/java/.../scrutiny/service/ScrutinyService.java` | Orchestrator for scrutiny runs, finding reviews, deficiency notices, and upload handling. |
| `src/main/java/.../scrutiny/service/ReScrutinyService.java` | Re-scrutiny comparison engine generating automated diffs. |
| `src/main/java/.../scrutiny/controller/ScrutinyController.java` | Spring Web REST controller exposing 10 scrutiny endpoints. |
| `src/main/java/.../scrutiny/dto/*.java` (9 DTOs) | Request and response records for API payload serialization. |
| `src/test/java/.../ScrutinyAutomationAndRescrutinyUnitTest.java` | Comprehensive unit tests covering OCR extraction, validation rules, diffs, and guards. |

### Modified Files
| File Path | Modification Summary |
|---|---|
| `pom.xml` | Added Apache PDFBox 3.0.4 and Tess4J 5.14.0 dependencies; configured surefire test runner. |
| `src/main/java/.../workflow/WorkflowService.java` | Injected `ScrutinyDeficiencyRepository`; fixed `SUBMIT_CORRECTIONS` to prevent wiping `deficienciesJson`; added completion safeguard in `COMPLETE_SCRUTINY`. |

---

## 4. Frontend Files Created or Modified

### Created Files
| File Path | Description |
|---|---|
| `frontend/src/components/scrutiny/AutomatedScrutinyPanel.jsx` | Full-featured UI panel featuring 4 tabs: Preliminary Findings, OCR & Extracted Evidence, Deficiencies & Formal Notice, and Re-Scrutiny Diffs. |

### Modified Files
| File Path | Modification Summary |
|---|---|
| `frontend/src/context/DataContext.jsx` | Added 10 API wrapper functions for scrutiny runs, report fetching, deficiency management, and document uploads. |
| `frontend/src/pages/ApplicationDetailPage.jsx` | Added dedicated `Automated Scrutiny (AICTE)` tab, embedded `AutomatedScrutinyPanel` in `Scrutiny` tab, and wired blocking safeguards. |
| `frontend/src/pages/dashboards/ScrutinyOfficerDashboard.jsx` | Added `Automated Scrutiny` column displaying live OCR and deficiency status badges. |

---

## 5. Database Migrations Added

1. **`V7__automated_scrutiny_and_rescrutiny.sql`**:
   - `scrutiny_assessments`: Tracks assessment references, cycles, rule versions, totals, passes, and deficiencies.
   - `scrutiny_document_extractions`: Tracks extraction methods, OCR statuses, confidence scores, and structured JSON fields.
   - `scrutiny_findings`: Persists rule code, severity, expected condition, actual extracted value, and officer confirmation.
   - `scrutiny_deficiencies`: Tracks formal deficiency references, deadlines, institution explanations, and linked corrected document IDs.
   - `scrutiny_rescrutiny_comparisons`: Persists original vs. corrected extracted values and comparison outcome summaries.
   - Includes indexes on `application_id`, `status`, and `requirement_code`.

2. **`V8__seed_synthetic_scrutiny_demo_data.sql`**:
   - Seeds demonstration application `APP-2026-1042` with 5 statutory documents, an expired Fire Safety Certificate (v1), an inconsistent address in Affidavit 2 (v1), a renewed Fire Safety Certificate (v2), formal deficiency notice `DEF-2026-0001`, and re-scrutiny comparative outcome.

---

## 6. Tesseract OCR Setup & Operational Configuration

The OCR engine is powered by **Tess4J** wrapping Google's Tesseract engine.

### Windows Development Requirements
- **Local Tesseract Installation (Optional for production / full OCR):**
  - Download and install Tesseract 5.x for Windows (e.g. from UB-Mannheim: `https://github.com/UB-Mannheim/tesseract/wiki`).
  - Set environment variable: `TESSDATA_PREFIX=C:\Program Files\Tesseract-OCR\tessdata`
- **Graceful Fallback Mode:**
  - If Tesseract native binaries are not installed on the host machine, `TesseractOcrService.java` automatically detects the absence, captures the event safely, and returns `OCR_UNAVAILABLE_FALLBACK` / `NEEDS_MANUAL_REVIEW`.
  - Normal application workflows continue uninterrupted without crashes or false PASS results.

---

## 7. OCR & Document Processing Workflow

```
[ Uploaded Document in MinIO ]
              │
              ▼
[ DocumentTextExtractionService ]
              │
      Is Searchable PDF?
      ├── YES ──► Extract digital text via Apache PDFBox (High Speed)
      └── NO  ──► Render PDF pages to images via PDFRenderer
                        │
                        ▼
                  [ TesseractOcrService (Tess4J) ]
                        │
                        ▼
           [ Extracted Raw Text Stream ]
                        │
                        ▼
      [ DocumentFieldExtractionService ]
   (Extracts: Issue/Expiry Dates, Cert Numbers, PAN, Address)
                        │
                        ▼
      [ Persist ScrutinyDocumentExtraction ]
```

---

## 8. Automated Validation Rules Implemented

| Rule Code | Category | Statutory Reference | Expected Condition | Action on Failure |
|---|---|---|---|---|
| `RULE_DOC_MANDATORY_MISSING_*` | Completeness | APH Mandatory Checklist | Document present in application dossier | `BLOCKING` Potential Deficiency |
| `RULE_DOC_EMPTY_*` | Completeness | APH Document Specification | File size >= 100 bytes | `BLOCKING` Potential Deficiency |
| `RULE_CERT_VALIDITY_EXPIRED_*` | Validity | APH Chapter II Clause 2.14 | Certificate expiry date >= assessment date | `BLOCKING` Potential Deficiency |
| `RULE_CONSISTENCY_INST_NAME_*` | Consistency | APH Annexure 2 Norms | Extracted institution name matches application name | `WARNING` Potential Deficiency |
| `RULE_CONSISTENCY_ADDRESS_*` | Consistency | APH Annexure 2 Format 2 | Extracted address matches registered location | `BLOCKING` Potential Deficiency |
| `RULE_OCR_NEEDS_MANUAL_REVIEW_*`| Readability | General Scrutiny Protocol | High-confidence automated extraction | `WARNING` Needs Manual Review |

---

## 9. Deficiency and Re-Scrutiny Workflow

```
[ Automated Scrutiny Completes ] ──► [ Preliminary Findings Displayed ]
                                                   │
                                                   ▼
                                     [ Officer Reviews Findings ]
                                    (Confirm / Dismiss with Reason)
                                                   │
                                                   ▼
                                  [ Issue Formal Deficiency Notice ]
                                   (Deficiencies saved in PostgreSQL)
                                                   │
                                                   ▼
                                 [ Institution Uploads Rectifications ]
                               (New Version v2 in MinIO; preserves v1)
                                                   │
                                                   ▼
                                     [ Automated Re-Scrutiny ]
                               (Compares v1 vs v2 extracted values)
                                                   │
                                                   ▼
                                    [ Re-Scrutiny Diff Report ]
                                 (Displays side-by-side comparisons)
                                                   │
                                                   ▼
                                     [ Officer Verifies & Closes ]
                                                   │
                                                   ▼
                                  [ Complete Scrutiny Safeguard ]
                             (Blocks if blocking deficiencies remain)
```

---

## 10. New REST API Endpoints

All endpoints are hosted under `/api/v1/applications/{applicationId}`:

| Method | Endpoint | Authorization | Description |
|---|---|---|---|
| `POST` | `/scrutiny/run` | Officer / Admin | Triggers automated scrutiny assessment across dossier |
| `GET` | `/scrutiny/report` | Officer / Inst / Admin | Retrieves latest preliminary scrutiny report & findings |
| `GET` | `/scrutiny/assessments` | Officer / Inst / Admin | Retrieves historical scrutiny assessment cycles |
| `POST` | `/scrutiny/findings/{id}/review` | Officer / Admin | Records officer decision (`CONFIRMED`/`DISMISSED`) on finding |
| `GET` | `/deficiencies` | Officer / Inst / Admin | Retrieves formal deficiencies issued for the application |
| `POST` | `/deficiencies/issue` | Officer / Admin | Issues formal deficiency notice for confirmed findings |
| `POST` | `/deficiencies/{id}/documents` | Institution / Admin | Uploads corrected evidence revision (v2+) to MinIO |
| `POST` | `/deficiencies/submit-responses` | Institution / Admin | Submits institution explanations and triggers re-scrutiny |
| `POST` | `/deficiencies/{id}/review` | Officer / Admin | Records officer verification (`VERIFIED_RESOLVED`/`REJECTED`) |
| `GET` | `/scrutiny/comparison` | Officer / Inst / Admin | Retrieves side-by-side original vs. corrected diffs |

---

## 11. Instructions to Run the Complete Project

### Prerequisites
- Java 21+ (Java 25 verified compatible)
- Maven 3.9+ (or included `mvnw.cmd`)
- Node.js 18+ and npm
- PostgreSQL running on port 5432 (default DB `aicte_platform_db`)
- MinIO running on port 9000

### Step 1: Start PostgreSQL and MinIO
Ensure containers or local services are active:
```powershell
# Using docker compose in project root
docker compose up -d
```

### Step 2: Run Backend (Spring Boot)
From the project root:
```powershell
.\mvnw.cmd spring-boot:run
```
The Flyway migrations `V1` through `V8` will execute automatically, creating all scrutiny tables and seeding the synthetic demonstration dossier (`APP-2026-1042`).

### Step 3: Run Frontend (React + Vite)
In a separate terminal:
```powershell
cd frontend
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 12. Testing Results

### Backend Automated Unit Tests
Executed via Maven Surefire:
```
[INFO] Running in.gov.aicte.platform.ScrutinyAutomationAndRescrutinyUnitTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.109 s
[INFO] BUILD SUCCESS
```
- **Test 1:** `testFieldExtractionFromRawText` — Verified regex/NLP extraction of dates, certificate number, PAN, PIN, and institution name.
- **Test 2:** `testValidationFlagsExpiredCertificate` — Verified expired certificate triggers `POTENTIAL_DEFICIENCY` with `BLOCKING` severity.
- **Test 3:** `testValidationFlagsInstitutionNameDiscrepancy` — Verified cross-document entity mismatch is detected.
- **Test 4:** `testValidationPassesValidCertificate` — Verified valid unexpired certificate generates `PASS`.
- **Test 5:** `testSubmitCorrectionsPreservesDeficienciesJson` — Verified `SUBMIT_CORRECTIONS` preserves deficiencies and historical observations.
- **Test 6:** `testCompleteScrutinyBlockedByUnresolvedBlockingDeficiencies` — Verified `COMPLETE_SCRUTINY` throws `IllegalStateException` when blocking deficiencies exist.
- **Test 7:** `testCompleteScrutinySucceedsWhenNoBlockingDeficiencies` — Verified `COMPLETE_SCRUTINY` cleanly advances stage when all deficiencies are resolved.

### Frontend Production Build & Regression Tests
- **Vite Production Build:** Transformed 2553 modules in 900ms without syntax or bundling errors.
- **Frontend Regression Suite:** All 6 EVC lifecycle tests and all 5 Draft Promotion tests passed with 100% success.

---

## 13. Limitations and Future Enhancements

1. **Native Tesseract Host Dependency:** For production deployments on cloud Linux servers, ensure the `tesseract-ocr` and `tessdata` packages are installed in the container image (`apt-get install -y tesseract-ocr`).
2. **Advanced Machine Learning / LayoutLM:** While heuristic extraction handles standard AICTE formats, future iterations can introduce open-weights LayoutLM models for multi-lingual and non-standard layout parsing.
3. **Geo-Spatial Coordinate Verification:** Future enhancements can link the extracted satellite coordinates with the Survey of India / Bhuvan portal API for automated land boundary verification.

---
*Report certified by SAGE Full-Stack Development & Compliance Automation Team.*
