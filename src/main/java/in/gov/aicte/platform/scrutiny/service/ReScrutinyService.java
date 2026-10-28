package in.gov.aicte.platform.scrutiny.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;
import in.gov.aicte.platform.scrutiny.ocr.DocumentFieldExtractionService;
import in.gov.aicte.platform.scrutiny.ocr.DocumentTextExtractionService;
import in.gov.aicte.platform.scrutiny.ocr.ExtractedDocumentData;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyAssessmentRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDeficiencyRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDocumentExtractionRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyRescrutinyComparisonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReScrutinyService {

    private static final Logger log = LoggerFactory.getLogger(ReScrutinyService.class);

    private final ScrutinyAssessmentRepository assessmentRepository;
    private final ScrutinyDeficiencyRepository deficiencyRepository;
    private final ScrutinyDocumentExtractionRepository extractionRepository;
    private final ScrutinyRescrutinyComparisonRepository comparisonRepository;
    private final DocumentTextExtractionService textExtractionService;
    private final DocumentFieldExtractionService fieldExtractionService;
    private final MinioStorageService minioStorageService;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public ReScrutinyService(
            ScrutinyAssessmentRepository assessmentRepository,
            ScrutinyDeficiencyRepository deficiencyRepository,
            ScrutinyDocumentExtractionRepository extractionRepository,
            ScrutinyRescrutinyComparisonRepository comparisonRepository,
            DocumentTextExtractionService textExtractionService,
            DocumentFieldExtractionService fieldExtractionService,
            MinioStorageService minioStorageService,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper
    ) {
        this.assessmentRepository = assessmentRepository;
        this.deficiencyRepository = deficiencyRepository;
        this.extractionRepository = extractionRepository;
        this.comparisonRepository = comparisonRepository;
        this.textExtractionService = textExtractionService;
        this.fieldExtractionService = fieldExtractionService;
        this.minioStorageService = minioStorageService;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScrutinyAssessment runAutomatedRescrutiny(Application application, String actorId) {
        log.info("Initiating automated re-scrutiny for application: {}", application.getApplicationReference());

        // Technical Requirement: Prevent duplicate assessment cycles caused by repeated button clicks
        Instant recentThreshold = Instant.now().minusSeconds(15);
        Optional<ScrutinyAssessment> recentOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(application.getId());
        if (recentOpt.isPresent() && "RE_SCRUTINY".equals(recentOpt.get().getAssessmentType())
                && recentOpt.get().getCreatedAt() != null && recentOpt.get().getCreatedAt().isAfter(recentThreshold)) {
            log.info("Suppressed duplicate automated re-scrutiny for application: {} within 15-second idempotency window", application.getApplicationReference());
            return recentOpt.get();
        }

        List<ScrutinyDeficiency> deficiencies = deficiencyRepository.findByApplicationIdAndStatus(
                application.getId(), "RESPONSE_SUBMITTED"
        );
        if (deficiencies.isEmpty()) {
            // Also check for any ISSUED deficiencies that have a corrected document revision uploaded
            deficiencies = deficiencyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId()).stream()
                    .filter(d -> d.getCorrectedDocument() != null && !"VERIFIED_RESOLVED".equalsIgnoreCase(d.getStatus()))
                    .collect(Collectors.toList());
        }
        if (deficiencies.isEmpty()) {
            // Check all un-resolved deficiencies
            deficiencies = deficiencyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId()).stream()
                    .filter(d -> !"VERIFIED_RESOLVED".equalsIgnoreCase(d.getStatus()))
                    .collect(Collectors.toList());
        }

        int currentCycle = (int) assessmentRepository.countByApplicationId(application.getId()) + 1;
        String ref = String.format("RE-SCR-%s-%02d", application.getApplicationReference(), currentCycle);

        ScrutinyAssessment reAssessment = new ScrutinyAssessment(
                ref,
                application,
                application.getInstitution() != null ? application.getInstitution().getId() : null,
                "RE_SCRUTINY",
                currentCycle,
                "APH_2024_27_V1",
                (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM_AUTOMATION"
        );
        reAssessment = assessmentRepository.save(reAssessment);

        int rectifiedCount = 0;
        int unresolvedCount = 0;

        for (ScrutinyDeficiency deficiency : deficiencies) {
            ApplicationDocument correctedDoc = deficiency.getCorrectedDocument();
            ApplicationDocument originalDoc = deficiency.getAssociatedDocument();

            String origValue = deficiency.getOriginalEvidenceText();
            String corrValue = "No corrected document file attached; explanation provided: " + deficiency.getInstitutionExplanation();
            String comparisonStatus = "RECTIFIED_PENDING_CONFIRMATION";
            String comparisonSummary = "";
            String remainingIssues = null;

            if (correctedDoc != null) {
                byte[] corrBytes = retrieveDocumentBytes(correctedDoc);
                ExtractedDocumentData rawData = textExtractionService.extractText(
                        corrBytes, correctedDoc.getOriginalFileName(), correctedDoc.getContentType()
                );
                ExtractedDocumentData structuredData = fieldExtractionService.extractStructuredFields(
                        rawData, correctedDoc.getRequirementCode(), correctedDoc
                );

                // Persist extraction record
                String fieldsJson;
                try {
                    fieldsJson = objectMapper.writeValueAsString(structuredData.getFields());
                } catch (Exception e) {
                    fieldsJson = "{}";
                }

                ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                        reAssessment,
                        application.getId(),
                        correctedDoc,
                        correctedDoc.getRequirementCode(),
                        correctedDoc.getDocumentName(),
                        correctedDoc.getVersion(),
                        structuredData.getExtractionMethod(),
                        structuredData.getRawText(),
                        fieldsJson,
                        structuredData.getConfidenceScore(),
                        structuredData.getOcrStatus(),
                        structuredData.getTextCharacterCount(),
                        structuredData.getPageCount(),
                        structuredData.getValidationMetric()
                );
                if (structuredData.getErrorMessage() != null) {
                    extraction.setErrorMessage(structuredData.getErrorMessage());
                }
                extractionRepository.save(extraction);

                String rawText = structuredData.getRawText() != null ? structuredData.getRawText() : "";
                String reqCode = deficiency.getRequirementCode();

                // 1. Identical File Hash Detection (Acceptance Test Step 9: Re-running without changes does not produce misleading improvements)
                boolean isSameFileHash = originalDoc != null && originalDoc.getFileHash() != null
                        && correctedDoc.getFileHash() != null
                        && originalDoc.getFileHash().equalsIgnoreCase(correctedDoc.getFileHash());

                if (isSameFileHash) {
                    comparisonStatus = "DEFICIENCY_PERSISTS";
                    corrValue = "Unchanged document content (Identical SHA-256 hash to original deficient v" + originalDoc.getVersion() + ")";
                    comparisonSummary = String.format(
                            "Corrected document revision v%d has identical content (SHA-256: %s) to deficient original submission v%d. No statutory corrections detected.",
                            correctedDoc.getVersion(),
                            correctedDoc.getFileHash().length() > 12 ? correctedDoc.getFileHash().substring(0, 12) + "..." : correctedDoc.getFileHash(),
                            originalDoc.getVersion()
                    );
                    remainingIssues = "Uploaded file content is identical to the original deficient document. Substantive statutory correction is required.";
                    unresolvedCount++;
                } else if ("EXTRACTION_FAILED".equalsIgnoreCase(structuredData.getOcrStatus()) || (structuredData.getTextCharacterCount() != null && structuredData.getTextCharacterCount() == 0)) {
                    // 2. Newly Introduced Issue: Unreadable / corrupt document
                    comparisonStatus = "NEW_DEFICIENCY_DETECTED";
                    corrValue = "Unreadable or corrupt document (0 characters extracted)";
                    comparisonSummary = String.format(
                            "Corrected document revision v%d could not be processed: text extraction failed or document is empty/corrupt.",
                            correctedDoc.getVersion()
                    );
                    remainingIssues = "Newly introduced issue: Corrected file cannot be read or rendered by OCR engine.";
                    unresolvedCount++;
                } else if (rawText.toLowerCase().contains("revoked") || rawText.toLowerCase().contains("cancelled") || rawText.toLowerCase().contains("notice of violation")) {
                    // Newly Introduced Issue: Revocation or violation keywords
                    comparisonStatus = "NEW_DEFICIENCY_DETECTED";
                    corrValue = "Explicit revocation/violation notice detected in document";
                    comparisonSummary = String.format(
                            "Corrected document revision v%d contains explicit revocation or violation notices.",
                            correctedDoc.getVersion()
                    );
                    remainingIssues = "Newly introduced issue: Document indicates revocation or statutory violation.";
                    unresolvedCount++;
                } else if (fieldExtractionService.containsUnrelatedAcademicContent(rawText)) {
                    // 3. Unrelated academic lab manual content
                    comparisonStatus = "DEFICIENCY_PERSISTS";
                    corrValue = "Unrelated academic/lab manual content detected (" + correctedDoc.getOriginalFileName() + ")";
                    comparisonSummary = String.format(
                            "Corrected document revision v%d ('%s') contains academic lab coursework text and does not substantiate statutory certificate. Deficiency persists.",
                            correctedDoc.getVersion(), correctedDoc.getOriginalFileName()
                    );
                    remainingIssues = "Uploaded document is an unrelated coursework/lab file rather than a valid statutory certificate.";
                    unresolvedCount++;
                } else if (!fieldExtractionService.containsRequirementContentSignals(rawText, reqCode)) {
                    // 4. Missing required statutory terminology
                    comparisonStatus = "DEFICIENCY_PERSISTS";
                    corrValue = "Missing statutory terminology for " + reqCode;
                    comparisonSummary = String.format(
                            "Corrected document revision v%d lacks required statutory terminology and authority seals. Deficiency persists.",
                            correctedDoc.getVersion()
                    );
                    remainingIssues = "Statutory certification keywords not found in corrected document.";
                    unresolvedCount++;
                } else if ("DOC_FIRE_SAFETY".equalsIgnoreCase(reqCode)) {
                    // 5. Fire Safety date verification
                    String expiryStr = structuredData.getStringField("expiryDate");
                    corrValue = "Extracted Expiry Date: " + (expiryStr != null ? expiryStr : "Not detected");

                    if (expiryStr != null) {
                        try {
                            LocalDate exp = LocalDate.parse(expiryStr);
                            if (exp.isAfter(LocalDate.now())) {
                                comparisonStatus = "RECTIFIED_PENDING_CONFIRMATION";
                                comparisonSummary = String.format(
                                        "Extracted certificate expiry date has changed from '%s' to '%s' and now satisfies the date-validity check. Reviewer confirmation pending.",
                                        origValue != null ? origValue : "EXPIRED", exp
                                );
                                corrValue = "Extracted Expiry Date: " + exp + " (Valid through " + exp + ")";
                                rectifiedCount++;
                            } else {
                                comparisonStatus = "DEFICIENCY_PERSISTS";
                                comparisonSummary = String.format("Corrected Fire Safety Certificate remains expired (Expiry: %s).", exp);
                                remainingIssues = "Expiry date is still in the past.";
                                unresolvedCount++;
                            }
                        } catch (Exception e) {
                            comparisonStatus = "NEEDS_MANUAL_REVIEW";
                            comparisonSummary = "Corrected document uploaded but date format is ambiguous. Manual verification required.";
                            remainingIssues = "Certificate expiry date format could not be verified automatically.";
                            unresolvedCount++;
                        }
                    } else {
                        comparisonStatus = "NEEDS_MANUAL_REVIEW";
                        comparisonSummary = "Corrected document uploaded; automated expiry extraction inconclusive. Manual review needed.";
                        remainingIssues = "Automated expiry extraction inconclusive. Scrutiny Officer verification required.";
                        unresolvedCount++;
                    }
                } else {
                    // 6. Valid statutory document
                    comparisonStatus = "RECTIFIED_PENDING_CONFIRMATION";
                    comparisonSummary = String.format(
                            "Corrected document revision v%d uploaded with substantiated statutory certification. Ready for officer confirmation.",
                            correctedDoc.getVersion()
                    );
                    corrValue = "Substantiated certificate content verified (v" + correctedDoc.getVersion() + ")";
                    rectifiedCount++;
                }
            } else {
                // Explanation-only rectification
                comparisonStatus = "NEEDS_MANUAL_REVIEW";
                comparisonSummary = "Institutional explanation submitted without new document attachment. Scrutiny Officer manual review required.";
                remainingIssues = "No document attachment provided; officer must evaluate institutional explanation.";
                unresolvedCount++;
            }

            ScrutinyRescrutinyComparison comparison = new ScrutinyRescrutinyComparison(
                    reAssessment,
                    deficiency,
                    originalDoc,
                    originalDoc != null ? originalDoc.getVersion() : deficiency.getAssociatedDocumentVersion(),
                    origValue,
                    correctedDoc,
                    correctedDoc != null ? correctedDoc.getVersion() : (deficiency.getCorrectedDocumentVersion() != null ? deficiency.getCorrectedDocumentVersion() : 2),
                    corrValue,
                    comparisonStatus,
                    comparisonSummary,
                    remainingIssues
            );
            comparisonRepository.save(comparison);
        }

        reAssessment.setTotalChecks(deficiencies.size());
        reAssessment.setPassedChecks(rectifiedCount);
        reAssessment.setDeficiencyChecks(unresolvedCount);
        reAssessment.setSummaryOutcome(unresolvedCount > 0 ? "POTENTIAL_DEFICIENCIES_DETECTED" : "RECTIFIED_PENDING_REVIEW");
        reAssessment.setSummaryNotes(String.format(
                "Automated re-scrutiny completed for cycle %d. %d deficiencies reviewed: %d show apparent rectification, %d require further attention.",
                currentCycle, deficiencies.size(), rectifiedCount, unresolvedCount
        ));
        reAssessment = assessmentRepository.save(reAssessment);

        recordAuditEvent("AUTOMATED_RESCRUTINY_EXECUTED", application, reAssessment, actorId);

        return reAssessment;
    }

    private byte[] retrieveDocumentBytes(ApplicationDocument doc) {
        if (doc == null) return new byte[0];
        try {
            if (doc.getStoragePath() != null && !doc.getStoragePath().isBlank()) {
                try (InputStream is = minioStorageService.downloadDocument(doc.getStoragePath())) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception e) {
            log.warn("MinIO download failed for {}: {}", doc.getDocumentName(), e.getMessage());
        }
        return new byte[0];
    }

    private void recordAuditEvent(String eventType, Application app, ScrutinyAssessment assessment, String actorId) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("assessmentReference", assessment.getAssessmentReference());
            meta.put("applicationReference", app.getApplicationReference());
            meta.put("cycleNumber", assessment.getCycleNumber());
            meta.put("totalChecks", assessment.getTotalChecks());

            AuditEvent event = new AuditEvent(
                    eventType,
                    actorId != null ? actorId : "SYSTEM",
                    "SCRUTINY_ASSESSMENT",
                    assessment.getId().toString(),
                    objectMapper.writeValueAsString(meta)
            );
            auditEventRepository.save(event);
        } catch (Exception ignored) {
        }
    }
}
