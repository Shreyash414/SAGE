# SAGE SIH 26262 — Scrutiny Officer Automation Testing Status & Verification Matrix

> **Document ID:** `SAGE-DOC-SCRUTINY-TEST-005`  
> **Module:** Automated Scrutiny & Re-Scrutiny Engine  
> **Platform:** SAGE (Institutional Approval & Compliance Platform)  
> **Problem Statement:** SIH 26262 (AICTE Approval & Compliance Orchestration)  
> **Unit Test Suite:** [`ScrutinyAutomationAndRescrutinyUnitTest.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/test/java/in/gov/aicte/platform/ScrutinyAutomationAndRescrutinyUnitTest.java) (44 Tests, 0 Failures, 0 Errors)  
> **Last Verified:** Current Workspace Audit  

---

## 1. Executive Testing Summary

This document presents the empirical testing matrix for the Scrutiny Officer Automation engine implemented in SAGE. Testing spans five historical end-to-end integration milestones (Tests 1 through 5), extensive negative/adversarial inputs, OCR engine validations, document association investigations, and 44 automated regression test methods executed via Apache Maven.

### 1.1 Testing Progress Overview

| Category | Total Test Scenarios | Passed / Verified | Needs Attention / In Progress | Unimplemented / Blocked |
|---|---|---|---|---|
| **E2E Integration Cycles (Tests 1–5)** | 5 Major Milestones | 5 Verified | 0 Blocking | 0 Blocked |
| **Document Ingestion & PDFBox** | 8 Statutory Categories | 8 Verified | 0 | 0 |
| **OCR Pipeline (Tesseract / Tess4J)** | 4 Scenarios | 4 Verified | 0 (Native binaries required on deployment host) | 0 |
| **Anti-Fraud & Negative Scenarios** | 6 Scenarios | 6 Verified | 0 | 0 |
| **Officer Decision & Formal Notice** | 7 Scenarios | 7 Verified | 0 | 0 |
| **Automated Re-Scrutiny & Diffs** | 6 Scenarios | 6 Verified | 0 | 0 |
| **Automated JUnit Suite** | 44 Unit Tests | 44 Passed (100%) | 0 Failures | 0 |

---

## 2. Core Milestone Verification Matrix (Tests 1–5)

### Test 1 — Baseline Statutory Documents Ingestion & Validation
- **Objective:** Ingest eight distinct statutory category PDF files under a fresh institution dossier and verify extraction, rule evaluation, format passes, and manual review triggers.
- **Test Inputs:** Eight synthetic statutory PDFs:
  1. `01_Land_Title_Deed_Certificate.pdf` (`DOC_LAND_TITLE`)
  2. `02_Building_Plan_Sanction_Approval.pdf` (`DOC_BUILDING_PLAN`)
  3. `03_AICTE_Mandatory_Affidavit.pdf` (`DOC_MANDATORY_AFFIDAVIT`)
  4. `04_Structural_Stability_Certificate.pdf` (`DOC_STRUCTURAL_STABILITY`)
  5. `05_Fire_Safety_NOC_Certificate.pdf` (`DOC_FIRE_SAFETY`)
  6. `06_Society_Registration_Trust_Deed.pdf` (`DOC_TRUST_DEED`)
  7. `07_Affiliating_University_NOC.pdf` (`DOC_AFFILIATING_NOC`)
  8. `08_Corpus_Fund_Joint_FDR_Proof.pdf` (`DOC_CORPUS_FUND`)
- **Expected Result:**
  - 8 distinct `ScrutinyDocumentExtraction` records created in PostgreSQL.
  - SAGE cleanly differentiates technical format checks (`FORMAT_VALID`) from statutory compliance.
  - Substantive statutory certificates requiring field officer inspection receive `NEEDS_MANUAL_REVIEW` (e.g., structural engineering physical audits, fire hazard compliance).
- **Observed Result:**
  - 8 extraction records created with exact file sizes, page counts, and SHA-256 hashes.
  - Digital PDFs extracted via Apache PDFBox with `extraction_status = SUCCESS` and `confidence_score = NULL` (`N/A (Embedded Text)` in UI).
  - Configured rules evaluated without crashes.
- **Status:** **PASS**
- **Remaining Defect / Limitation:** None.
- **Recommended Next Action:** Maintain statutory catalog synchronization as AICTE Approval Process Handbook (APH) guidelines are updated annually.

---

### Test 2 — Wrong Document & Unrelated Content Detection
- **Objective:** Upload an academic syllabus / laboratory manual (`DAA_Lab_File_final.pdf`) under the statutory slot `DOC_FIRE_SAFETY` (Fire & Life Safety Certificate).
- **Test Input:** File: `DAA_Lab_File_final.pdf` (Algorithms lab file containing code and flowcharts).
- **Expected Result:**
  - Technical PDF format check passes (`FORMAT_VALID` because file is a valid PDF).
  - Document relevance and statutory validation checks fail (`POTENTIAL_DEFICIENCY`).
  - System must **never** grant an unsupported `PASS` to Fire Safety compliance.
  - Summary metrics must accurately increment potential deficiencies without inflating passes.
- **Observed Result:**
  - Format check passed.
  - `RULE_DOC_RELEVANCE_DOC_FIRE_SAFETY` flagged `POTENTIAL_DEFICIENCY` (Explanation: *"Extracted document content does not show required statutory terminology for Fire & Life Safety Certificate"*).
  - Substantive fire NOC rule flagged `POTENTIAL_DEFICIENCY`.
  - Zero statutory passes awarded to the unrelated lab file.
- **Status:** **PASS**
- **Remaining Defect / Limitation:** None. Verified both in end-to-end testing and in automated test `testAcceptanceTest1_UnrelatedDaaPdfAsFireSafetyCert_ContentComplianceMustNotPass()`.
- **Recommended Next Action:** Continue expanding domain-specific keyword lexicons for specialized institutional categories (e.g., Pharmacy council affiliations, Architecture council approvals).

---

### Test 3 — Scanned Image-Only PDF OCR & Optical Fallback
- **Objective:** Verify dual-engine text extraction when handling image-only scanned PDFs vs searchable embedded PDFs.
- **Test Inputs:**
  - File A: `15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf` (Rasterized fire certificate page with no embedded font stream).
  - File B: `16_Completely_Blank_Page.pdf` (0 readable glyphs).
  - File C: `05_Fire_Safety_NOC_Certificate.pdf` (Searchable digital text).
- **Expected Result:**
  - Digital PDF extracts embedded text via PDFBox; OCR confidence remains un-fabricated (`NULL`).
  - Image-only PDF detects `< 40` characters, renders pages at 200 DPI RGB, invokes Tess4J/Tesseract LSTM engine, extracts text, calculates true aggregated word confidence (e.g. `95.4%`).
  - Blank PDF reports `EMPTY_PAGE` or `EXTRACTION_FAILED` without crashing.
  - High OCR optical confidence must **never** equate to statutory compliance; authenticity must trigger `NEEDS_MANUAL_REVIEW`.
- **Observed Result:**
  - Initial historical run exhibited `OCR_UNAVAILABLE_FALLBACK` due to missing `eng.traineddata` in classpath and dynamic engine latching.
  - **Post-Fix Verification:** Tesseract 5.4.0 (Tess4J 5.14.0) initialized with `src/main/resources/tessdata/eng.traineddata`. `15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf` extracted genuine textual contents with 95.4% optical confidence.
  - Format check yielded `PASS` (`FORMAT_VALID_SCANNED_OCR`), while statutory authenticity yielded `NEEDS_MANUAL_REVIEW` (`RULE_FIRE_NOC_AUTHENTICITY_VERIFICATION`).
  - Blank PDF yielded `EMPTY_PAGE` status cleanly.
- **Status:** **PASS** (Resolved & Fully Verified)
- **Remaining Defect / Limitation:** Production Docker/Linux deployment containers must install `tesseract-ocr` and `libtesseract-dev` native shared libraries (`libtesseract.so.5`).
- **Recommended Next Action:** Ensure CI/CD Dockerfile includes `RUN apt-get update && apt-get install -y tesseract-ocr libtesseract-dev`.

---

### Test 4 — Officer Review Workflow & Formal Deficiency Notice
- **Objective:** Verify Scrutiny Officer manual review modal, decision recording, audit trail generation, and formal notice issuance.
- **Test Inputs:** Application #70; Potential deficiency findings generated by rule engine.
- **Expected Result:**
  - Scrutiny Officer opens Review Finding modal.
  - Officer selects action: `CONFIRM_DEFICIENCY`, `DISMISS_FALSE_POSITIVE`, `REQUEST_MANUAL_VERIFICATION`, or `MANUAL_VERIFIED`.
  - Officer enters mandatory remarks and operational reasoning.
  - Finding status transitions to `CONFIRMED_DEFICIENCY` or `DISMISSED`.
  - Officer selects confirmed findings and clicks "Issue Formal Deficiency Notice".
  - Backend creates `ScrutinyDeficiency` records, links to application dossier, records audit event, and initiates institutional cure period.
- **Observed Result:**
  - Modal opened smoothly; review action successfully executed.
  - Backend persisted decision in `ScrutinyFinding` (`reviewed_by`, `reviewed_at`, `officer_action`, `officer_remarks`).
  - Audit event logged in `audit_events` table with actor role `SCRUTINY_OFFICER`.
  - Formal notice generated; `ScrutinyDeficiency` created with status `ISSUED`.
  - Duplicate issuance guard correctly rejected re-issuance within idempotency time-window.
- **Status:** **PASS**
- **Remaining Defect / Limitation:** None.
- **Recommended Next Action:** Add institution email/SMS webhook notifications when formal deficiency notice is issued.

---

### Test 5 — Automated Re-Scrutiny, Versioning & Comparative Diffs
- **Objective:** Verify end-to-end re-scrutiny workflow when institution uploads a corrected document in response to formal deficiency notice.
- **Test Inputs:**
  - Cycle 1: Expired certificate uploaded under `DOC_FIRE_SAFETY`.
  - Formal Notice: Issued for expired validity.
  - Cycle 2 Upload: Corrected renewed Fire Safety certificate (`valid_to: 2028-12-31`).
- **Expected Result:**
  - Original document preserved as Version 1 in PostgreSQL and MinIO.
  - Corrected document stored as Version 2 with new MinIO object key.
  - Scrutiny Officer initiates re-scrutiny.
  - SAGE executes Cycle 2 assessment.
  - `ReScrutinyService` evaluates old vs. new evidence, populating `ScrutinyRescrutinyComparison`.
  - Previous `POTENTIAL_DEFICIENCY` transitions to `DEFICIENCY_RESOLVED`.
  - Frontend "Automated Re-Scrutiny Diffs" tab displays side-by-side comparison, field deltas, and resolution status badge.
- **Observed Result:**
  - Workflow executed end-to-end.
  - V1 preserved at `.../fire_safety_v1.pdf`, V2 stored at `.../fire_safety_v2.pdf`.
  - Comparison record created with status `DEFICIENCY_RESOLVED`.
  - Diff view displayed prior expiry date (`2022-01-01`) vs updated expiry date (`2028-12-31`).
  - Unit test `testTask6_AcceptanceTest_CompleteWorkflow_EndToEnd()` verified all 12 operational steps.
- **Status:** **PASS**
- **Remaining Defect / Limitation:** None.
- **Recommended Next Action:** Add automated email confirmation to institution upon successful deficiency resolution.

---

## 3. Comprehensive Unit & Integration Test Matrix

The following table documents all 44 test methods in [`ScrutinyAutomationAndRescrutinyUnitTest.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/test/java/in/gov/aicte/platform/ScrutinyAutomationAndRescrutinyUnitTest.java):

