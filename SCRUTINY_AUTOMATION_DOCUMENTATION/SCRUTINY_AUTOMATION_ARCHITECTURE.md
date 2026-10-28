# SAGE Automated Scrutiny & Re-Scrutiny Engine — Architecture & Workflows

**Platform:** SAGE — Institutional Approval & Compliance Platform (SIH 26262)  
**File Location:** [`SCRUTINY_AUTOMATION_DOCUMENTATION/SCRUTINY_AUTOMATION_ARCHITECTURE.md`](file:///c:/Users/alone/Downloads/SIH2-Copy/SIH2/SCRUTINY_AUTOMATION_DOCUMENTATION/SCRUTINY_AUTOMATION_ARCHITECTURE.md)  
**Date:** October 28, 2026  

---

## 1. High-Level Component & System Architecture

```mermaid
graph TB
    subgraph ClientLayer ["Frontend Client Layer (React 19 + Vite)"]
        UI[Scrutiny Officer Dashboard]
        ASP[AutomatedScrutinyPanel.jsx]
        DC[DataContext.jsx / apiClient]
        UI --> ASP
        ASP --> DC
    end

    subgraph SecurityLayer ["Security & Routing Layer"]
        SEC[Spring Security Filter Chain]
        RBAC{Role-Based Access Control}
        DC -->|HTTP REST + Bearer Token| SEC
        SEC --> RBAC
        RBAC -->|SCRUTINY_OFFICER / ADMIN| SC[ScrutinyController.java]
    end

    subgraph ServiceLayer ["Automation & Business Logic Layer"]
        SC --> SS[ScrutinyService.java]
        SC --> RSS[ReScrutinyService.java]
        SS --> SVS[ScrutinyValidationService.java]
        SS --> DTES[DocumentTextExtractionService.java]
        SS --> DFES[DocumentFieldExtractionService.java]
        RSS --> SVS
        RSS --> DTES
        RSS --> DFES
    end

    subgraph ExtractionEngines ["Document Extraction & Optical Engines"]
        DTES -->|1. Try Native Character Stream| PB_TEXT[Apache PDFBox PDFTextStripper]
        DTES -->|2. Scanned Fallback: 200 DPI Render| PB_IMG[Apache PDFBox PDFRenderer]
        PB_IMG -->|Rendered BufferedImage| TOS[TesseractOcrService.java]
        TOS -->|JNA Native Calls| TESS[Tess4J 5.14.0 / Tesseract 5.4.0 LSTM]
        TESS -->|Read Model| TESSDATA[eng.traineddata]
    end

    subgraph StorageLayer ["Persistence & Object Storage"]
        SS --> MSS[MinioStorageService.java]
        MSS -->|S3 API: PutObject, GetObject| MINIO[(MinIO Object Storage: sage-application-documents)]
        SS --> REPOS[Spring Data JPA Repositories]
        RSS --> REPOS
        REPOS -->|HikariCP Connection Pool| PG[(PostgreSQL Database: aicte_platform_db)]
    end
```

---

## 2. Document Processing & OCR Fallback Workflow

This flowchart details how the dual-engine pipeline extracts text and distinguishes digital from scanned PDFs:

```mermaid
flowchart TD
    Start([Application Document Ingestion]) --> CheckBytes{Bytes null or empty?}
    CheckBytes -- Yes --> ReturnEmpty[Return ExtractedDocumentData: EMPTY_DOCUMENT, status: EMPTY]
    CheckBytes -- No --> CheckFormat{File Type / MIME PDF?}

    CheckFormat -- No Image File --> ImageOcr[extractFromImage: decode via ImageIO]
    ImageOcr --> ExecTessImage[TesseractOcrService.extractTextWithConfidence]
    ExecTessImage --> ReturnOcrImage[Return ExtractedDocumentData: TESSERACT_OCR_IMAGE]

    CheckFormat -- Yes PDF --> LoadPDF[PDDocument.load pdfBytes via Apache PDFBox]
    LoadPDF --> CheckPages{Page Count > 0?}
    CheckPages -- No --> ReturnZeroPages[Return ExtractedDocumentData: 0 Pages, status: EMPTY]

    CheckPages -- Yes --> Stripper[PDFTextStripper.getText: extract embedded text stream]
    Stripper --> CheckCharCount{Trimmed Text >= 40 chars?}

    CheckCharCount -- Yes: Digital PDF --> EmbedText[ExtractedDocumentData.embeddedText]
    EmbedText --> EmbedMetadata[ExtractionMethod: PDFBOX_SEARCHABLE_TEXT<br/>confidenceScore: NULL<br/>validationMetric: 'N/A (Embedded Text)']
    EmbedMetadata --> StructuredExtract[DocumentFieldExtractionService.extractStructuredFields]

    CheckCharCount -- No: Scanned / Blank --> CheckTessAvail{TesseractOcrService.isAvailable()?}
    CheckTessAvail -- No --> TessUnavailable[ExtractedDocumentData.unavailable: Native OCR engine not ready]
    TessUnavailable --> ManualFallback[Flag for Officer Manual Review: RULE_OCR_NEEDS_MANUAL_REVIEW]

    CheckTessAvail -- Yes --> RenderPages[PDFRenderer.renderImageWithDPI: 200 DPI up to 5 pages]
    RenderPages --> TessExec[TesseractOcrService.extractTextWithConfidence on each page]
    TessExec --> AggregateConf[Aggregate Word-Level Confidences via RIL_WORD]
    AggregateConf --> CheckOcrText{Total Recognized Chars > 0?}

    CheckOcrText -- No --> BlankScan[ExtractedDocumentData.empty: Blank or unreadable scan]
    BlankScan --> EmptyRule[Flag RULE_DOC_EMPTY_CONTENT as BLOCKING]

    CheckOcrText -- Yes --> ReturnOcrScanned[ExtractedDocumentData.ocr]
    ReturnOcrScanned --> OcrMetadata[ExtractionMethod: TESSERACT_OCR_SCANNED<br/>confidenceScore: e.g. 0.9537<br/>validationMetric: 'OCR_MEASURED (95.4%)']
    OcrMetadata --> StructuredExtract

    StructuredExtract --> PersistExtraction[(Save to scrutiny_document_extractions)]
```

---

## 3. Statutory Rule Engine & Deficiency Detection Flowchart

```mermaid
flowchart TD
    Start([Execute ScrutinyValidationService.evaluateScrutiny]) --> DupCheck[Check 0: Detect Duplicate Upload Hashes Across All Categories]
    DupCheck --> AnyDup{Shared File Hashes in Multiple Categories?}
    AnyDup -- Yes --> FlagDup[Generate RULE_DUPLICATE_FILE_UPLOAD: BLOCKING deficiency]
    AnyDup -- No --> LoopMandatory[Check A: Iterate Over Mandatory Catalogue Requirements]

    LoopMandatory --> DocUploaded{Document Present in Dossier?}
    DocUploaded -- No --> FlagMissing[Generate RULE_DOC_MANDATORY_MISSING: BLOCKING deficiency]
    DocUploaded -- Yes --> CheckFileSize{File Size < 100 bytes?}
    CheckFileSize -- Yes --> FlagEmptyFile[Generate RULE_DOC_EMPTY: BLOCKING deficiency]
    CheckFileSize -- No --> CheckOcrStatus{Extraction OCR Status}

    CheckOcrStatus -- FAILED --> FlagCorrupt[Generate RULE_DOC_UNREADABLE: BLOCKING deficiency]
    CheckOcrStatus -- EMPTY --> FlagBlankContent[Generate RULE_DOC_EMPTY_CONTENT: BLOCKING deficiency]
    CheckOcrStatus -- UNCERTAIN --> FlagUncertain[Generate RULE_DOC_TEXT_UNCERTAIN: NEEDS_MANUAL_REVIEW]
    CheckOcrStatus -- UNAVAILABLE --> FlagOcrUnavail[Generate RULE_OCR_NEEDS_MANUAL_REVIEW: NEEDS_MANUAL_REVIEW]
    CheckOcrStatus -- SUCCESS --> FlagFormatPass[Generate RULE_DOC_FORMAT_VALID: PASS Advisory]

    FlagFormatPass --> CheckCategoryRelevance[Check B: Category Plausibility & Domain Signals]
    CheckCategoryRelevance --> IsAcademicLab{Contains Academic Lab/DAA Coursework Regex?}
    IsAcademicLab -- Yes --> FlagAcademic[Generate RULE_DOC_CONTENT_MISMATCH: BLOCKING deficiency]
    IsAcademicLab -- No --> HasSignals{Contains Domain Statutory Terminology?}
    HasSignals -- No --> FlagMissingSignals[Generate RULE_DOC_SIGNALS_MISSING: BLOCKING deficiency]
    HasSignals -- Yes --> CategoryRules[Check C: Evaluate Category-Specific Substantive Rules]

    CategoryRules --> FireRules[Fire Safety: Date Expiry vs Current Date]
    FireRules --> FireValid{Expiry Date >= Eval Date?}
    FireValid -- No --> FlagFireExpired[Generate RULE_FIRE_NOC_EXPIRED: BLOCKING]
    FireValid -- Yes --> FlagFireValid[Generate RULE_FIRE_NOC_VALID: PASS]

    FlagFireValid --> FireOcrCheck{Extracted via OCR or Synthetic Fixture?}
    FireOcrCheck -- Yes --> FlagFireAuth[Generate RULE_FIRE_NOC_AUTHENTICITY_VERIFICATION: NEEDS_MANUAL_REVIEW]
    FireOcrCheck -- No --> OtherRules[Occupancy, Land, Stability, Affiliation, Corpus Fund Rules]

    OtherRules --> CrossDoc[Check D: Cross-Document Applicant Name Consistency]
    CrossDoc --> Consolidate[Save all findings to scrutiny_findings]
```

---

## 4. Scrutiny Officer Review & Formal Notice Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Officer as Scrutiny Officer
    participant UI as AutomatedScrutinyPanel.jsx
    participant Controller as ScrutinyController
    participant Service as ScrutinyService
    participant FindingRepo as ScrutinyFindingRepository
    participant DeficiencyRepo as ScrutinyDeficiencyRepository
    participant AppRepo as ApplicationRepository
    participant Audit as AuditEventRepository

    Officer->>UI: Clicks "Review Finding" on finding card
    UI->>Officer: Displays Review Finding Modal (Finding details, options)
    Officer->>UI: Selects "CONFIRM_DEFICIENCY" + enters remarks
    UI->>Controller: POST /api/v1/applications/{appId}/scrutiny/findings/{findingId}/review
    Controller->>Service: reviewFinding(findingId, request, actor)
    Service->>FindingRepo: Update finding (officerDecision='CONFIRMED', officerRemarks, reviewedAt)
    Service->>Audit: Record AuditEvent (FINDING_REVIEWED)
    Service-->>Controller: Return updated ScrutinyFindingResponse
    Controller-->>UI: 200 OK (Updates state and badges)

    Note over Officer,UI: Officer proceeds to issue formal notice
    Officer->>UI: Selects confirmed findings + Clicks "Issue Formal Notice"
    UI->>Officer: Displays Notice Modal (Due date, general observations)
    Officer->>UI: Confirms notice issuance
    UI->>Controller: POST /api/v1/applications/{appId}/deficiencies/issue
    Controller->>Service: issueFormalDeficiencies(appId, request, actor)
    Service->>DeficiencyRepo: Insert formal ScrutinyDeficiency records (status='ISSUED')
    Service->>AppRepo: Update Application stage to 'Re-scrutiny' (status='Returned')
    Service->>Audit: Record AuditEvent (DEFICIENCY_NOTICE_ISSUED)
    Service-->>Controller: Return List<ScrutinyDeficiencyResponse>
    Controller-->>UI: 200 OK (Deficiencies & Notice Tab updated, Safeguard banner active)
```

---

## 5. Automated Re-Scrutiny & Evidence Comparison Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Inst as Institution Representative
    actor Officer as Scrutiny Officer
    participant UI as AutomatedScrutinyPanel.jsx
    participant Controller as ScrutinyController
    participant ScrutinySvc as ScrutinyService
    participant ReScrutinySvc as ReScrutinyService
    participant MinIO as MinioStorageService
    participant DocRepo as ApplicationDocumentRepository
    participant CompRepo as ScrutinyRescrutinyComparisonRepository

    Inst->>Controller: POST /api/v1/applications/{appId}/deficiencies/{defId}/documents (Uploads v2 PDF)
    Controller->>ScrutinySvc: uploadCorrectedDocument(appId, defId, file, actor)
    ScrutinySvc->>MinIO: Store binary bytes as Version 2
    ScrutinySvc->>DocRepo: Save corrected ApplicationDocument (version=2, status='UPLOADED')
    ScrutinySvc-->>Controller: Return ApplicationDocument v2
    Controller-->>Inst: 200 OK (File uploaded successfully)

    Inst->>Controller: POST /api/v1/applications/{appId}/deficiencies/submit-responses
    Controller->>ScrutinySvc: submitDeficiencyResponses(appId, request, actor)
    ScrutinySvc->>ReScrutinySvc: runAutomatedRescrutiny(application, actor)

    Note over ReScrutinySvc: Re-Scrutiny Cycle Execution
    ReScrutinySvc->>MinIO: Download bytes for Version 1 and Version 2
    ReScrutinySvc->>ReScrutinySvc: Extract text & fields for both versions
    ReScrutinySvc->>ReScrutinySvc: Compare values (e.g. Expiry Date 2025-12-31 vs 2027-09-01)
    ReScrutinySvc->>CompRepo: Save ScrutinyRescrutinyComparison (status='RECTIFIED_PENDING_CONFIRMATION')

    Officer->>UI: Navigates to "Automated Re-Scrutiny Diffs" Tab
    UI->>Controller: GET /api/v1/applications/{appId}/scrutiny/comparison
    Controller-->>UI: Returns side-by-side comparison records
    UI->>Officer: Displays Old vs New evidence comparison cards
    Officer->>UI: Clicks "Verify Resolution"
    UI->>Controller: POST /api/v1/applications/{appId}/deficiencies/{defId}/review (decision='VERIFIED_RESOLVED')
    Controller-->>UI: 200 OK (Deficiency marked resolved, audit trail recorded)
```
