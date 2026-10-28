# SAGE SIH 26262 — Scrutiny Officer Automation Presentation Notes & Demo Guide

> **Document ID:** `SAGE-DOC-SCRUTINY-DEMO-006`  
> **Module:** Automated Scrutiny & Re-Scrutiny Engine  
> **Platform:** SAGE (Institutional Approval & Compliance Platform)  
> **Problem Statement:** SIH 26262 (AICTE Approval & Compliance Orchestration)  
> **Target Audience:** SIH Evaluators, Ministry Judges, Mentors, and Project Teammates  

---

## 1. The 2-Minute Elevator Pitch (High-Impact Summary)

> **"Respected Judges and Teammates:**
>
> In the traditional AICTE approval process, scrutiny officers must manually inspect thousands of pages across dozens of statutory document categories for every single institution—checking dates, verifying trust deeds, calculating land contiguity, and reading municipal sanction orders. This manual bottleneck causes multi-month approval delays, human fatigue, and creates risks of fraudulent document re-use.
>
> In **SAGE**, I have engineered the **Automated Scrutiny & Re-Scrutiny Engine** to completely modernize this workflow into a transparent, hybrid human-in-the-loop system:
>
> 1. **Zero-Latency Ingestion & Dual-Engine Extraction:** When an institution uploads compliance dossiers, SAGE automatically routes digital text through **Apache PDFBox** in under 15 milliseconds, and dynamically switches to **Tesseract 5.4.0 (Tess4J)** LSTM OCR when image-only scanned certificates are detected.
> 2. **Deterministic, Explainable Rule Engine:** We do **not** use black-box hallucinating LLMs for legal compliance. Instead, our engine runs deterministic regex field extraction, date validity arithmetic, cross-document institution name consistency, and SHA-256 duplicate-hash fraud detection across eight statutory categories.
> 3. **Non-Fabrication Architecture:** SAGE strictly separates technical document parseability from legal statutory compliance. An academic lab manual uploaded under Fire Safety will pass the PDF format check, but instantly triggers a `POTENTIAL_DEFICIENCY` on content relevance.
> 4. **Closed-Loop Officer Review & Formal Notices:** Scrutiny Officers can confirm deficiencies, dismiss false positives with audit remarks, and issue formal deficiency notices with idempotent duplicate protection.
> 5. **Automated Re-Scrutiny Diffs:** When the institution uploads Version 2 of a defective certificate, SAGE preserves Version 1 immutably, performs an automated comparative diff, and automatically transitions resolved defects from `POTENTIAL_DEFICIENCY` to `DEFICIENCY_RESOLVED`.
>
> This shifts the officer’s role from a manual document reader to a high-level compliance decision-maker, cutting scrutiny turnaround time from weeks to minutes."

---

## 2. The 5-Minute Technical Deep-Dive Walkthrough

When presenting to technical evaluators or architectural judges, structure your walkthrough across these five sequential layers:

```
[1. Ingestion & Storage] ──> [2. Dual-Engine Extraction] ──> [3. Rule-Based Evaluation]
                                                                        │
[5. Re-Scrutiny Diffs]   <── [4. Officer Review & Notice] <─────────────┘
```

### Step 1: Secure Ingestion & Traceable Storage (30 Seconds)
- **MinIO Object Store:** Original PDFs are streamed directly to MinIO S3 object storage with cryptographically secure, randomized object keys (`/documents/{appId}/{category}_{uuid}.pdf`).
- **PostgreSQL Relational Tracking:** Metadata, SHA-256 content hashes, upload timestamps, file sizes, and version numbers (`version_number = 1`) are recorded in the `application_documents` table.
- **Traceability:** Every finding in SAGE traces back to a specific document ID, S3 key, and extraction record.

