# SAGE Automated Scrutiny - Synthetic Test Document Pack

**For local software testing only. Every PDF containing certificate content is explicitly synthetic and has no real legal, regulatory, academic, fire, land, financial, or municipal validity.** The one completely blank PDF intentionally has no watermark so that its true empty-text condition can be tested.

**Prepared for:** SAGE - Institutional Approval & Compliance Platform / AICTE-style demonstration workflow.
**Example application:** APP-2026-552150 | ABC Institute of Technology | INST-1025.
**Test reference date:** 2026-10-28.

## Files

- `01_baseline_category_samples/`: 8 searchable, category-relevant PDFs (trust/society deed, land, building plan, occupancy, fire, structural, university NOC, corpus fund).
- `02_negative_tests/`: unrelated DAA laboratory PDF, wrong-institution fire sample, insufficient-detail PDF, byte-identical duplicate of trust deed.
- `03_rescrutiny_versions/`: expired fire certificate version 1 and corrected future-dated version 2.
- `04_ocr_edge_cases/`: image-only scanned fire sample and completely blank PDF.
- `TEST_CASE_MATRIX.csv`: filenames, upload-category IDs and expected outcomes.

## Test sequences

### A - Positive document-type and extraction sanity checks
1. In a **fresh test application**, upload each of the 8 baseline files into its matching document category (`DOC_*` identifiers in the CSV).
2. Start Automated Scrutiny.
3. Confirm 8 evidence records, text previews and field-source attribution. They are searchable PDF files; PDFBox should extract text. Valid format and relevance may pass, but **statutory authenticity should stay unverified / subject to officer review**.
4. Review all PASS reasons; none should claim genuine regulator approval for these synthetic fixtures.

### B - Incorrect DAA upload and duplicate detection
1. Use another **fresh test application** (or a clean isolated upload scenario).
2. Upload `09_DAA_Algorithms_Lab_Report_WRONG_CATEGORY.pdf` into multiple unrelated document categories.
3. Run scrutiny. Content-specific compliance checks must **not** pass. The software should detect the same file hash in multiple slots and show evidence behind each mismatch.
4. A technical file-format rule may pass independently; it must be labeled format-only.

### C - Institution mismatch
Upload `10_Fire_Safety_WRONG_INSTITUTION.pdf` under `DOC_FIRE_SAFETY` in an application belonging to ABC Institute of Technology. Expect an applicant-name mismatch or explicit manual review, not a substantive PASS.

### D - Formal deficiency workflow
As an authorized Scrutiny Officer, review a well-supported negative finding, record decision and rationale, issue a formal notice (if SAGE's workflow enables it), then verify visibility in the institution account and audit log. Preliminary findings alone need not create automatic official notices.

### E - Re-scrutiny version comparison
1. In a **separate test application**, upload `13_Fire_Safety_v1_EXPIRED.pdf` under `DOC_FIRE_SAFETY`.
2. Run scrutiny. The expiry date is 2025-12-31, which is past the test reference date 2026-10-28. If this rule is configured, the app should flag expiry.
3. Have an officer review/issue a correction notice according to existing access controls.
4. Upload `14_Fire_Safety_v2_CORRECTED.pdf` as a **new version of the SAME document category** and re-run scrutiny.
5. Confirm old version preserved, new assessment cycle generated, and a diff showing changed validity date and findings. **Do not auto-certify authenticity or auto-close a confirmed deficiency without authorized review.**

### F - Actual OCR check
Upload `15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf`. Its pages contain raster images only (no selectable text). PDFBox alone cannot extract its contents. Tess4J/Tesseract should extract text or record an honest OCR failure; inspect actual OCR output and per-page provenance.

### G - Empty / insufficient content
Upload `16_Completely_Blank_Page.pdf` and `11_Blank_Unreadable_Content.pdf` separately. A readable PDF with inadequate evidence or a completely blank page must not content-PASS.

## Expected results are qualitative, not guaranteed AICTE decisions
SAGE's actual APH rules, required data fields and verification requirements may differ. These fixtures test extraction, classification, evidence association, negative detection, officer review, and state transitions. A local system must not treat a synthetic certificate as genuinely verified, nor must it pretend that test fixtures prove official regulatory compliance.

## Safety / reproducibility
- Keep these documents inside local test applications; do not submit them to an actual institution, authority, university, or regulator.
- Prefer fresh test applications for each scenario to avoid old assessment cycles or seed data affecting the counts.
- Never delete live data or MinIO volumes to rerun these cases.
- Use the exact same file for a multiple-category duplicate-hash test.
- If re-scrutiny does not yield an updated cycle, check document versioning and backend logs; do not fabricate a diff.
