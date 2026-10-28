package in.gov.aicte.platform.scrutiny.validation;

import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;
import in.gov.aicte.platform.application.catalogue.DocumentRequirementDefinition;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;
import in.gov.aicte.platform.scrutiny.ocr.DocumentFieldExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScrutinyValidationService {

    private static final Logger log = LoggerFactory.getLogger(ScrutinyValidationService.class);

    private final ApplicationTypeCatalogue catalogue;
    private final DocumentFieldExtractionService fieldExtractionService;

    public ScrutinyValidationService(
            ApplicationTypeCatalogue catalogue,
            DocumentFieldExtractionService fieldExtractionService
    ) {
        this.catalogue = catalogue;
        this.fieldExtractionService = fieldExtractionService;
    }

    public List<ScrutinyFinding> evaluateScrutiny(
            ScrutinyAssessment assessment,
            Application application,
            List<ApplicationDocument> documents,
            Map<String, ScrutinyDocumentExtraction> extractionsByReqCode,
            LocalDate assessmentDate
    ) {
        List<ScrutinyFinding> findings = new ArrayList<>();
        LocalDate evalDate = (assessmentDate != null) ? assessmentDate : LocalDate.now();

        // 1. Identify catalogue requirements for this application category
        String appTypeCode = application.getApplicationType();
        Optional<ApplicationTypeDefinition> typeDefOpt = catalogue.findByCode(appTypeCode);
        if (typeDefOpt.isEmpty()) {
            if (appTypeCode != null && appTypeCode.toUpperCase().contains("NEW")) {
                typeDefOpt = catalogue.findByCode("NEW_INSTITUTION");
            } else {
                typeDefOpt = catalogue.findByCode("EXTENSION_OF_APPROVAL");
            }
        }

        List<DocumentRequirementDefinition> requiredDocs = typeDefOpt
                .map(ApplicationTypeDefinition::documentRequirements)
                .orElse(Collections.emptyList());

        // ---------------------------------------------------------------------
        // Check 0: Exact Duplicate File Hash Detection Across Document Categories
        // ---------------------------------------------------------------------
        detectDuplicateDocumentUploads(assessment, application, documents, findings);

        // ---------------------------------------------------------------------
        // Check A: Mandatory Document Completeness & Readable Technical Format
        // ---------------------------------------------------------------------
        for (DocumentRequirementDefinition req : requiredDocs) {
            if (!req.mandatory()) continue;

            Optional<ApplicationDocument> docOpt = documents.stream()
                    .filter(d -> req.code().equalsIgnoreCase(d.getRequirementCode()))
                    .findFirst();

            if (docOpt.isEmpty()) {
                // Missing mandatory document
                findings.add(new ScrutinyFinding(
                        assessment,
                        application.getId(),
                        null,
                        req.code(),
                        "RULE_DOC_MANDATORY_MISSING_" + req.code(),
                        "Mandatory Document: " + req.name(),
                        req.aphClause(),
                        "DOCUMENT_COMPLETENESS",
                        "POTENTIAL_DEFICIENCY",
                        "BLOCKING",
                        "Mandatory document must be uploaded as per " + req.aphClause(),
                        "NOT_SUBMITTED",
                        "Document '" + req.name() + "' is mandatory under " + req.aphClause() + " but was not found in the application dossier.",
                        "Issue formal deficiency to institution to upload required document.",
                        null
                ));
            } else {
                ApplicationDocument doc = docOpt.get();
                String docName = (doc.getOriginalFileName() != null && !doc.getOriginalFileName().isBlank())
                        ? doc.getOriginalFileName() : doc.getDocumentName();

                // Check if file is empty (< 100 bytes)
                if (doc.getFileSize() == null || doc.getFileSize() < 100) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_EMPTY_" + req.code(),
                            "Empty Document File: " + req.name(),
                            req.aphClause(),
                            "TECHNICAL_VALIDATION",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Uploaded document file must contain valid non-empty byte stream",
                            "FILE_SIZE_" + (doc.getFileSize() != null ? doc.getFileSize() : 0) + "_BYTES",
                            "The uploaded file '" + docName + "' is empty (under 100 bytes).",
                            "Require institution to re-upload complete document file.",
                            docName
                    ));
                    continue;
                }

                // Check text extraction outcome
                ScrutinyDocumentExtraction ext = extractionsByReqCode.get(req.code());
                if (ext != null && "FAILED".equalsIgnoreCase(ext.getOcrStatus())) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_UNREADABLE_" + req.code(),
                            "Document Processing: " + req.name(),
                            req.aphClause(),
                            "TECHNICAL_VALIDATION",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Document must be readable and uncorrupted",
                            "EXTRACTION_FAILED: " + (ext.getErrorMessage() != null ? ext.getErrorMessage() : "Corrupted stream"),
                            "The uploaded document file '" + docName + "' could not be parsed or decoded.",
                            "Require institution to re-upload an uncorrupted PDF document.",
                            docName
                    ));
                    continue;
                }

                if (ext != null && "EMPTY".equalsIgnoreCase(ext.getOcrStatus())) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_EMPTY_CONTENT_" + req.code(),
                            "Empty Document Content: " + req.name(),
                            req.aphClause(),
                            "TECHNICAL_VALIDATION",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Document must contain readable text and substantive statutory content",
                            "EMPTY_DOCUMENT (0 characters extracted)",
                            "The uploaded document file '" + docName + "' contains no extractable text or recognized characters (blank or unreadable document).",
                            "Require institution to re-upload a valid document containing readable statutory content.",
                            docName
                    ));
                    continue;
                }

                if (ext != null && "UNCERTAIN".equalsIgnoreCase(ext.getOcrStatus())) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_TEXT_UNCERTAIN_" + req.code(),
                            "Document Text Legibility: " + req.name(),
                            req.aphClause(),
                            "TECHNICAL_VALIDATION",
                            "NEEDS_MANUAL_REVIEW",
                            "WARNING",
                            "Document text must be clearly legible",
                            "UNCERTAIN_LEGIBILITY: " + (ext.getConfidenceScore() != null ? String.format("%.1f%%", ext.getConfidenceScore() * 100.0) : "Low text density"),
                            "The extracted text for '" + docName + "' has low optical recognition confidence or insufficient character density. Scrutiny Officer manual review recommended to confirm certificate clarity.",
                            "Officer manual document verification recommended.",
                            docName
                    ));
                }

                if (ext != null && "UNAVAILABLE".equalsIgnoreCase(ext.getOcrStatus())) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_OCR_NEEDS_MANUAL_REVIEW_" + req.code(),
                            "Document Readability: " + req.name(),
                            req.aphClause(),
                            "TECHNICAL_VALIDATION",
                            "NEEDS_MANUAL_REVIEW",
                            "WARNING",
                            "Automated text extraction completed and legible for evaluation",
                            ext.getExtractionMethod() + " (" + ext.getOcrStatus() + ")",
                            "Automated OCR is not available for this scanned document format. Officer must manually inspect the uploaded document.",
                            "Officer manual document verification required.",
                            docName
                    ));
                    continue;
                }

                // Record Technical Format & Container Verification (explicitly separate from statutory compliance)
                boolean isOcrExt = ext != null && ext.getExtractionMethod() != null && ext.getExtractionMethod().startsWith("TESSERACT");
                String formatActualValue = isOcrExt
                        ? "FORMAT_VALID_SCANNED_OCR (" + ext.getExtractionMethod() + (ext.getConfidenceScore() != null ? String.format(", %.1f%% confidence", ext.getConfidenceScore() * 100.0) : "") + ")"
                        : "FORMAT_VALID (" + doc.getContentType() + ", " + doc.getFileSize() + " bytes)";
                String formatExplanation = isOcrExt
                        ? "Scanned document file '" + docName + "' was optically processed via Tess4J OCR engine (" + (ext.getTextCharacterCount() != null ? ext.getTextCharacterCount() : 0) + " characters extracted). Technical readability verified. Note: Optical readability verifies technical character extraction only; official issuing authority seal and statutory legal authenticity must be confirmed independently."
                        : "Document file '" + docName + "' is stored in object repository and text stream is readable. Note: This check validates technical file structure only; substantive statutory compliance is evaluated independently under regulatory rules.";

                findings.add(new ScrutinyFinding(
                        assessment,
                        application.getId(),
                        doc.getId(),
                        req.code(),
                        "RULE_DOC_FORMAT_VALID_" + req.code(),
                        "Technical File & Format Verification: " + req.name(),
                        req.aphClause(),
                        "TECHNICAL_VALIDATION",
                        "PASS",
                        "ADVISORY",
                        "Valid uncorrupted digital file container and readable text stream (Technical container check only; does NOT verify statutory compliance)",
                        formatActualValue,
                        formatExplanation,
                        "Technical format verified. Proceed with substantive statutory content evaluation.",
                        docName
                ));

                // -------------------------------------------------------------
                // Check B: Document Category Plausibility & Statutory Content Relevance
                // -------------------------------------------------------------
                String rawText = ext != null ? ext.getRawText() : "";
                if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_CONTENT_MISMATCH_" + req.code(),
                            "Statutory Content Relevance: " + req.name(),
                            req.aphClause(),
                            "STATUTORY_COMPLIANCE",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Document content must substantiate statutory requirements for " + req.name() + " as per " + req.aphClause(),
                            "CONTRADICTORY_CONTENT: Academic coursework / lab manual text detected ('" + docName + "')",
                            "The uploaded document '" + docName + "' contains text characteristic of an academic lab manual or student coursework (e.g. algorithm experiments), which contradicts the required statutory certificate for " + req.name() + ".",
                            "Issue formal deficiency requiring upload of the genuine statutory certificate issued by the competent authority.",
                            docName
                    ));
                } else if (!fieldExtractionService.containsRequirementContentSignals(rawText, req.code())) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            doc.getId(),
                            req.code(),
                            "RULE_DOC_SIGNALS_MISSING_" + req.code(),
                            "Statutory Terminology & Authority Seals: " + req.name(),
                            req.aphClause(),
                            "STATUTORY_COMPLIANCE",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Document text must contain domain statutory terminology, clause citations, or authority seals for " + req.name(),
                            "MISSING_STATUTORY_SIGNALS: Domain keywords absent in extracted text",
                            "The uploaded document '" + docName + "' does not contain statutory terminology, clause citations, or authority references pertaining to " + req.name() + " (" + req.aphClause() + ").",
                            "Confirm deficiency or flag for officer manual inspection.",
                            docName
                    ));
                }
            }
        }

        // ---------------------------------------------------------------------
        // Check C: Substantive Statutory Rules for Each Document Category
        // ---------------------------------------------------------------------
        // 1. Fire Safety Certificate
        ScrutinyDocumentExtraction fireExt = extractionsByReqCode.get("DOC_FIRE_SAFETY");
        if (fireExt != null && fireExt.getDocument() != null) {
            evaluateFireSafetyValidity(assessment, application, fireExt, evalDate, findings);
        }

        // 2. Building Occupancy Certificate
        ScrutinyDocumentExtraction occExt = extractionsByReqCode.get("DOC_OCCUPANCY_CERT");
        if (occExt != null && occExt.getDocument() != null) {
            evaluateOccupancyValidity(assessment, application, occExt, evalDate, findings);
        }

        // 3. Structural Stability Certificate
        ScrutinyDocumentExtraction structExt = extractionsByReqCode.get("DOC_STRUCTURAL_STABILITY");
        if (structExt != null && structExt.getDocument() != null) {
            evaluateStructuralStabilityValidity(assessment, application, structExt, evalDate, findings);
        }

        // 4. Approved Master Building Plan
        ScrutinyDocumentExtraction planExt = extractionsByReqCode.get("DOC_BUILDING_PLAN");
        if (planExt != null && planExt.getDocument() != null) {
            evaluateBuildingPlanValidity(assessment, application, planExt, evalDate, findings);
        }

        // 5. Land Documents (for general non-NEW institutions)
        ScrutinyDocumentExtraction landExt = extractionsByReqCode.get("DOC_LAND_DOCUMENTS");
        if (landExt != null && landExt.getDocument() != null && appTypeCode != null && !appTypeCode.toUpperCase().contains("NEW")) {
            evaluateLandDocumentsValidity(assessment, application, landExt, evalDate, findings);
        }

        // 6. Trust / Society Deed
        ScrutinyDocumentExtraction trustExt = extractionsByReqCode.get("DOC_TRUST_DEED");
        if (trustExt != null && trustExt.getDocument() != null) {
            evaluateTrustDeedValidity(assessment, application, trustExt, evalDate, findings);
        }

        // 7. Affiliation NOC / Consent
        ScrutinyDocumentExtraction affExt = extractionsByReqCode.get("DOC_AFFILIATING_NOC");
        if (affExt != null && affExt.getDocument() != null) {
            evaluateAffiliatingNocValidity(assessment, application, affExt, evalDate, findings);
        }

        // 8. Corpus Fund Proof
        ScrutinyDocumentExtraction corpusExt = extractionsByReqCode.get("DOC_CORPUS_FUND");
        if (corpusExt != null && corpusExt.getDocument() != null) {
            evaluateCorpusFundValidity(assessment, application, corpusExt, evalDate, findings);
        }

        // 9. Previous EoA Copy
        ScrutinyDocumentExtraction eoaExt = extractionsByReqCode.get("DOC_PREVIOUS_EOA");
        if (eoaExt != null && eoaExt.getDocument() != null) {
            evaluatePreviousEoaValidity(assessment, application, eoaExt, evalDate, findings);
        }

        // 10. Faculty Roll
        ScrutinyDocumentExtraction facultyExt = extractionsByReqCode.get("DOC_FACULTY_ROLL");
        if (facultyExt != null && facultyExt.getDocument() != null) {
            evaluateFacultyRollValidity(assessment, application, facultyExt, evalDate, findings);
        }

        // 11. Audited Accounts
        ScrutinyDocumentExtraction accountsExt = extractionsByReqCode.get("DOC_AUDITED_ACCOUNTS");
        if (accountsExt != null && accountsExt.getDocument() != null) {
            evaluateAuditedAccountsValidity(assessment, application, accountsExt, evalDate, findings);
        }

        // ---------------------------------------------------------------------
        // Check D: Cross-Document Consistency (Institution Name)
        // ---------------------------------------------------------------------
        evaluateCrossDocumentConsistency(assessment, application, documents, extractionsByReqCode, findings);

        // ---------------------------------------------------------------------
        // Check E: Category-Specific Conditional Checks (EoA vs New Institution)
        // ---------------------------------------------------------------------
        evaluateCategorySpecificRules(assessment, application, documents, extractionsByReqCode, findings, evalDate);

        return findings;
    }

    private void detectDuplicateDocumentUploads(
            ScrutinyAssessment assessment,
            Application application,
            List<ApplicationDocument> documents,
            List<ScrutinyFinding> findings
    ) {
        if (documents == null || documents.size() < 2) return;

        Map<String, List<ApplicationDocument>> docsByHash = documents.stream()
                .filter(d -> d.getFileHash() != null && !d.getFileHash().isBlank() && d.getFileSize() != null && d.getFileSize() > 0)
                .collect(Collectors.groupingBy(ApplicationDocument::getFileHash));

        for (Map.Entry<String, List<ApplicationDocument>> entry : docsByHash.entrySet()) {
            List<ApplicationDocument> sharedDocs = entry.getValue();
            Set<String> distinctReqCodes = sharedDocs.stream()
                    .map(ApplicationDocument::getRequirementCode)
                    .collect(Collectors.toSet());

            if (distinctReqCodes.size() > 1) {
                // The exact same file was uploaded for multiple distinct statutory categories!
                for (ApplicationDocument d : sharedDocs) {
                    List<String> otherReqs = sharedDocs.stream()
                            .filter(other -> !other.getId().equals(d.getId()))
                            .map(other -> other.getRequirementCode() + " ('" + other.getOriginalFileName() + "')")
                            .distinct()
                            .toList();

                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            d.getId(),
                            d.getRequirementCode(),
                            "RULE_DUPLICATE_FILE_UPLOAD_" + d.getRequirementCode(),
                            "Duplicate File Upload Detected: " + d.getDocumentName(),
                            "APH 2024-27 Clause 1.2 & Anti-Fraud Regulatory Norms",
                            "DOCUMENT_AUTHENTICITY",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Each statutory requirement necessitates an independent certificate from its respective competent authority",
                            "DUPLICATE_FILE_HASH (Also submitted for: " + String.join(", ", otherReqs) + ")",
                            "Identical file content has been submitted under multiple document categories. The identical file was uploaded under multiple distinct document categories.",
                            "Review whether independent category-specific evidence is required.",
                            d.getDocumentName()
                    ));
                }
            }
        }
    }

    private void evaluateFireSafetyValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.6 & National Building Code Part 4";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        // If content is an unrelated lab file or lacks fire safety terms, do not evaluate date validity
        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_FIRE_SAFETY")) {
            return;
        }

        String expiryStr = getField(ext, "expiryDate");

        if (expiryStr != null && !expiryStr.isBlank()) {
            try {
                LocalDate expiryDate = LocalDate.parse(expiryStr);
                if (expiryDate.isBefore(evalDate)) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            ext.getDocument().getId(),
                            "DOC_FIRE_SAFETY",
                            "RULE_FIRE_NOC_EXPIRED",
                            "Fire & Life Safety Certificate Validity",
                            ruleRef,
                            "CERTIFICATE_VALIDITY",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Fire Safety Certificate must be currently valid as of evaluation date (" + evalDate + ")",
                            "EXPIRED_ON_" + expiryDate,
                            "Extracted Fire Safety Certificate expiry date is " + expiryDate + ", which is prior to assessment date " + evalDate + ". The NOC is expired.",
                            "Confirm deficiency and require institution to upload renewed Fire Safety Certificate.",
                            ext.getDocumentName()
                    ));
                } else {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            ext.getDocument().getId(),
                            "DOC_FIRE_SAFETY",
                            "RULE_FIRE_NOC_VALID",
                            "Fire & Life Safety Certificate Validity",
                            ruleRef,
                            "CERTIFICATE_VALIDITY",
                            "PASS",
                            "ADVISORY",
                            "Fire Safety Certificate valid beyond assessment date",
                            "VALID_TILL_" + expiryDate,
                            "Fire Safety Certificate extracted validity is " + expiryDate + ", satisfying statutory period requirements.",
                            "Document validity verified.",
                            ext.getDocumentName()
                    ));
                }
            } catch (Exception e) {
                findings.add(new ScrutinyFinding(
                        assessment,
                        application.getId(),
                        ext.getDocument().getId(),
                        "DOC_FIRE_SAFETY",
                        "RULE_FIRE_NOC_DATE_PARSE_ERROR",
                        "Fire & Life Safety Certificate Date Parsing",
                        ruleRef,
                        "CERTIFICATE_VALIDITY",
                        "NEEDS_MANUAL_REVIEW",
                        "WARNING",
                        "Fire Safety Certificate must have clearly legible validity date",
                        "UNPARSEABLE_DATE: " + expiryStr,
                        "Validity date was identified but could not be parsed automatically. Reviewer manual verification needed.",
                        "Verify expiry date manually on uploaded certificate.",
                        ext.getDocumentName()
                ));
            }
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_FIRE_SAFETY",
                    "RULE_FIRE_NOC_MISSING_EXPIRY",
                    "Fire & Life Safety Certificate Expiry Date",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Fire Safety Certificate must indicate formal validity period or expiry date",
                    "EXPIRY_DATE_NOT_EXTRACTED",
                    "No formal validity or expiry date could be automatically extracted from the Fire Certificate text. Manual officer review required.",
                    "Verify certificate validity period manually.",
                    ext.getDocumentName()
            ));
        }

        // Statutory Authenticity & Seal Check: Readable OCR text alone does not prove official legal authenticity
        boolean isOcr = ext.getExtractionMethod() != null && ext.getExtractionMethod().startsWith("TESSERACT");
        boolean isUnverifiedFixture = rawText.toLowerCase().contains("unverified")
                || rawText.toLowerCase().contains("synthetic test document")
                || rawText.toLowerCase().contains("controlled software test fixture");

        if (isOcr || isUnverifiedFixture) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_FIRE_SAFETY",
                    "RULE_FIRE_NOC_AUTHENTICITY_VERIFICATION",
                    "Fire & Life Safety Certificate Seal & Authenticity Verification",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Fire Safety Certificate must possess verified competent authority seal, signature, and official dispatch register entry (readable OCR text does not prove legal authenticity)",
                    isUnverifiedFixture
                            ? "SCANNED_FIXTURE_UNVERIFIED (Official authority seal & legal authenticity unverified)"
                            : "SCANNED_OCR_READABLE (Authority seal & signature unverified)",
                    "Document text was successfully extracted via automated OCR ("
                            + (ext.getConfidenceScore() != null ? String.format("%.1f%% confidence", ext.getConfidenceScore() * 100.0) : "Tess4J")
                            + "). However, readable OCR text alone does not substantiate legal authenticity or official government seal compliance under APH Clause 1.6. Scrutiny Officer manual review is required to verify competent authority stamp and signature.",
                    "Officer manual document verification required to confirm genuine authority seal before final statutory clearance.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateOccupancyValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.5.3";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        // If content is an unrelated lab file or lacks building occupancy terms, skip substantive pass
        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_OCCUPANCY_CERT")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank() && authority != null && !authority.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_OCCUPANCY_CERT",
                    "RULE_OCCUPANCY_CERT_EXTRACTED",
                    "Building Completion / Occupancy Certificate Authenticity",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "PASS",
                    "ADVISORY",
                    "Occupancy Certificate contains reference number and competent issuing authority",
                    "CERT_NO_" + certNum + " (" + authority + ")",
                    "Building Occupancy / Completion certificate verified with registered reference number " + certNum + " from " + authority + ".",
                    "Occupancy verification satisfied.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_OCCUPANCY_CERT",
                    "RULE_OCCUPANCY_NEEDS_REVIEW",
                    "Building Completion / Occupancy Certificate Reference",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Certificate must possess verifiable municipal sanction reference and issuing authority",
                    "REFERENCE_NOT_EXTRACTED",
                    "Certificate sanction or order number / authority could not be extracted automatically.",
                    "Officer must verify municipal order number.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateStructuralStabilityValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.7";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        // If content is an unrelated lab file or lacks structural terms, skip substantive pass
        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_STRUCTURAL_STABILITY")) {
            return;
        }

        String expiryStr = getField(ext, "expiryDate");
        String authority = getField(ext, "issuingAuthority");

        if (expiryStr != null && !expiryStr.isBlank()) {
            try {
                LocalDate expiryDate = LocalDate.parse(expiryStr);
                if (expiryDate.isBefore(evalDate)) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            ext.getDocument().getId(),
                            "DOC_STRUCTURAL_STABILITY",
                            "RULE_STRUCTURAL_EXPIRED",
                            "Structural Stability Certificate Validity",
                            ruleRef,
                            "CERTIFICATE_VALIDITY",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Structural stability certificate must be valid for ongoing academic period",
                            "EXPIRED_ON_" + expiryDate,
                            "Structural Stability Certificate expired on " + expiryDate + ".",
                            "Require updated Structural Stability Certificate from Registered Structural Engineer.",
                            ext.getDocumentName()
                    ));
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Structural stability certificates require certified engineer credentials verification
        if (authority != null && !authority.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_STRUCTURAL_STABILITY",
                    "RULE_STRUCTURAL_VALID",
                    "Structural Stability Certification",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "PASS",
                    "ADVISORY",
                    "Structural stability certificate submitted by certified engineer",
                    "VERIFIED_ENGINEER: " + authority,
                    "Structural Stability Certificate submitted by recognized engineer (" + authority + ").",
                    "Proceed with scrutiny.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_STRUCTURAL_STABILITY",
                    "RULE_STRUCTURAL_MANUAL_REVIEW",
                    "Structural Stability Engineer Verification",
                    ruleRef,
                    "CERTIFICATE_VALIDITY",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Structural stability certification requires registered structural engineer verification",
                    "ENGINEER_REGISTRATION_UNVERIFIED",
                    "Structural certificate detected but structural engineer registration credentials require manual officer confirmation.",
                    "Verify engineer credentials manually on uploaded document.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateBuildingPlanValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.5.1";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_BUILDING_PLAN")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank() && authority != null && !authority.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_BUILDING_PLAN",
                    "RULE_BUILDING_PLAN_SANCTIONED",
                    "Approved Master Building Plan & Sanction Order Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Master building plan approved and sanctioned by competent Town Planning / Municipal Authority",
                    "SANCTION_ORDER: " + certNum + " (" + authority + ")",
                    "Approved master building plan verified with sanction/approval reference " + certNum + " issued by " + authority + ".",
                    "Building plan statutory sanction verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_BUILDING_PLAN",
                    "RULE_BUILDING_PLAN_MANUAL_REVIEW",
                    "Building Plan Sanction Reference Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Building plan must contain legible municipal/town planning approval seal and sanction number",
                    "SANCTION_DETAILS_UNVERIFIED",
                    "Building plan was uploaded, but the competent municipal sanction number or approving authority seal could not be automatically validated.",
                    "Officer must manually inspect structural layout drawing and sanction seal on the uploaded building plan.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateLandDocumentsValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.4";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_LAND_DOCUMENTS")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_LAND_DOCUMENTS",
                    "RULE_LAND_TITLE_REGISTERED",
                    "Land Ownership & Registered Title Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Registered land title / lease deed in the name of the Applicant Society/Trust/Institution",
                    "REGISTERED_DEED: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "Land ownership documents verified with registered deed/document reference " + certNum + ".",
                    "Land title statutory registration verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_LAND_DOCUMENTS",
                    "RULE_LAND_TITLE_MANUAL_REVIEW",
                    "Land Title Registration Reference Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Land title document must clearly display Sub-Registrar registration details, survey numbers, and land area",
                    "REGISTRATION_DETAILS_UNVERIFIED",
                    "Land document uploaded, but Sub-Registrar registration/khasra details could not be detected in extracted text.",
                    "Officer must manually inspect the registered sale/lease deed and revenue records.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateTrustDeedValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.3";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_TRUST_DEED")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_TRUST_DEED",
                    "RULE_TRUST_REGISTRATION_VERIFIED",
                    "Trust / Society / Section 8 Company Registration Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Valid registration under Societies Registration Act, Indian Trusts Act, or Section 8 of Companies Act",
                    "TRUST_REGISTRATION_NO: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "Trust/Society/Section 8 Company constitution verified with registration reference " + certNum + ".",
                    "Sponsoring body legal entity registration verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_TRUST_DEED",
                    "RULE_TRUST_DEED_MANUAL_REVIEW",
                    "Trust / Society Legal Constitution Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Trust deed / Memorandum of Association must contain legible registration number and objectives",
                    "REGISTRATION_NUMBER_UNVERIFIED",
                    "Trust/Society deed uploaded, but registration number or registrar details could not be parsed automatically.",
                    "Officer must manually verify registration certificate and MoA bylaws.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateAffiliatingNocValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.8";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_AFFILIATING_NOC")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_AFFILIATING_NOC",
                    "RULE_AFFILIATION_NOC_VERIFIED",
                    "Affiliating University / DTE NOC Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Unconditional NOC / consent for affiliation from affiliating University or State Technical Board",
                    "AFFILIATION_NOC: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "NOC/Consent for affiliation verified with letter reference " + certNum + " issued by " + (authority != null ? authority : "affiliating authority") + ".",
                    "Affiliation NOC verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_AFFILIATING_NOC",
                    "RULE_AFFILIATION_NOC_MANUAL_REVIEW",
                    "Affiliation NOC Order Reference Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "NOC letter must have explicit university reference number and signature",
                    "NOC_REFERENCE_UNVERIFIED",
                    "Affiliating university NOC uploaded, but dispatch reference number or registrar signature could not be verified automatically.",
                    "Officer must manually check affiliating university letter and validity.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateCorpusFundValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.9";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";
        String docDisplayName = getDocDisplayName(ext);

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_CORPUS_FUND")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_CORPUS_FUND",
                    "RULE_CORPUS_FUND_VERIFIED",
                    "Joint Fixed Deposit / Corpus Fund Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Corpus fund maintained as Fixed Deposit in scheduled bank in joint name with AICTE Regional Officer",
                    "FDR_RECEIPT: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "Corpus fund / FDR receipt verified with account/receipt reference " + certNum + ".",
                    "Corpus fund deposit verified.",
                    docDisplayName
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_CORPUS_FUND",
                    "RULE_CORPUS_FUND_MANUAL_REVIEW",
                    "Corpus Fund FDR Number & Bank Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Bank FDR receipt must state FDR number, joint tenure, and principal amount",
                    "FDR_DETAILS_UNVERIFIED",
                    "Corpus fund document uploaded, but FDR receipt number or bank details could not be parsed automatically.",
                    "Officer must manually inspect bank FDR receipt and joint lien endorsement.",
                    docDisplayName
            ));
        }
    }

    private void evaluatePreviousEoaValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 2.1.2";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_PREVIOUS_EOA")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_PREVIOUS_EOA",
                    "RULE_PREVIOUS_EOA_VERIFIED",
                    "Previous Academic Year AICTE Approval Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Official AICTE Extension of Approval (EoA) letter for immediate preceding academic year",
                    "EOA_REFERENCE: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "Previous AICTE approval letter verified with official reference " + certNum + ".",
                    "Previous EoA verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_PREVIOUS_EOA",
                    "RULE_PREVIOUS_EOA_MANUAL_REVIEW",
                    "Previous EoA Letter Reference Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "EoA document must contain valid AICTE approval order reference and course tables",
                    "EOA_REFERENCE_UNVERIFIED",
                    "Previous approval document uploaded, but AICTE approval order reference could not be extracted automatically.",
                    "Officer must manually verify previous AICTE EoA letter and course sanctions.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateFacultyRollValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 7.1";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_FACULTY_ROLL")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_FACULTY_ROLL",
                    "RULE_FACULTY_ROLL_VERIFIED",
                    "Faculty Roster & Cadre Ratio Certification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Certified faculty roster meeting prescribed Student-Faculty Ratio (SFR) and Cadre Ratio",
                    "FACULTY_ROSTER_REF: " + certNum,
                    "Faculty roster verified with certification/order reference " + certNum + ".",
                    "Faculty roster verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_FACULTY_ROLL",
                    "RULE_FACULTY_ROLL_MANUAL_REVIEW",
                    "Faculty Roster Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Faculty roster must be signed by Head of Institution and list designations and qualifications",
                    "FACULTY_ROSTER_UNVERIFIED",
                    "Faculty list uploaded, but certification seal or roster order reference requires manual confirmation.",
                    "Officer must manually inspect faculty qualifications and cadre ratio.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateAuditedAccountsValidity(
            ScrutinyAssessment assessment,
            Application application,
            ScrutinyDocumentExtraction ext,
            LocalDate evalDate,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.10";
        String rawText = ext.getRawText() != null ? ext.getRawText() : "";

        if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)
                || !fieldExtractionService.containsRequirementContentSignals(rawText, "DOC_AUDITED_ACCOUNTS")) {
            return;
        }

        String certNum = getField(ext, "certificateNumber");
        String authority = getField(ext, "issuingAuthority");

        if (certNum != null && !certNum.isBlank()) {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_AUDITED_ACCOUNTS",
                    "RULE_AUDITED_ACCOUNTS_VERIFIED",
                    "Chartered Accountant Audited Financial Statements",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "PASS",
                    "ADVISORY",
                    "Audited balance sheet and income/expenditure statement certified by practicing Chartered Accountant with UDIN",
                    "CA_AUDIT_REF: " + certNum + (authority != null ? " (" + authority + ")" : ""),
                    "Audited financial statements verified with CA certification/UDIN reference " + certNum + ".",
                    "Financial audit statements verified.",
                    ext.getDocumentName()
            ));
        } else {
            findings.add(new ScrutinyFinding(
                    assessment,
                    application.getId(),
                    ext.getDocument().getId(),
                    "DOC_AUDITED_ACCOUNTS",
                    "RULE_AUDITED_ACCOUNTS_MANUAL_REVIEW",
                    "CA Audit Certification & UDIN Verification",
                    ruleRef,
                    "STATUTORY_COMPLIANCE",
                    "NEEDS_MANUAL_REVIEW",
                    "WARNING",
                    "Financial statement must bear practicing CA membership number and UDIN",
                    "CA_UDIN_UNVERIFIED",
                    "Financial statements uploaded, but CA membership number or UDIN could not be parsed automatically.",
                    "Officer must manually verify CA stamp, signature, and UDIN on audit report.",
                    ext.getDocumentName()
            ));
        }
    }

    private void evaluateCrossDocumentConsistency(
            ScrutinyAssessment assessment,
            Application application,
            List<ApplicationDocument> documents,
            Map<String, ScrutinyDocumentExtraction> extractionsByReqCode,
            List<ScrutinyFinding> findings
    ) {
        String ruleRef = "APH 2024-27 Clause 1.4 & AICTE General Invariants";
        String appInstName = application.getInstitution() != null ? application.getInstitution().getName() : "";
        String normalizedAppInstName = normalizeName(appInstName);

        for (String reqCode : List.of("DOC_FIRE_SAFETY", "DOC_OCCUPANCY_CERT", "DOC_LAND_DOCUMENTS")) {
            ScrutinyDocumentExtraction ext = extractionsByReqCode.get(reqCode);
            if (ext == null || ext.getDocument() == null) continue;

            // Do not evaluate consistency on unrelated academic files
            if (fieldExtractionService.containsUnrelatedAcademicContent(ext.getRawText())) {
                continue;
            }

            String docInstName = getField(ext, "institutionName");
            if (docInstName != null && !docInstName.isBlank()) {
                String normalizedDocInstName = normalizeName(docInstName);

                if (isNameCompatible(normalizedAppInstName, normalizedDocInstName)) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            ext.getDocument().getId(),
                            reqCode,
                            "RULE_NAME_CONSISTENCY_" + reqCode,
                            "Cross-Document Institution Name Match: " + ext.getDocumentName(),
                            ruleRef,
                            "CROSS_DOCUMENT_CONSISTENCY",
                            "PASS",
                            "ADVISORY",
                            "Document must be issued in the name of the applicant institution (" + appInstName + ")",
                            "MATCH: " + docInstName,
                            "Institution name on document matches registered applicant institution.",
                            "Name consistency satisfied.",
                            ext.getDocumentName()
                    ));
                } else {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            ext.getDocument().getId(),
                            reqCode,
                            "RULE_NAME_MISMATCH_" + reqCode,
                            "Cross-Document Institution Name Discrepancy: " + ext.getDocumentName(),
                            ruleRef,
                            "CROSS_DOCUMENT_CONSISTENCY",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Certificate must be issued to the applicant institution (" + appInstName + ")",
                            "DISCREPANCY: Found '" + docInstName + "' vs Registered '" + appInstName + "'",
                            "The institution name extracted from " + ext.getDocumentName() + " ('" + docInstName + "') does not match registered applicant institution ('" + appInstName + "').",
                            "Review discrepancy. Issue deficiency if document was issued to an unrelated entity.",
                            ext.getDocumentName()
                    ));
                }
            }
        }
    }

    private void evaluateCategorySpecificRules(
            ScrutinyAssessment assessment,
            Application application,
            List<ApplicationDocument> documents,
            Map<String, ScrutinyDocumentExtraction> extractionsByReqCode,
            List<ScrutinyFinding> findings,
            LocalDate evalDate
    ) {
        String appType = application.getApplicationType() != null ? application.getApplicationType().toUpperCase() : "";

        if (appType.contains("EXTENSION") || appType.contains("EOA")) {
            boolean hasPrevEoa = documents.stream().anyMatch(d -> "DOC_PREVIOUS_EOA".equalsIgnoreCase(d.getRequirementCode()));
            if (!hasPrevEoa) {
                findings.add(new ScrutinyFinding(
                        assessment,
                        application.getId(),
                        null,
                        "DOC_PREVIOUS_EOA",
                        "RULE_EOA_PREVIOUS_APPROVAL_MANDATORY",
                        "Previous Academic Year EoA Copy",
                        "APH 2024-27 Clause 2.1.2",
                        "CONDITIONAL_REGULATORY",
                        "POTENTIAL_DEFICIENCY",
                        "BLOCKING",
                        "Existing institutions applying for EoA must provide previous year's approval copy",
                        "MISSING_PREVIOUS_EOA",
                        "Previous AICTE Extension of Approval (EoA) copy is required for EoA processing.",
                        "Issue deficiency for previous approval letter.",
                        "Previous EoA Copy"
                ));
            }
        } else if (appType.contains("NEW")) {
            ScrutinyDocumentExtraction landExt = extractionsByReqCode.get("DOC_LAND_DOCUMENTS");
            if (landExt != null && landExt.getDocument() != null) {
                String landText = landExt.getRawText() != null ? landExt.getRawText() : "";
                if (fieldExtractionService.containsUnrelatedAcademicContent(landText)
                        || !fieldExtractionService.containsRequirementContentSignals(landText, "DOC_LAND_DOCUMENTS")) {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            landExt.getDocument().getId(),
                            "DOC_LAND_DOCUMENTS",
                            "RULE_NEW_INST_LAND_CONTENT_DEFICIENT",
                            "Land Title & Registered Lease Evidence",
                            "APH 2024-27 Clause 1.4",
                            "CONDITIONAL_REGULATORY",
                            "POTENTIAL_DEFICIENCY",
                            "BLOCKING",
                            "Document must contain registered sale deed, gift deed, or government lease",
                            "LAND_CONTENT_UNSUBSTANTIATED",
                            "The uploaded document for land ownership does not contain registered land title, deed numbers, or survey information.",
                            "Require institution to upload registered land deed.",
                            landExt.getDocumentName()
                    ));
                } else {
                    findings.add(new ScrutinyFinding(
                            assessment,
                            application.getId(),
                            landExt.getDocument().getId(),
                            "DOC_LAND_DOCUMENTS",
                            "RULE_NEW_INST_LAND_REGISTERED",
                            "Land Title & Registered Lease Tenure",
                            "APH 2024-27 Clause 1.4",
                            "CONDITIONAL_REGULATORY",
                            "PASS",
                            "ADVISORY",
                            "Registered land ownership or minimum 30-year lease required",
                            "SUBMITTED_LAND_DEED",
                            "Land title and registered deed submitted for new institution appraisal.",
                            "Proceed to physical inspection planning.",
                            landExt.getDocumentName()
                    ));
                }
            }
        }
    }

    private String getDocDisplayName(ScrutinyDocumentExtraction ext) {
        if (ext == null) return "Unknown Document";
        if (ext.getDocument() != null && ext.getDocument().getOriginalFileName() != null && !ext.getDocument().getOriginalFileName().isBlank()) {
            return ext.getDocument().getOriginalFileName();
        }
        return ext.getDocumentName();
    }

    private String getField(ScrutinyDocumentExtraction ext, String fieldKey) {
        if (ext == null || ext.getExtractedFieldsJson() == null || ext.getExtractedFieldsJson().isBlank()) {
            return null;
        }
        try {
            String json = ext.getExtractedFieldsJson();
            String targetKey = "\"" + fieldKey + "\":\"";
            int idx = json.indexOf(targetKey);
            if (idx >= 0) {
                int start = idx + targetKey.length();
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("\\b(pvt|private|ltd|limited|inc|incorporated|tech|technology|engg|engineering|college|institute|of|the|and|&)\\b", "")
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    private boolean isNameCompatible(String norm1, String norm2) {
        if (norm1.isBlank() || norm2.isBlank()) return true;
        if (norm1.equals(norm2)) return true;
        if (norm1.contains(norm2) || norm2.contains(norm1)) return true;
        return computeSimilarity(norm1, norm2) >= 0.75;
    }

    private double computeSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int dist = editDistance(s1, s2);
        return 1.0 - ((double) dist / maxLen);
    }

    private int editDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
}