### Step 2: Dual-Engine Text & OCR Pipeline (60 Seconds)
- **Digital Searchable PDFs:** Extracted via [`DocumentTextExtractionService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/ocr/DocumentTextExtractionService.java) using Apache PDFBox `PDFTextStripper`. If character count $\ge 40$, embedded text is ingested directly.
- **Scanned Image-Only Fallback:** If character count $< 40$, the engine renders PDF pages into 200 DPI RGB images via `PDFRenderer` and feeds them to Tess4J (Tesseract 5.4.0) using neural network LSTM models (`eng.traineddata`).
- **Non-Fabrication Compliance:** Digital text reports `confidence_score = NULL` (displayed as `N/A (Embedded Text)`). Optical OCR calculates true aggregated word confidences (e.g. `95.4%`). We never display synthetic or hardcoded confidence numbers.

### Step 3: Explainable Rule Evaluation Engine (60 Seconds)
- **Metadata Extraction:** [`DocumentFieldExtractionService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/ocr/DocumentFieldExtractionService.java) extracts dates (`dd/MM/yyyy`, ISO), certificate registration numbers, PAN numbers, and PIN codes.
- **Rule Processing:** [`ScrutinyValidationService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/validation/ScrutinyValidationService.java) evaluates:
  - **Mandatory Document Presence:** Flags missing required statutory documents as `BLOCKING` deficiencies.
  - **Date Expiry:** Flags certificates expired relative to the approval academic year.
  - **Name Discrepancy:** Compares names extracted across title deeds, affidavits, and NOCs with the application profile.
  - **Duplicate Content Hashing:** Computes SHA-256 hashes across all uploads. If an institution attempts to upload the same PDF across multiple slots (e.g., submitting an Affiliating NOC under Corpus Fund), SAGE immediately flags cross-category duplicate abuse.
- **Four-Quadrant Classification:** Every finding is classified as:
  - `PASS` (Compliant)
  - `POTENTIAL_DEFICIENCY` (Algorithmically identified statutory defect)
  - `NEEDS_MANUAL_REVIEW` (Optical legibility confirmed, but legal authenticity requires human verification)
  - `INFO` (Informational notice)

### Step 4: Human-in-the-Loop Officer Workflow & Notice Issuance (60 Seconds)
- **Interactive Review:** The Scrutiny Officer clicks **Review Finding** on any finding card in the React UI ([`AutomatedScrutinyPanel.jsx`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/frontend/src/components/scrutiny/AutomatedScrutinyPanel.jsx)).
- **Decision Persistence:** Officers can:
  - `CONFIRM_DEFICIENCY`: Validates algorithmic defect into an actionable institutional deficiency.
  - `DISMISS_FALSE_POSITIVE`: Dismisses edge-case findings with mandatory remarks.
  - `REQUEST_MANUAL_VERIFICATION`: Escalates to Expert Visit Committee (EVC).
  - `MANUAL_VERIFIED`: Overrides requirement after physical document inspection.
- **Formal Notice Generation:** Officer selects confirmed deficiencies and clicks **Issue Formal Deficiency Notice**. SAGE generates `ScrutinyDeficiency` records, updates application status, and triggers statutory cure periods.

### Step 5: Automated Re-Scrutiny & Comparative Diffs (60 Seconds)
- **Versioned Ingestion:** When the institution uploads corrected files, SAGE assigns `version_number = 2`, preserving the original defective file for legal audit.
- **Cycle 2 Assessment:** The officer triggers **Run Automated Re-Scrutiny**. SAGE evaluates Cycle 2.
- **Comparative Diff Engine:** [`ReScrutinyService.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/service/ReScrutinyService.java) runs diff algorithms between Cycle 1 and Cycle 2 evidence:
  - Expired date resolved $\rightarrow$ `DEFICIENCY_RESOLVED` badge.
  - Unchanged or still-defective file $\rightarrow$ `DEFICIENCY_PERSISTS` badge.
  - Side-by-side JSON and visual field deltas displayed in the **Automated Re-Scrutiny Diffs** tab.

---

## 3. Major Technical Challenges Encountered & Solutions