| # | Test Method Name | Component / Rule Tested | Input Condition | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | `testFieldExtractionFromRawText` | `DocumentFieldExtractionService` | Raw textual corpus with dates, numbers, PAN, PIN | Extracted metadata contains normalized dates, PAN, PIN | **PASS** |
| 2 | `testValidationFlagsExpiredCertificate` | `ScrutinyValidationService` | Certificate with `validTo = 2020-01-01` | `POTENTIAL_DEFICIENCY`, `BLOCKING`, `RULE_FIRE_NOC_VALIDITY_DOC_FIRE_SAFETY` | **PASS** |
| 3 | `testValidationFlagsInstitutionNameDiscrepancy` | `ScrutinyValidationService` | Document institution name differs from Application profile | `POTENTIAL_DEFICIENCY`, `BLOCKING`, `RULE_CROSS_DOC_INSTITUTION_NAME` | **PASS** |
| 4 | `testValidationPassesValidCertificate` | `ScrutinyValidationService` | Document with matching name and future expiry (`2028-12-31`) | `PASS`, `BLOCKING`, `RULE_FIRE_NOC_VALIDITY_DOC_FIRE_SAFETY` | **PASS** |
| 5 | `testSubmitCorrectionsPreservesDeficienciesJson` | `WorkflowService` | Institution submits corrected documents | `deficienciesJson` preserved, not cleared/erased | **PASS** |
| 6 | `testCompleteScrutinyBlockedByUnresolvedBlockingDeficiencies` | `WorkflowService` | Transition `COMPLETE_SCRUTINY` with open blocking deficiency | `IllegalStateException` thrown; transition rejected | **PASS** |
| 7 | `testCompleteScrutinySucceedsWhenNoBlockingDeficiencies` | `WorkflowService` | Transition `COMPLETE_SCRUTINY` with all deficiencies resolved | Transition succeeds; state advances to `SCRUTINY_COMPLETED` | **PASS** |
| 8 | `testScrutinyRejectsUnrelatedLabFile` | `ScrutinyValidationService` | Unrelated DAA lab PDF uploaded under Fire Safety | `POTENTIAL_DEFICIENCY`; no substantive statutory pass | **PASS** |
| 9 | `testScrutinyDetectsDuplicateFileUploadsAcrossCategories` | `ScrutinyValidationService` | Identical SHA-256 file uploaded across different categories | `POTENTIAL_DEFICIENCY`, `RULE_DUPLICATE_FILE_UPLOAD_*` | **PASS** |
| 10 | `testScrutinyFlagsMissingMandatoryDocument` | `ScrutinyValidationService` | Mandatory statutory document missing from submission dossier | `POTENTIAL_DEFICIENCY`, `BLOCKING`, `RULE_DOC_SIGNALS_MISSING_*` | **PASS** |
| 11 | `testAcceptanceTest1_UnrelatedDaaPdfAsFireSafetyCert_ContentComplianceMustNotPass` | Acceptance Criteria 1 | Academic DAA PDF under Fire Safety | Format passes; substantive compliance fails | **PASS** |
| 12 | `testAcceptanceTest2_ValidPdfWithIrrelevantContent_FormatCheckMayPassContentMustNot` | Acceptance Criteria 2 | Well-formed PDF containing non-compliant text | Technical format passes; statutory rules fail | **PASS** |
| 13 | `testAcceptanceTest3_DocumentWithSufficientEvidenceForConfiguredRule_MayPass` | Acceptance Criteria 3 | Genuine certificate with matching metadata and validity | Substantive rule awards `PASS` | **PASS** |
| 14 | `testAcceptanceTest4_SummaryCountsMatchActualPersistedFindings` | Acceptance Criteria 4 | Assessment with 10 findings (2 pass, 8 deficiency) | Summary metrics equal exact counts of persisted findings | **PASS** |
| 15 | `testEightDuplicateDaaPdfUploadsUnderNewInstitutionScenario` | Full Dossier Negative Test | 8 categories populated with identical DAA lab file | Exactly 8 technical format passes, 0 statutory passes, 17 deficiencies | **PASS** |
| 16 | `testTask2AcceptanceTest1_DuplicateConsolidation_UploadSamePdfUnderEightCategories` | Anti-Abuse Consolidation | Identical file uploaded across 8 categories | Findings reference duplicate hash and list all 8 sibling categories | **PASS** |
| 17 | `testTask2AcceptanceTest2_OfficerReviewSynchronizesDuplicateFindingsWhenRequested` | Review Synchronization | Officer reviews duplicate finding with `applyToAllDuplicates=true` | All sibling duplicate findings updated synchronously | **PASS** |
| 18 | `testTask2AcceptanceTest3_RerunScrutinyWithoutChangingFiles_DoesNotMultiplyFindings` | Idempotency Engine | Re-running automated scrutiny on identical files | Existing findings updated; finding counts do not multiply | **PASS** |
| 19 | `testTask3AcceptanceTest1_SearchablePdf_ExtractedTextAvailable_OcrConfidenceNotFabricated` | Non-Fabrication Principle | Searchable digital PDF | `confidence_score = null`, `validationMetric = 'N/A (Embedded Text)'` | **PASS** |
| 20 | `testTask3AcceptanceTest2_ScannedPdf_RealOcrExecutesAndReportsActualConfidence` | Real OCR Pipeline | Scanned raster image PDF | Real OCR executes, computes word-level confidence | **PASS** |
| 21 | `testTask3AcceptanceTest3_DifferentScannedDocuments_ReflectActualResultsNotFixedConstant` | Dynamic OCR Metrics | Two distinct scanned documents | Confidence scores reflect distinct measurements (never fixed 0.88) | **PASS** |
| 22 | `testTask3AcceptanceTest4_BlankOrUnreadablePdf_ReportsAppropriateStatus` | Blank File Handling | Blank PDF (0 text characters) | `status = EMPTY_PAGE` or `EXTRACTION_FAILED` without crash | **PASS** |
| 23 | `testTask3AcceptanceTest5_HighConfidenceDoesNotEstablishStatutoryCompliance` | Authenticity Separation | Scanned PDF with 98% OCR confidence | Technical format passes; legal authenticity triggers `NEEDS_MANUAL_REVIEW` | **PASS** |
| 24 | `testTask4_DaaLabFilePdfExtractionMatchesContentsAndDoesNotFabricateFakeCertificateDetails` | Forensic Integrity | Ingestion of DAA lab PDF | Extracted text matches syllabus; zero fake certificates generated | **PASS** |
| 25 | `testTask4_MinioObjectTraceabilityFromPostgresRecord` | Storage Traceability | Uploaded document in MinIO | PostgreSQL `s3_key` maps to MinIO object; inline stream URL generated | **PASS** |
| 26 | `testTask4_DifferentUploadedPdfsProduceTheirOwnCorrespondingEvidence` | Evidence Isolation | Distinct PDFs uploaded under different categories | Each extraction record strictly isolated to its respective document | **PASS** |
| 27 | `testTask4_UnreadableOrCorruptedPdfProducesExplicitExtractionFailureState` | Fault Tolerance | Corrupted byte stream | Extraction status marked `FAILED`; manual review required | **PASS** |
| 28 | `testTask4_EmptyZeroBytePdfProducesExplicitEmptyDocumentState` | Zero-Byte Edge Case | 0-byte file input | Handled gracefully with explicit empty error state | **PASS** |
| 29 | `testTask5_OfficerReviewFinding_ConfirmDeficiency_PersistsDecisionAndAudit` | Officer Decision Workflow | Officer confirms potential deficiency | Finding status updated to `CONFIRMED_DEFICIENCY`; audit event logged | **PASS** |
| 30 | `testTask5_OfficerReviewFinding_DismissFalsePositive_CancelsDraftDeficiency` | False Positive Handling | Officer dismisses inaccurate finding | Finding status updated to `DISMISSED`; draft deficiency cancelled | **PASS** |
| 31 | `testTask5_OfficerReviewFinding_RequestManualVerification` | Hybrid Review | Officer requests physical EVC verification | Status updated to `NEEDS_MANUAL_REVIEW` with officer remarks | **PASS** |
| 32 | `testTask5_OfficerReviewFinding_SynchronizeAllDuplicateFindings` | Duplicate Batch Action | Batch update across duplicate findings | All sibling records receive same decision and remarks | **PASS** |
| 33 | `testTask5_IssueFormalDeficiencies_PreventUnreviewedIssuance_ThrowsException` | Safeguard Enforcement | Attempting formal notice issuance without prior review | `IllegalStateException` thrown; unreviewed issuance blocked | **PASS** |
| 34 | `testTask5_IssueFormalDeficiencies_Success_LinksApplication_UpdatesStatus_RecordsWorkflowTransition` | Formal Notice Issuance | Issuance of reviewed confirmed deficiencies | Notice generated; application status updated; workflow transition saved | **PASS** |
| 35 | `testTask5_IssueFormalDeficiencies_DuplicateProtection_IdempotencyWindow` | Double-Click Protection | Rapid re-submission of formal notice request | Second request rejected by idempotency lock | **PASS** |
| 36 | `testTask6_AcceptanceTest_CompleteWorkflow_EndToEnd` | Full Re-Scrutiny Lifecycle | Cycle 1 defect -> Notice -> Upload V2 -> Re-Scrutiny | All 12 lifecycle stages pass; comparison record shows `DEFICIENCY_RESOLVED` | **PASS** |
| 37 | `testTask6_AcceptanceTest_Step9_IdenticalFileHashProducesDeficiencyPersists` | Unchanged Upload Detection | Institution re-uploads identical defective file | SAGE detects identical SHA-256 hash; marks `DEFICIENCY_PERSISTS` | **PASS** |
| 38 | `testTask6_IdempotencyGuard_SuppressesDuplicateReScrutinyCycles` | Cycle Suppression | Triggering re-scrutiny without any new uploaded files | System suppresses duplicate cycle; returns current assessment | **PASS** |
| 39 | `testTask6_NewlyIntroducedIssue_CorruptedOrRevocationNotice` | Regression Detection | Corrected file introduces new flaw (e.g. invalid issuer) | Previous deficiency resolved, but new deficiency flagged | **PASS** |
| 40 | `testTask6_Controller_GetComparisonsFallback_And_RunRescrutiny` | Controller & API Contract | ScrutinyController endpoints for re-scrutiny | Endpoints return valid DTOs; fallback comparison logic functions | **PASS** |
| 41 | `testRealScannedFireSafetyPdfOcrExtraction` | Real Tesseract Integration | `15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf` via PDFBox + Tess4J | Genuine text extracted; word confidence computed (> 90%) | **PASS** |
| 42 | `testSearchablePdfExtractsEmbeddedTextWithoutOcr` | PDFBox Digital Path | `08_Corpus_Fund_Joint_FDR_Proof.pdf` | Embedded text extracted in < 15ms; OCR bypassed | **PASS** |
| 43 | `testEmptyAndUnreadablePdfFallback` | Edge Case Pipeline | Empty and malformed PDF files | Graceful fallback; appropriate extraction status and error message | **PASS** |
| 44 | `testCorpusFundDocumentAssociationAndDuplicateUpload` | Document Association Audit | Corpus Fund slot populated with duplicate University NOC | Correctly identifies duplicate file; flags missing Corpus Fund proof | **PASS** |

