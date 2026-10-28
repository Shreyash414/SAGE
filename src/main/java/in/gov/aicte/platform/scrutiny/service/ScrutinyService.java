package in.gov.aicte.platform.scrutiny.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.scrutiny.dto.IssueDeficienciesRequest;
import in.gov.aicte.platform.scrutiny.dto.PreliminaryScrutinyReportResponse;
import in.gov.aicte.platform.scrutiny.dto.RescrutinyComparisonResponse;
import in.gov.aicte.platform.scrutiny.dto.ReviewDeficiencyRequest;
import in.gov.aicte.platform.scrutiny.dto.ReviewFindingRequest;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyAssessmentResponse;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyDeficiencyResponse;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyDocumentExtractionResponse;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyFindingResponse;
import in.gov.aicte.platform.scrutiny.dto.SubmitDeficiencyResponsesRequest;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;
import in.gov.aicte.platform.scrutiny.ocr.DocumentFieldExtractionService;
import in.gov.aicte.platform.scrutiny.ocr.DocumentTextExtractionService;
import in.gov.aicte.platform.scrutiny.ocr.ExtractedDocumentData;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyAssessmentRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDeficiencyRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDocumentExtractionRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyFindingRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyRescrutinyComparisonRepository;
import in.gov.aicte.platform.scrutiny.validation.ScrutinyValidationService;
import in.gov.aicte.platform.workflow.WorkflowTransition;
import in.gov.aicte.platform.workflow.WorkflowTransitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScrutinyService {

    private static final Logger log = LoggerFactory.getLogger(ScrutinyService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final ScrutinyAssessmentRepository assessmentRepository;
    private final ScrutinyDocumentExtractionRepository extractionRepository;
    private final ScrutinyFindingRepository findingRepository;
    private final ScrutinyDeficiencyRepository deficiencyRepository;
    private final ScrutinyRescrutinyComparisonRepository comparisonRepository;
    private final DocumentTextExtractionService textExtractionService;
    private final DocumentFieldExtractionService fieldExtractionService;
    private final ScrutinyValidationService validationService;
    private final ReScrutinyService reScrutinyService;
    private final MinioStorageService minioStorageService;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowTransitionRepository workflowTransitionRepository;

    public ScrutinyService(
            ApplicationRepository applicationRepository,
            ApplicationDocumentRepository documentRepository,
            ScrutinyAssessmentRepository assessmentRepository,
            ScrutinyDocumentExtractionRepository extractionRepository,
            ScrutinyFindingRepository findingRepository,
            ScrutinyDeficiencyRepository deficiencyRepository,
            ScrutinyRescrutinyComparisonRepository comparisonRepository,
            DocumentTextExtractionService textExtractionService,
            DocumentFieldExtractionService fieldExtractionService,
            ScrutinyValidationService validationService,
            ReScrutinyService reScrutinyService,
            MinioStorageService minioStorageService,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper
    ) {
        this(applicationRepository, documentRepository, assessmentRepository, extractionRepository,
                findingRepository, deficiencyRepository, comparisonRepository, textExtractionService,
                fieldExtractionService, validationService, reScrutinyService, minioStorageService,
                auditEventRepository, objectMapper, null);
    }

    @Autowired
    public ScrutinyService(
            ApplicationRepository applicationRepository,
            ApplicationDocumentRepository documentRepository,
            ScrutinyAssessmentRepository assessmentRepository,
            ScrutinyDocumentExtractionRepository extractionRepository,
            ScrutinyFindingRepository findingRepository,
            ScrutinyDeficiencyRepository deficiencyRepository,
            ScrutinyRescrutinyComparisonRepository comparisonRepository,
            DocumentTextExtractionService textExtractionService,
            DocumentFieldExtractionService fieldExtractionService,
            ScrutinyValidationService validationService,
            ReScrutinyService reScrutinyService,
            MinioStorageService minioStorageService,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) WorkflowTransitionRepository workflowTransitionRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.assessmentRepository = assessmentRepository;
        this.extractionRepository = extractionRepository;
        this.findingRepository = findingRepository;
        this.deficiencyRepository = deficiencyRepository;
        this.comparisonRepository = comparisonRepository;
        this.textExtractionService = textExtractionService;
        this.fieldExtractionService = fieldExtractionService;
        this.validationService = validationService;
        this.reScrutinyService = reScrutinyService;
        this.minioStorageService = minioStorageService;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.workflowTransitionRepository = workflowTransitionRepository;
    }

    @Transactional
    public PreliminaryScrutinyReportResponse runAutomatedScrutiny(Long applicationId, String actorId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        log.info("Running automated preliminary scrutiny assessment for: {}", application.getApplicationReference());

        List<ApplicationDocument> documents = documentRepository.findByApplicationIdOrderByUploadedAtAsc(applicationId);
        Optional<ScrutinyAssessment> latestOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);

        ScrutinyAssessment assessment;
        boolean documentsUnchanged = false;

        if (latestOpt.isPresent()) {
            ScrutinyAssessment latestAssessment = latestOpt.get();
            List<ScrutinyDocumentExtraction> prevExtractions = extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(latestAssessment.getId());
            if (prevExtractions.size() == documents.size() && !documents.isEmpty()) {
                Map<Long, String> prevDocs = prevExtractions.stream()
                        .filter(e -> e.getDocument() != null && e.getDocument().getId() != null)
                        .collect(Collectors.toMap(
                                e -> e.getDocument().getId(),
                                e -> e.getDocument().getFileHash() != null ? e.getDocument().getFileHash() : "",
                                (a, b) -> a
                        ));
                documentsUnchanged = documents.stream().allMatch(d ->
                        d.getId() != null && prevDocs.containsKey(d.getId()) && Objects.equals(prevDocs.get(d.getId()), d.getFileHash())
                );
            }
        }

        if (documentsUnchanged && latestOpt.isPresent()) {
            assessment = latestOpt.get();
            log.info("Re-evaluating assessment {} for unchanged documents (preventing finding accumulation)", assessment.getAssessmentReference());
        } else {
            int currentCycle = (int) assessmentRepository.countByApplicationId(applicationId) + 1;
            String ref = String.format("SCR-%s-%02d", application.getApplicationReference(), currentCycle);

            assessment = new ScrutinyAssessment(
                    ref,
                    application,
                    application.getInstitution() != null ? application.getInstitution().getId() : null,
                    "PRELIMINARY_SCRUTINY",
                    currentCycle,
                    "APH_2024_27_V1",
                    (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM_AUTOMATION"
            );
            assessment = assessmentRepository.save(assessment);
        }

        Map<String, ScrutinyDocumentExtraction> extractionsByReqCode = new LinkedHashMap<>();
        if (documentsUnchanged) {
            List<ScrutinyDocumentExtraction> existing = extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId());
            for (ScrutinyDocumentExtraction ext : existing) {
                if (ext.getRequirementCode() != null) {
                    if ("UNAVAILABLE".equalsIgnoreCase(ext.getOcrStatus()) && textExtractionService.isOcrAvailable()) {
                        log.info("OCR engine is now available. Evicting stale UNAVAILABLE extraction for reqCode: {}", ext.getRequirementCode());
                        extractionRepository.delete(ext);
                    } else {
                        extractionsByReqCode.put(ext.getRequirementCode(), ext);
                    }
                }
            }
        }

        // Process each uploaded document that lacks an active extraction
        for (ApplicationDocument doc : documents) {
            if (!extractionsByReqCode.containsKey(doc.getRequirementCode())) {
                byte[] docBytes = downloadBytes(doc);
                ExtractedDocumentData rawData = textExtractionService.extractText(
                        docBytes, doc.getOriginalFileName(), doc.getContentType()
                );
                ExtractedDocumentData structuredData = fieldExtractionService.extractStructuredFields(
                        rawData, doc.getRequirementCode(), doc
                );

                String fieldsJson;
                try {
                    fieldsJson = objectMapper.writeValueAsString(structuredData.getFields());
                } catch (Exception e) {
                    fieldsJson = "{}";
                }

                ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                        assessment,
                        applicationId,
                        doc,
                        doc.getRequirementCode(),
                        doc.getDocumentName(),
                        doc.getVersion(),
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
                extraction = extractionRepository.save(extraction);
                extractionsByReqCode.put(doc.getRequirementCode(), extraction);
            }
        }

        // Execute regulatory rules engine
        List<ScrutinyFinding> evaluatedFindings = validationService.evaluateScrutiny(
                assessment, application, documents, extractionsByReqCode, LocalDate.now()
        );

        List<ScrutinyFinding> findings;
        if (documentsUnchanged && latestOpt.isPresent()) {
            List<ScrutinyFinding> existingFindings = findingRepository.findByAssessmentIdOrderByIdAsc(assessment.getId());
            Map<String, ScrutinyFinding> existingByKey = existingFindings.stream().collect(Collectors.toMap(
                    f -> f.getRuleCode() + ":" + (f.getDocumentId() != null ? f.getDocumentId() : "NODOC"),
                    f -> f,
                    (a, b) -> a
            ));

            List<ScrutinyFinding> toSave = new ArrayList<>();
            for (ScrutinyFinding eval : evaluatedFindings) {
                String key = eval.getRuleCode() + ":" + (eval.getDocumentId() != null ? eval.getDocumentId() : "NODOC");
                ScrutinyFinding existing = existingByKey.get(key);
                if (existing != null) {
                    existing.setOutcome(eval.getOutcome());
                    existing.setSeverity(eval.getSeverity());
                    existing.setExpectedCondition(eval.getExpectedCondition());
                    existing.setActualValue(eval.getActualValue());
                    existing.setExplanation(eval.getExplanation());
                    existing.setRecommendedAction(eval.getRecommendedAction());
                    existing.setDocumentName(eval.getDocumentName());
                    existing.setFindingType(eval.getFindingType());
                    existing.setRuleName(eval.getRuleName());
                    existing.setRuleReference(eval.getRuleReference());
                    toSave.add(existing);
                } else {
                    toSave.add(eval);
                }
            }
            findings = findingRepository.saveAll(toSave);
        } else {
            findings = findingRepository.saveAll(evaluatedFindings);
        }

        // Calculate summary metrics
        int totalChecks = findings.size();
        int passedChecks = (int) findings.stream().filter(f -> "PASS".equalsIgnoreCase(f.getOutcome())).count();
        int passedStatutoryChecks = (int) findings.stream()
                .filter(f -> "PASS".equalsIgnoreCase(f.getOutcome()) && !"TECHNICAL_VALIDATION".equalsIgnoreCase(f.getFindingType()) && !"TECHNICAL_FORMAT".equalsIgnoreCase(f.getFindingType()))
                .count();
        int passedTechnicalChecks = (int) findings.stream()
                .filter(f -> "PASS".equalsIgnoreCase(f.getOutcome()) && ("TECHNICAL_VALIDATION".equalsIgnoreCase(f.getFindingType()) || "TECHNICAL_FORMAT".equalsIgnoreCase(f.getFindingType())))
                .count();
        int deficiencyChecks = (int) findings.stream().filter(f -> "POTENTIAL_DEFICIENCY".equalsIgnoreCase(f.getOutcome())).count();
        int reviewChecks = (int) findings.stream().filter(f -> "NEEDS_MANUAL_REVIEW".equalsIgnoreCase(f.getOutcome())).count();

        assessment.setTotalChecks(totalChecks);
        assessment.setPassedChecks(passedChecks);
        assessment.setDeficiencyChecks(deficiencyChecks);
        assessment.setReviewChecks(reviewChecks);
        assessment.setStatus("COMPLETED");

        if (deficiencyChecks > 0) {
            assessment.setSummaryOutcome("POTENTIAL_DEFICIENCIES_DETECTED");
        } else if (reviewChecks > 0) {
            assessment.setSummaryOutcome("NEEDS_MANUAL_REVIEW");
        } else {
            assessment.setSummaryOutcome("PASSED_CLEAN");
        }

        assessment.setSummaryNotes(String.format(
                "Automated scrutiny run completed: %d total checks (%d substantive statutory PASS, %d technical file PASS), %d potential deficiencies identified, %d require officer manual review.",
                totalChecks, passedStatutoryChecks, passedTechnicalChecks, deficiencyChecks, reviewChecks
        ));
        assessment = assessmentRepository.save(assessment);

        recordAuditEvent("AUTOMATED_SCRUTINY_EXECUTED", application, assessment, actorId);

        return buildReportResponse(application, assessment, findings);
    }

    @Transactional(readOnly = true)
    public PreliminaryScrutinyReportResponse getLatestReport(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        Optional<ScrutinyAssessment> latestOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (latestOpt.isEmpty()) {
            return new PreliminaryScrutinyReportResponse(
                    application.getId(),
                    application.getApplicationReference(),
                    application.getInstitution() != null ? application.getInstitution().getName() : "Unassigned Institution",
                    application.getApplicationType(),
                    application.getAcademicYear(),
                    "APH_2024_27_V1",
                    0,
                    "NOT_EVALUATED",
                    "PENDING_INITIAL_RUN",
                    null,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    false,
                    List.of("Automated scrutiny assessment has not been executed yet. Click 'Run Automated Scrutiny'."),
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        ScrutinyAssessment assessment = latestOpt.get();
        List<ScrutinyFinding> findings = findingRepository.findByAssessmentIdOrderByIdAsc(assessment.getId());
        return buildReportResponse(application, assessment, findings);
    }

    @Transactional
    public ScrutinyFindingResponse reviewFinding(Long findingId, ReviewFindingRequest request, String actorId) {
        ScrutinyFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found with id: " + findingId));

        String decision = request.decision() != null ? request.decision().trim() : "PENDING";
        finding.setOfficerDecision(decision);
        finding.setOfficerRemarks(request.remarks());
        finding.setReviewedBy(actorId != null ? actorId : "Scrutiny Officer");
        finding.setReviewedAt(Instant.now());
        finding = findingRepository.save(finding);

        // If confirmed as deficiency, automatically create or link a ScrutinyDeficiency entity
        if ("CONFIRMED_DEFICIENCY".equalsIgnoreCase(decision) || "CONFIRMED".equalsIgnoreCase(decision)) {
            createOrUpdateDeficiencyFromFinding(finding, request, actorId);
        } else if ("DISMISSED".equalsIgnoreCase(decision) || "MANUAL_VERIFIED".equalsIgnoreCase(decision)) {
            Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(finding.getId());
            if (existingOpt.isPresent()) {
                ScrutinyDeficiency def = existingOpt.get();
                if ("CONFIRMED".equalsIgnoreCase(def.getStatus())) {
                    def.setStatus("DISMISSED");
                    deficiencyRepository.save(def);
                }
            }
        } else if ("REQUEST_MANUAL_VERIFICATION".equalsIgnoreCase(decision)) {
            Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(finding.getId());
            if (existingOpt.isPresent()) {
                ScrutinyDeficiency def = existingOpt.get();
                if ("CONFIRMED".equalsIgnoreCase(def.getStatus())) {
                    def.setStatus("MANUAL_VERIFICATION_REQUIRED");
                    deficiencyRepository.save(def);
                }
            }
        }

        // If applyToAllDuplicates is true and this is a duplicate upload finding, synchronize all sibling duplicate findings
        if (Boolean.TRUE.equals(request.applyToAllDuplicates()) && finding.getRuleCode() != null && finding.getRuleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_")) {
            ApplicationDocument currentDoc = finding.getDocumentId() != null ? documentRepository.findById(finding.getDocumentId()).orElse(null) : null;
            if (currentDoc != null && currentDoc.getFileHash() != null && finding.getAssessment() != null) {
                List<ScrutinyFinding> siblingFindings = findingRepository.findByAssessmentIdOrderByIdAsc(finding.getAssessment().getId());
                for (ScrutinyFinding sibling : siblingFindings) {
                    if (!sibling.getId().equals(finding.getId()) && sibling.getRuleCode() != null && sibling.getRuleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_")) {
                        ApplicationDocument sibDoc = sibling.getDocumentId() != null ? documentRepository.findById(sibling.getDocumentId()).orElse(null) : null;
                        if (sibDoc != null && currentDoc.getFileHash().equals(sibDoc.getFileHash())) {
                            sibling.setOfficerDecision(decision);
                            sibling.setOfficerRemarks(request.remarks());
                            sibling.setReviewedBy(actorId != null ? actorId : "Scrutiny Officer");
                            sibling.setReviewedAt(Instant.now());
                            findingRepository.save(sibling);

                            if ("CONFIRMED_DEFICIENCY".equalsIgnoreCase(decision) || "CONFIRMED".equalsIgnoreCase(decision)) {
                                createOrUpdateDeficiencyFromFinding(sibling, request, actorId);
                            } else if ("DISMISSED".equalsIgnoreCase(decision) || "MANUAL_VERIFIED".equalsIgnoreCase(decision)) {
                                Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(sibling.getId());
                                if (existingOpt.isPresent()) {
                                    ScrutinyDeficiency def = existingOpt.get();
                                    if ("CONFIRMED".equalsIgnoreCase(def.getStatus())) {
                                        def.setStatus("DISMISSED");
                                        deficiencyRepository.save(def);
                                    }
                                }
                            } else if ("REQUEST_MANUAL_VERIFICATION".equalsIgnoreCase(decision)) {
                                Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(sibling.getId());
                                if (existingOpt.isPresent()) {
                                    ScrutinyDeficiency def = existingOpt.get();
                                    if ("CONFIRMED".equalsIgnoreCase(def.getStatus())) {
                                        def.setStatus("MANUAL_VERIFICATION_REQUIRED");
                                        deficiencyRepository.save(def);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        recordAuditEvent("SCRUTINY_FINDING_REVIEWED", finding.getApplicationId(), "FINDING", findingId.toString(), actorId);

        return ScrutinyFindingResponse.from(finding);
    }

    @Transactional
    public List<ScrutinyDeficiencyResponse> issueFormalDeficiencies(
            Long applicationId,
            IssueDeficienciesRequest request,
            String actorId
    ) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        List<ScrutinyFinding> findingsToIssue;
        if (request.findingIds() != null && !request.findingIds().isEmpty()) {
            List<ScrutinyFinding> raw = findingRepository.findAllById(request.findingIds());
            // Requirement 7: Prevent issuing dismissed findings or passed checks
            List<ScrutinyFinding> invalidFindings = raw.stream()
                    .filter(f -> "DISMISSED".equalsIgnoreCase(f.getOfficerDecision()) || "PASS".equalsIgnoreCase(f.getOutcome()))
                    .toList();
            if (!invalidFindings.isEmpty()) {
                String codes = invalidFindings.stream().map(ScrutinyFinding::getRequirementCode).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Cannot issue formal deficiency notice for dismissed or passed finding(s): [" + codes + "]");
            }

            // For explicitly selected findings that were pending officer review, confirm them with this formal issuance
            for (ScrutinyFinding f : raw) {
                if ("PENDING".equalsIgnoreCase(f.getOfficerDecision()) || f.getOfficerDecision() == null) {
                    f.setOfficerDecision("CONFIRMED_DEFICIENCY");
                    f.setReviewedBy(actorId != null ? actorId : "Scrutiny Officer");
                    f.setReviewedAt(Instant.now());
                    if (f.getOfficerRemarks() == null || f.getOfficerRemarks().isBlank()) {
                        f.setOfficerRemarks("Confirmed upon issuing formal statutory notice: " + request.effectiveObservations());
                    }
                    findingRepository.save(f);
                }
            }
            findingsToIssue = raw;
        } else {
            // Pick only officer-confirmed findings for latest assessment (prevent automatic notice issuance without review)
            Optional<ScrutinyAssessment> latestOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
            if (latestOpt.isPresent()) {
                findingsToIssue = findingRepository.findByAssessmentIdOrderByIdAsc(latestOpt.get().getId()).stream()
                        .filter(f -> "CONFIRMED_DEFICIENCY".equalsIgnoreCase(f.getOfficerDecision())
                                || "CONFIRMED".equalsIgnoreCase(f.getOfficerDecision()))
                        .collect(Collectors.toList());
            } else {
                findingsToIssue = Collections.emptyList();
            }
        }

        if (findingsToIssue.isEmpty()) {
            throw new IllegalStateException("Cannot issue formal deficiency notice: No confirmed deficiencies selected. Officer review and confirmation is required before issuing a formal notice.");
        }

        // Requirement 12: Idempotency check to prevent duplicate notices from accidental repeated clicks
        Instant recentThreshold = Instant.now().minusSeconds(15);
        boolean allRecentlyIssued = findingsToIssue.stream().allMatch(f -> {
            Optional<ScrutinyDeficiency> existing = deficiencyRepository.findByFindingId(f.getId());
            return existing.isPresent() && "ISSUED".equals(existing.get().getStatus()) &&
                    existing.get().getIssuedAt() != null && existing.get().getIssuedAt().isAfter(recentThreshold);
        });
        if (allRecentlyIssued) {
            log.info("Suppressed duplicate formal notice issuance for applicationId: {} within idempotency window", applicationId);
            return deficiencyRepository.findByApplicationIdAndStatus(applicationId, "ISSUED").stream()
                    .map(ScrutinyDeficiencyResponse::from).toList();
        }

        List<ScrutinyDeficiency> createdList = new ArrayList<>();
        int count = (int) deficiencyRepository.countByApplicationIdAndStatus(applicationId, "ISSUED");

        LocalDate dueDate = request.effectiveDueDate();
        String generalObs = request.effectiveObservations();

        for (ScrutinyFinding f : findingsToIssue) {
            Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(f.getId());
            ScrutinyDeficiency deficiency;

            if (existingOpt.isPresent()) {
                deficiency = existingOpt.get();
                deficiency.setStatus("ISSUED");
                deficiency.setDueDate(dueDate);
                if (generalObs != null && !generalObs.isBlank()) {
                    deficiency.setOfficerObservation(generalObs);
                }
                deficiency.setIssuedBy(actorId != null ? actorId : "B. Singh (Scrutiny Officer)");
                deficiency.setIssuedAt(Instant.now());
            } else {
                count++;
                String defRef = String.format("DEF-%s-%02d", application.getApplicationReference(), count);

                ApplicationDocument doc = null;
                if (f.getDocumentId() != null) {
                    doc = documentRepository.findById(f.getDocumentId()).orElse(null);
                }

                deficiency = new ScrutinyDeficiency(
                        defRef,
                        application,
                        f.getAssessment(),
                        f,
                        f.getRequirementCode(),
                        f.getRuleName(),
                        f.getExplanation(),
                        f.getRuleReference(),
                        doc,
                        doc != null ? doc.getVersion() : 1,
                        f.getActualValue(),
                        generalObs != null ? generalObs : f.getRecommendedAction(),
                        true,
                        dueDate,
                        actorId != null ? actorId : "B. Singh (Scrutiny Officer)"
                );
                deficiency.setStatus("ISSUED");
                deficiency.setIssuedAt(Instant.now());
            }
            createdList.add(deficiencyRepository.save(deficiency));
        }

        // Sync application deficiencies JSON so existing timeline and banners reflect official deficiencies
        List<String> defDescriptions = createdList.stream()
                .map(d -> d.getTitle() + ": " + d.getDescription())
                .collect(Collectors.toList());
        try {
            application.setDeficienciesJson(objectMapper.writeValueAsString(defDescriptions));
        } catch (Exception ignored) {
        }

        // Return application for correction if requested
        if (Boolean.TRUE.equals(request.returnApplicationForCorrection())) {
            String fromStage = application.getCurrentStage() != null ? application.getCurrentStage() : "Scrutiny";
            String fromStatus = application.getStatus() != null ? application.getStatus() : "In progress";

            application.setCurrentStage("Re-scrutiny");
            application.setStatus("Returned");
            application.setOwnerName("Institution Representative");
            application.setOwnerId("USR-INST-01");
            if (generalObs != null && !generalObs.isBlank()) {
                application.setObservations(generalObs);
            }
            application = applicationRepository.save(application);

            if (workflowTransitionRepository != null) {
                try {
                    WorkflowTransition transition = new WorkflowTransition(
                            application,
                            "RETURN_FOR_CORRECTION",
                            fromStage,
                            "Re-scrutiny",
                            fromStatus,
                            "Returned",
                            actorId != null ? actorId : "USR-SO-01",
                            actorId != null ? actorId : "B. Singh (Scrutiny Officer)",
                            "SCRUTINY_OFFICER",
                            request.formalNoticeTitle() != null ? request.formalNoticeTitle() : "Notice of Statutory Deficiencies for AICTE Approval",
                            generalObs,
                            application.getDeficienciesJson()
                    );
                    workflowTransitionRepository.save(transition);
                } catch (Exception e) {
                    log.warn("Could not record workflow transition for deficiency notice: {}", e.getMessage());
                }
            }
        } else {
            applicationRepository.save(application);
        }

        recordAuditEvent("FORMAL_DEFICIENCIES_ISSUED", application, null, actorId);

        return createdList.stream().map(ScrutinyDeficiencyResponse::from).toList();
    }

    @Transactional
    public ApplicationDocument uploadCorrectedDocument(
            Long applicationId,
            Long deficiencyId,
            MultipartFile file,
            String actorId
    ) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
        ScrutinyDeficiency deficiency = deficiencyRepository.findByIdAndApplicationId(deficiencyId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Deficiency not found: " + deficiencyId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        try {
            byte[] bytes = file.getBytes();
            int newVersion = deficiency.getAssociatedDocumentVersion() + 1;
            String reqCode = deficiency.getRequirementCode() != null ? deficiency.getRequirementCode() : "DOC_CORRECTED";

            MinioStorageService.StoredObjectResult stored = minioStorageService.uploadDocument(
                    applicationId, reqCode, newVersion, file.getOriginalFilename(), file.getContentType(), bytes
            );

            // Create new ApplicationDocument revision while strictly preserving original!
            ApplicationDocument correctedDoc = new ApplicationDocument(
                    application,
                    reqCode,
                    deficiency.getTitle() + " (Revision v" + newVersion + ")",
                    file.getOriginalFilename(),
                    file.getOriginalFilename(),
                    stored.storageKey(),
                    file.getSize(),
                    file.getContentType(),
                    stored.sha256Checksum(),
                    newVersion,
                    "CORRECTED_REVISION",
                    actorId != null ? actorId : "Institution Representative"
            );
            correctedDoc = documentRepository.save(correctedDoc);

            // Associate with deficiency
            deficiency.setCorrectedDocument(correctedDoc);
            deficiency.setCorrectedDocumentVersion(newVersion);
            deficiencyRepository.save(deficiency);

            recordAuditEvent("DEFICIENCY_CORRECTION_DOCUMENT_UPLOADED", application, null, actorId);
            return correctedDoc;
        } catch (Exception e) {
            log.error("Failed to upload corrected document: {}", e.getMessage());
            throw new RuntimeException("Failed to upload corrected document revision: " + e.getMessage(), e);
        }
    }

    @Transactional
    public PreliminaryScrutinyReportResponse submitDeficiencyResponses(
            Long applicationId,
            SubmitDeficiencyResponsesRequest request,
            String actorId
    ) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        List<ScrutinyDeficiency> activeDeficiencies = deficiencyRepository.findByApplicationIdAndStatus(
                applicationId, "ISSUED"
        );

        if (activeDeficiencies.isEmpty()) {
            throw new IllegalStateException("No outstanding issued deficiencies found for application: " + applicationId);
        }

        Map<Long, String> explanationsMap = (request != null) ? request.getEffectiveExplanations() : Collections.emptyMap();
        Instant now = Instant.now();
        for (ScrutinyDeficiency def : activeDeficiencies) {
            if (explanationsMap.containsKey(def.getId())) {
                def.setInstitutionExplanation(explanationsMap.get(def.getId()));
            } else if (request != null && request.submissionNote() != null) {
                def.setInstitutionExplanation(request.submissionNote());
            }
            def.setStatus("RESPONSE_SUBMITTED");
            def.setRespondedAt(now);
            deficiencyRepository.save(def);
        }

        // CRITICAL INVARIANT: DO NOT ERASE DEFICIENCIES!
        // Transition application state to Re-scrutiny / In progress, assigned to Scrutiny Officer
        application.setCurrentStage("Re-scrutiny");
        application.setStatus("In progress");
        application.setOwnerName("B. Singh (Scrutiny Officer)");
        application.setOwnerId("USR-SO-01");
        if (request != null && request.submissionNote() != null && !request.submissionNote().isBlank()) {
            application.setObservations(request.submissionNote());
        }
        application = applicationRepository.save(application);

        if (workflowTransitionRepository != null) {
            try {
                WorkflowTransition transition = new WorkflowTransition(
                        application,
                        "SUBMIT_CORRECTIONS",
                        "Re-scrutiny",
                        "Re-scrutiny",
                        "Returned",
                        "In progress",
                        actorId != null ? actorId : "USR-INST-01",
                        "Institution Representative",
                        "INSTITUTION",
                        request != null && request.submissionNote() != null ? request.submissionNote() : "Deficiency rectifications submitted",
                        request != null && request.submissionNote() != null ? request.submissionNote() : null,
                        application.getDeficienciesJson()
                );
                workflowTransitionRepository.save(transition);
            } catch (Exception e) {
                log.warn("Could not record workflow transition for submit corrections: {}", e.getMessage());
            }
        }

        // Run automated re-scrutiny to compare original vs corrected evidence
        reScrutinyService.runAutomatedRescrutiny(application, actorId);

        recordAuditEvent("DEFICIENCY_RESPONSES_SUBMITTED", application, null, actorId);

        return getLatestReport(applicationId);
    }

    @Transactional
    public ScrutinyDeficiencyResponse reviewDeficiency(
            Long deficiencyId,
            ReviewDeficiencyRequest request,
            String actorId
    ) {
        ScrutinyDeficiency deficiency = deficiencyRepository.findById(deficiencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Deficiency not found: " + deficiencyId));

        String decision = request.decision().toUpperCase().trim();
        deficiency.setReviewerDecision(decision);
        deficiency.setReviewerRemarks(request.remarks());

        if ("VERIFIED_RESOLVED".equals(decision) || "RESOLVED".equals(decision)) {
            deficiency.setStatus("VERIFIED_RESOLVED");
            deficiency.setResolvedAt(Instant.now());
        } else {
            deficiency.setStatus("ISSUED");
        }
        deficiency = deficiencyRepository.save(deficiency);

        syncApplicationDeficienciesJson(deficiency.getApplication().getId());

        recordAuditEvent("DEFICIENCY_REVIEW_COMPLETED", deficiency.getApplication(), null, actorId);

        return ScrutinyDeficiencyResponse.from(deficiency);
    }

    @Transactional(readOnly = true)
    public boolean hasUnresolvedBlockingDeficiencies(Long applicationId) {
        long openCount = deficiencyRepository.countByApplicationIdAndBlockingTrueAndStatusIn(
                applicationId, List.of("ISSUED", "RESPONSE_SUBMITTED")
        );
        return openCount > 0;
    }

    private void createOrUpdateDeficiencyFromFinding(ScrutinyFinding f, ReviewFindingRequest req, String actorId) {
        Application app = applicationRepository.findById(f.getApplicationId()).orElse(null);
        if (app == null) return;

        ApplicationDocument doc = null;
        if (f.getDocumentId() != null) {
            doc = documentRepository.findById(f.getDocumentId()).orElse(null);
        }

        String title = (req.customTitle() != null && !req.customTitle().isBlank()) ? req.customTitle() : f.getRuleName();
        String desc = (req.customDescription() != null && !req.customDescription().isBlank()) ? req.customDescription() : f.getExplanation();
        boolean blocking = req.blocking() != null ? req.blocking() : "BLOCKING".equalsIgnoreCase(f.getSeverity());

        Optional<ScrutinyDeficiency> existingOpt = deficiencyRepository.findByFindingId(f.getId());
        ScrutinyDeficiency deficiency;

        if (existingOpt.isPresent()) {
            deficiency = existingOpt.get();
            deficiency.setTitle(title);
            deficiency.setDescription(desc);
            deficiency.setBlocking(blocking);
            if (req.remarks() != null) {
                deficiency.setOfficerObservation(req.remarks());
            }
        } else {
            long count = deficiencyRepository.countByApplicationIdAndStatus(app.getId(), "ISSUED") + 1;
            String defRef = String.format("DEF-%s-%02d", app.getApplicationReference(), count);

            deficiency = new ScrutinyDeficiency(
                    defRef,
                    app,
                    f.getAssessment(),
                    f,
                    f.getRequirementCode(),
                    title,
                    desc,
                    f.getRuleReference(),
                    doc,
                    doc != null ? doc.getVersion() : 1,
                    f.getActualValue(),
                    req.remarks() != null ? req.remarks() : f.getRecommendedAction(),
                    blocking,
                    LocalDate.now().plusDays(10),
                    actorId != null ? actorId : "Scrutiny Officer"
            );
            deficiency.setStatus("CONFIRMED");
        }
        deficiencyRepository.save(deficiency);
    }

    private void syncApplicationDeficienciesJson(Long applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) return;

        List<ScrutinyDeficiency> openList = deficiencyRepository.findByApplicationIdAndStatus(applicationId, "ISSUED");
        if (openList.isEmpty()) {
            application.setDeficienciesJson("[]");
        } else {
            List<String> items = openList.stream().map(d -> d.getTitle() + ": " + d.getDescription()).toList();
            try {
                application.setDeficienciesJson(objectMapper.writeValueAsString(items));
            } catch (Exception ignored) {
            }
        }
        applicationRepository.save(application);
    }

    private PreliminaryScrutinyReportResponse buildReportResponse(
            Application application,
            ScrutinyAssessment assessment,
            List<ScrutinyFinding> findings
    ) {
        List<ScrutinyDeficiency> deficiencies = deficiencyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        List<ScrutinyRescrutinyComparison> comparisons = comparisonRepository.findByAssessmentIdOrderByIdAsc(assessment.getId());

        List<ScrutinyDocumentExtraction> extractions = extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId());
        if (extractions.isEmpty()) {
            extractions = extractionRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId());
        }
        List<ScrutinyDocumentExtractionResponse> extractionDtos = extractions.stream()
                .map(ScrutinyDocumentExtractionResponse::from)
                .toList();

        List<ApplicationDocument> appDocuments = documentRepository.findByApplicationIdOrderByUploadedAtAsc(application.getId());
        Map<Long, ApplicationDocument> docsById = appDocuments.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(ApplicationDocument::getId, d -> d, (a, b) -> a));

        Map<Long, String> docNamesById = appDocuments.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(
                        ApplicationDocument::getId,
                        ApplicationDocument::getDocumentName,
                        (a, b) -> a
                ));

        Map<String, String> docNamesByReqCode = appDocuments.stream()
                .filter(d -> d.getRequirementCode() != null)
                .collect(Collectors.toMap(
                        ApplicationDocument::getRequirementCode,
                        ApplicationDocument::getDocumentName,
                        (a, b) -> a
                ));

        List<ScrutinyFindingResponse> findingDtos = findings.stream().map(f -> {
            String docName = f.getDocumentName();
            if (docName == null || docName.isBlank()) {
                if (f.getDocumentId() != null) {
                    docName = docNamesById.get(f.getDocumentId());
                }
                if (docName == null && f.getRequirementCode() != null) {
                    docName = docNamesByReqCode.get(f.getRequirementCode());
                }
            }
            return ScrutinyFindingResponse.from(f, docName != null ? docName : "Uploaded Statutory Document");
        }).toList();

        List<ScrutinyFindingResponse> consolidatedDtos = consolidateFindings(findingDtos, docsById);
        int totalGroupedChecks = consolidatedDtos.size();
        int groupedPotentialDefs = (int) consolidatedDtos.stream()
                .filter(f -> "POTENTIAL_DEFICIENCY".equalsIgnoreCase(f.outcome()))
                .count();

        List<String> blockingReasons = new ArrayList<>();
        long openDeficiencies = deficiencies.stream()
                .filter(d -> d.isBlocking() && ("ISSUED".equalsIgnoreCase(d.getStatus()) || "RESPONSE_SUBMITTED".equalsIgnoreCase(d.getStatus())))
                .count();

        if (openDeficiencies > 0) {
            blockingReasons.add(String.format("There are %d unresolved blocking deficiencies requiring institution rectification or officer verification.", openDeficiencies));
        }

        long unreviewedPotentialDeficiencies = findings.stream()
                .filter(f -> "POTENTIAL_DEFICIENCY".equalsIgnoreCase(f.getOutcome()) && "PENDING".equalsIgnoreCase(f.getOfficerDecision()))
                .count();

        if (unreviewedPotentialDeficiencies > 0) {
            blockingReasons.add(String.format("There are %d automated potential deficiencies requiring officer confirmation or dismissal.", unreviewedPotentialDeficiencies));
        }

        boolean canComplete = blockingReasons.isEmpty();

        int passedChecks = (int) findings.stream().filter(f -> "PASS".equalsIgnoreCase(f.getOutcome())).count();
        int passedStatutoryChecks = (int) findings.stream()
                .filter(f -> "PASS".equalsIgnoreCase(f.getOutcome()) && !"TECHNICAL_VALIDATION".equalsIgnoreCase(f.getFindingType()) && !"TECHNICAL_FORMAT".equalsIgnoreCase(f.getFindingType()))
                .count();
        int passedTechnicalChecks = (int) findings.stream()
                .filter(f -> "PASS".equalsIgnoreCase(f.getOutcome()) && ("TECHNICAL_VALIDATION".equalsIgnoreCase(f.getFindingType()) || "TECHNICAL_FORMAT".equalsIgnoreCase(f.getFindingType())))
                .count();
        int potentialDefs = (int) findings.stream().filter(f -> "POTENTIAL_DEFICIENCY".equalsIgnoreCase(f.getOutcome())).count();
        int manualReviews = (int) findings.stream().filter(f -> "NEEDS_MANUAL_REVIEW".equalsIgnoreCase(f.getOutcome())).count();
        int processingErrors = (int) findings.stream().filter(f -> "PROCESSING_ERROR".equalsIgnoreCase(f.getOutcome())).count();

        return new PreliminaryScrutinyReportResponse(
                application.getId(),
                application.getApplicationReference(),
                application.getInstitution() != null ? application.getInstitution().getName() : "Unassigned Institution",
                application.getApplicationType(),
                application.getAcademicYear(),
                assessment.getRulesVersion(),
                assessment.getCycleNumber(),
                assessment.getStatus(),
                assessment.getSummaryOutcome(),
                assessment.getCreatedAt(),
                extractions.size(),
                findings.size(),
                totalGroupedChecks,
                passedChecks,
                passedStatutoryChecks,
                passedTechnicalChecks,
                potentialDefs,
                groupedPotentialDefs,
                manualReviews,
                processingErrors,
                canComplete,
                blockingReasons,
                ScrutinyAssessmentResponse.from(assessment),
                consolidatedDtos,
                findingDtos,
                deficiencies.stream().map(ScrutinyDeficiencyResponse::from).toList(),
                comparisons.stream().map(RescrutinyComparisonResponse::from).toList(),
                extractionDtos
        );
    }

    public static String resolveCategoryFriendlyName(String reqCode) {
        if (reqCode == null) return "Statutory Document";
        return switch (reqCode) {
            case "DOC_TRUST_DEED" -> "Trust Deed";
            case "DOC_BUILDING_PLAN" -> "Building Plan";
            case "DOC_FIRE_SAFETY" -> "Fire Safety";
            case "DOC_STRUCTURAL_STABILITY" -> "Structural Stability";
            case "DOC_LAND_DOCUMENTS" -> "Land Documents";
            case "DOC_OCCUPANCY_CERT" -> "Occupancy Certificate";
            case "DOC_AFFILIATING_NOC" -> "Affiliation NOC";
            case "DOC_CORPUS_FUND" -> "Corpus Fund";
            default -> reqCode.replace("DOC_", "").replace("_", " ");
        };
    }

    public List<ScrutinyFindingResponse> consolidateFindings(
            List<ScrutinyFindingResponse> findingDtos,
            Map<Long, ApplicationDocument> docsById
    ) {
        if (findingDtos == null || findingDtos.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScrutinyFindingResponse> duplicateFindings = new ArrayList<>();
        List<ScrutinyFindingResponse> nonDuplicateFindings = new ArrayList<>();

        for (ScrutinyFindingResponse f : findingDtos) {
            boolean isDup = f.ruleCode() != null && f.ruleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_");
            if (isDup) {
                duplicateFindings.add(f);
            } else {
                nonDuplicateFindings.add(f);
            }
        }

        if (duplicateFindings.isEmpty()) {
            return findingDtos;
        }

        // Group duplicate findings by fileHash (or file name if hash missing)
        Map<String, List<ScrutinyFindingResponse>> dupesByHash = new LinkedHashMap<>();
        for (ScrutinyFindingResponse f : duplicateFindings) {
            ApplicationDocument doc = f.documentId() != null ? docsById.get(f.documentId()) : null;
            String key = (doc != null && doc.getFileHash() != null && !doc.getFileHash().isBlank())
                    ? doc.getFileHash()
                    : (f.documentName() != null ? f.documentName() : "UNKNOWN_FILE");
            dupesByHash.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        List<ScrutinyFindingResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<ScrutinyFindingResponse>> entry : dupesByHash.entrySet()) {
            List<ScrutinyFindingResponse> group = entry.getValue();
            if (group.size() <= 1) {
                result.addAll(group);
                continue;
            }

            // Create consolidated finding
            ScrutinyFindingResponse primary = group.get(0);
            List<String> affectedCategories = group.stream()
                    .map(ScrutinyFindingResponse::requirementCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            List<String> affectedCategoryNames = affectedCategories.stream()
                    .map(ScrutinyService::resolveCategoryFriendlyName)
                    .toList();
            List<Long> affectedDocumentIds = group.stream()
                    .map(ScrutinyFindingResponse::documentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            boolean allConfirmed = group.stream().allMatch(f -> "CONFIRMED".equalsIgnoreCase(f.officerDecision()) || "CONFIRMED_DEFICIENCY".equalsIgnoreCase(f.officerDecision()));
            boolean allDismissed = group.stream().allMatch(f -> "DISMISSED".equalsIgnoreCase(f.officerDecision()));
            boolean anyReviewed = group.stream().anyMatch(f -> f.officerDecision() != null && !"PENDING".equalsIgnoreCase(f.officerDecision()));

            String aggregateDecision = allConfirmed ? "CONFIRMED" : (allDismissed ? "DISMISSED" : (anyReviewed ? "PARTIALLY_REVIEWED" : "PENDING"));

            ApplicationDocument primaryDoc = primary.documentId() != null ? docsById.get(primary.documentId()) : null;
            String originalFileName = (primaryDoc != null && primaryDoc.getOriginalFileName() != null)
                    ? primaryDoc.getOriginalFileName()
                    : primary.documentName();

            ScrutinyFindingResponse consolidated = new ScrutinyFindingResponse(
                    primary.id(),
                    primary.assessmentId(),
                    primary.applicationId(),
                    primary.documentId(),
                    originalFileName,
                    "DOC_CONSOLIDATED_DUPLICATE",
                    "RULE_DUPLICATE_DOCUMENT_HASH",
                    "Duplicate Document Detected",
                    "APH 2024-27 Clause 1.2 & Anti-Fraud Regulatory Norms",
                    "DOCUMENT_AUTHENTICITY",
                    "POTENTIAL_DEFICIENCY",
                    "BLOCKING",
                    "Each statutory requirement necessitates an independent certificate from its respective competent authority",
                    "DUPLICATE_FILE_HASH: Identical file uploaded across " + affectedCategoryNames.size() + " categories (" + String.join(", ", affectedCategoryNames) + ")",
                    "Identical file content has been submitted under multiple document categories.",
                    "Review whether independent category-specific evidence is required.",
                    aggregateDecision,
                    primary.officerRemarks(),
                    primary.reviewedBy(),
                    primary.reviewedAt(),
                    primary.createdAt(),
                    true,
                    affectedCategories,
                    affectedCategoryNames,
                    affectedDocumentIds,
                    group
            );

            result.add(consolidated);
        }

        result.addAll(nonDuplicateFindings);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ScrutinyFindingResponse> getFindings(Long applicationId, Long assessmentId, boolean consolidated) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        ScrutinyAssessment assessment;
        if (assessmentId != null) {
            assessment = assessmentRepository.findById(assessmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));
        } else {
            assessment = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElse(null);
        }

        if (assessment == null) {
            return Collections.emptyList();
        }

        List<ScrutinyFinding> findings = findingRepository.findByAssessmentIdOrderByIdAsc(assessment.getId());
        List<ApplicationDocument> docs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(applicationId);
        Map<Long, ApplicationDocument> docsById = docs.stream().filter(d -> d.getId() != null).collect(Collectors.toMap(ApplicationDocument::getId, d -> d, (a, b) -> a));

        List<ScrutinyFindingResponse> dtos = findings.stream().map(f -> {
            String docName = f.getDocumentName();
            if ((docName == null || docName.isBlank()) && f.getDocumentId() != null) {
                ApplicationDocument doc = docsById.get(f.getDocumentId());
                if (doc != null) docName = doc.getDocumentName();
            }
            return ScrutinyFindingResponse.from(f, docName != null ? docName : "Uploaded Statutory Document");
        }).toList();

        if (consolidated) {
            return consolidateFindings(dtos, docsById);
        }
        return dtos;
    }

    private byte[] downloadBytes(ApplicationDocument doc) {
        if (doc == null) return new byte[0];
        try {
            if (doc.getStoragePath() != null && !doc.getStoragePath().isBlank()) {
                try (InputStream is = minioStorageService.downloadDocument(doc.getStoragePath())) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception e) {
            log.warn("MinIO retrieval failed for {}: {}", doc.getDocumentName(), e.getMessage());
        }
        return new byte[0];
    }

    private void recordAuditEvent(String eventType, Application app, ScrutinyAssessment assessment, String actorId) {
        recordAuditEvent(eventType, app.getId(), "APPLICATION", app.getId().toString(), actorId);
    }

    private void recordAuditEvent(String eventType, Long appId, String entityType, String entityId, String actorId) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("eventType", eventType);
            meta.put("applicationId", appId);
            meta.put("timestamp", Instant.now().toString());

            AuditEvent event = new AuditEvent(
                    eventType,
                    actorId != null ? actorId : "SYSTEM",
                    entityType,
                    entityId,
                    objectMapper.writeValueAsString(meta)
            );
            auditEventRepository.save(event);
        } catch (Exception ignored) {
        }
    }
}