| Challenge | Impact / Risk | Architectural Solution Implemented |
|---|---|---|
| **1. The Digital vs Scanned OCR Dilemma** | Searchable digital PDFs do not have optical recognition metrics. Hardcoding fake confidence numbers violates regulatory integrity. | Implemented strict architectural separation: digital PDFs report `confidence = null` (`N/A (Embedded Text)`). Real OCR computes character/word confidence dynamically from Tesseract `ResultIterator`. |
| **2. Conflating Format Validity with Compliance** | An academic syllabus PDF (DAA lab manual) is a perfectly valid PDF. If format parsers grant it a `PASS`, fraudulent files could be certified. | Separated technical parsing (`FORMAT_VALID_PDFBOX_PARSED`) from content relevance (`RULE_DOC_RELEVANCE_*`). The lab manual passes format validation, but receives `POTENTIAL_DEFICIENCY` on relevance. |
| **3. Cross-Category Duplicate Submission Abuse** | Institutions attempting to bypass mandatory document requirements by uploading one valid PDF into multiple categories. | Implemented SHA-256 file content hashing at upload. If identical hashes appear across distinct document categories, SAGE generates `RULE_DUPLICATE_FILE_UPLOAD_*` and consolidates sibling categories. |
| **4. Tesseract Native Library & Classpath Ingestion on Windows** | Tess4J relies on native C++ binaries (`libtesseract`). Missing language packs or path errors caused permanent false latching (`ocrAvailable = false`). | Added `eng.traineddata` (4.1 MB) to both `src/main/resources/tessdata/` and `./tessdata/`. Refactored `TesseractOcrService.java` to dynamically check datapath on every request without permanently latching false. |
| **5. Re-Scrutiny State & Version Consistency** | Submitting corrections risked overwriting existing deficiency history or multiplying duplicate assessment findings. | Protected `deficienciesJson` during workflow transitions, enforced idempotency locks on assessment runs, and recorded explicit comparison records in `scrutiny_rescrutiny_comparisons`. |

---

## 4. Current Limitations & Honest Technical Boundaries

To maintain credibility before SIH evaluators, clearly communicate what the system handles automatically versus what requires human/physical oversight:

1. **Physical Engineering Inspections:** Automated scrutiny inspects building sanction letters and structural certificates for signatures, dates, and issuing bodies. However, physical load-bearing structural integrity requires an on-ground Expert Visit Committee (EVC).
2. **Cursive Handwritten Endorsements:** Tesseract 5 LSTM optical recognition is optimized for printed, typed, and stamped statutory certificates. Heavily cursive, handwritten margin notes may exhibit lower confidence and are intentionally routed to `NEEDS_MANUAL_REVIEW`.
3. **Third-Party Government Server API Integrations:** In our demonstration environment, municipal records, revenue land registry APIs, and bank FDR accounts are validated using rule-based pattern matching and simulated data rather than live production state revenue databases.
4. **Container Host Native Libraries:** Real Tesseract OCR requires native shared libraries (`libtesseract.so.5`) on Linux host environments. In environments lacking native C++ binaries, SAGE gracefully falls back to `OCR_UNAVAILABLE_FALLBACK (NEEDS_MANUAL_REVIEW)` without crashing.

---

## 5. Likely Technical Questions & Answers (SIH Q&A Prep)

### Q1: "Why did you build a deterministic rule engine instead of using a Large Language Model (LLM) like GPT-4 or Gemini for scrutiny?"
> **Answer:**  
> "In statutory government compliance, decisions must be **100% deterministic, explainable, and legally defensible**. If an institution is rejected or issued a deficiency notice, the decision must cite an exact regulation, an unfulfilled clause, or a mathematically verifiable date expiry. LLMs introduce non-deterministic variance, token hallucination risks, and latency costs. In SAGE, our rule engine executes in milliseconds, costs zero API tokens, guarantees reproducible outcomes, and provides full auditability in a court of law."