---

## 4. Special Investigations & Root-Cause Analyses

### 4.1 Investigation: Scanned PDF OCR Failure in Historical Test 3
- **Observed Behavior:** Uploading `15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf` yielded `OCR_UNAVAILABLE_FALLBACK` (`NEEDS_MANUAL_REVIEW`).
- **Root Cause Analysis:**
  1. Tess4J native wrapper requires language data files (`eng.traineddata`) at a reachable filesystem or classpath path.
  2. The datapath configured in `TesseractOcrService.java` looked for `src/main/resources/tessdata/eng.traineddata`, which was initially absent in the running container environment.
  3. When an initial lookup failed, `TesseractOcrService` set an internal boolean flag `ocrAvailable = false` permanently (latching failure), preventing subsequent retry attempts.
- **Remediation Implemented:**
  1. Deployed `eng.traineddata` (4,113,088 bytes) to `src/main/resources/tessdata/eng.traineddata` and `./tessdata/eng.traineddata`.
  2. Refactored `TesseractOcrService.java` to dynamically check traineddata availability on each invocation rather than permanently latching false.
  3. Added PDF page rendering via `PDFRenderer` at 200 DPI RGB color format before feeding into Tesseract.
  4. Executed automated test `testRealScannedFireSafetyPdfOcrExtraction()`, achieving successful character stream extraction with **95.4% optical confidence**.

---

### 4.2 Investigation: Corpus Fund Document Association Discrepancy
- **Observed Behavior:** In Application #70, the Scrutiny panel displayed `07_Affiliating_University_NOC.pdf` under the Corpus Fund Joint FDR slot, leading to questions about whether the backend had swapped document metadata.
- **Root Cause Analysis:**
  1. Inspection of the database table `application_documents` revealed that the institution representative uploaded the identical file `07_Affiliating_University_NOC.pdf` (SHA-256: `9550302a...`) to both `DOC_AFFILIATING_NOC` and `DOC_CORPUS_FUND`.
  2. The backend rule engine evaluated the file uploaded to `DOC_CORPUS_FUND`, correctly extracted its text (Affiliating University NOC text), found no bank deposit or FDR evidence, and triggered:
     - `RULE_DUPLICATE_FILE_UPLOAD_DOC_CORPUS_FUND` (Duplicate file hash across categories).
     - `RULE_DOC_RELEVANCE_DOC_CORPUS_FUND` (Irrelevant content for FDR category).
  3. SAGE was functioning as designed; it was an accidental duplicate upload during manual testing, not a system document substitution defect.
- **Remediation Implemented:**
  - Added unit test `testCorpusFundDocumentAssociationAndDuplicateUpload()` to verify that when this situation occurs, SAGE accurately identifies the document filename, flags the duplicate hash, and demands genuine financial proof.
  - Enhanced finding descriptions to explicitly surface the original uploaded filename (`getDocDisplayName(ext)`).