### Q2: "What prevents an institution from uploading a fraudulent certificate with the correct keywords inserted?"
> **Answer:**  
> "SAGE enforces a **defense-in-depth, human-in-the-loop architecture**. The automated engine acts as a preliminary screening filter to catch missing files, expired dates, name mismatches, and duplicate uploads. For high-stakes statutory compliance (such as Fire Safety or Structural Stability), the rule engine deliberately assigns a status of `NEEDS_MANUAL_REVIEW`. The Scrutiny Officer is presented with the highlighted certificate number and issuing authority, enabling immediate manual verification against official state department portals before formal approval."

### Q3: "How does the duplicate upload detection work across different document slots?"
> **Answer:**  
> "During document upload, SAGE computes the cryptographic SHA-256 hash of the incoming byte stream and stores it in `application_documents.file_hash`. During scrutiny, `ScrutinyValidationService` builds a hash-to-category multimap across all uploaded files. If the same hash appears under multiple categories (such as uploading an Affiliating University NOC under Corpus Fund), SAGE immediately flags `RULE_DUPLICATE_FILE_UPLOAD_*`, lists all affected category slots, and prevents unverified approvals."

### Q4: "How does the Re-Scrutiny engine know which document was corrected?"
> **Answer:**  
> "When an institution submits corrections, new files are uploaded with an incremented version number (`version_number = 2`) linked to the same application ID and document category. The previous version (`version_number = 1`) remains immutable in MinIO. When re-scrutiny runs, `ReScrutinyService` fetches the latest extraction record, compares it against the prior assessment cycle's findings for that specific category, and computes field-level deltas. If the defect is resolved, it marks the status as `DEFICIENCY_RESOLVED`; if the same defective file was re-uploaded, it flags `DEFICIENCY_PERSISTS`."

### Q5: "How does SAGE enforce role-based security during the scrutiny process?"
> **Answer:**  
> "All scrutiny APIs in [`ScrutinyController.java`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/src/main/java/in/gov/aicte/platform/scrutiny/controller/ScrutinyController.java) are protected using Spring Security annotations: `@PreAuthorize(\"hasAnyRole('SCRUTINY_OFFICER', 'ADMIN')\")`. The officer's authenticated username and role are extracted directly from the verified JWT `SecurityContextHolder`—never trusted from request payloads. Furthermore, every action, decision remark, and formal notice issuance is persisted with microsecond timestamps in the immutable `audit_events` ledger."

---

## 6. Live Demonstration Script (Step-by-Step UI Actions)

When demonstrating SAGE live to an evaluator or judge, follow this sequence:

| Step | Action in UI | What to Say / Point Out |
|---|---|---|
| **1** | Open **Scrutiny Officer Dashboard**, navigate to **Application #70** | "Here is an application submitted for AICTE approval under New Institution category." |
| **2** | Click on the **Automated Scrutiny** tab | "Notice the four dedicated tabs: Preliminary Findings, OCR & Extracted Evidence, Deficiencies & Notice, and Automated Re-Scrutiny Diffs." |
| **3** | Show **OCR & Extracted Evidence** tab | "Notice how digital PDFs show `N/A (Embedded Text)` while scanned certificates show actual optical confidence (e.g. 95.4%). Explain our Non-Fabrication Principle." |
| **4** | Switch to **Preliminary Findings** tab | "Point out the separation between technical format passes (`FORMAT_VALID`) and statutory findings (`POTENTIAL_DEFICIENCY`). Show the DAA lab manual test where format passes but content is flagged." |
| **5** | Click **Review Finding** on a potential deficiency | "Demonstrate the interactive officer review modal. Select `CONFIRM_DEFICIENCY`, type officer remarks, and submit. Show that the finding immediately updates with officer name and timestamp." |
| **6** | Click **Issue Formal Deficiency Notice** | "Show that only confirmed deficiencies can be issued into a formal notice. Demonstrate the idempotent protection preventing double-issuance." |
| **7** | Switch to **Automated Re-Scrutiny Diffs** tab | "Show the side-by-side comparison between Version 1 (expired certificate) and Version 2 (renewed certificate). Point out the green `DEFICIENCY_RESOLVED` badge and field delta." |

---

*End of Presentation Notes & Demo Guide. Prepared for SAGE SIH 26262 presentation and evaluation.*