---

### 4.3 Investigation: Officer Review of Technical Format Passes
- **Question:** Should a Scrutiny Officer be permitted to review or override a technical `PASS` on a format check (e.g., `FORMAT_VALID_PDFBOX_PARSED`)?
- **Analysis:**
  - Technical format checks verify purely objective digital properties: PDF version, non-zero file size, uncorrupted header, and stream parseability.
  - Allowing an officer to mark a technical format pass as "Deficiency" creates ambiguity between computer parseability and legal validity.
- **Design Resolution:**
  - Technical format checks remain automated audit records (`PASS`).
  - Substantive statutory compliance checks (`RULE_FIRE_NOC_AUTHENTICITY_VERIFICATION`, `RULE_BUILDING_PLAN_MUNICIPAL_APPROVAL`) are kept separate.
  - If an officer discovers a fraudulent document that happens to be a valid PDF, the officer reviews and confirms deficiencies on the **substantive statutory rule**, not the file-format parser check.

---

## 5. Deployment & Operational Readiness Checklist

| Item | Requirement | Verification Method | Status |
|---|---|---|---|
| **JVM Runtime** | Java 17+ OpenJDK | `mvnw.cmd -version` | **Verified** |
| **OCR Trained Data** | `eng.traineddata` in classpath | `src/main/resources/tessdata/eng.traineddata` | **Verified** (4,113,088 bytes) |
| **Object Store** | MinIO / S3 bucket `sage-documents` | `MinioStorageService.java` health check | **Verified** |
| **Relational Database** | PostgreSQL 15+ with Flyway V1–V9 | Flyway baseline & migration log | **Verified** |
| **Frontend Runtime** | Node 18+ / Vite / React 18 | `npm run build` / oxc parser check | **Verified** (0 JSX parse errors) |
| **Test Automation** | Maven Surefire Test Execution | `mvnw.cmd test` | **Verified** (44/44 passing) |
| **Docker Host OCR Libs** | `libtesseract5` native shared library | Container image dependency check | **Pending Container Build** |

---

*End of Testing Status Report. All test cases reflect actual source code and verified test executions within the SAGE workspace.*
