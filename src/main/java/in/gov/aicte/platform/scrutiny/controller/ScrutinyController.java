package in.gov.aicte.platform.scrutiny.controller;

import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.document.ApplicationDocument;
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
import in.gov.aicte.platform.scrutiny.repository.ScrutinyAssessmentRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDeficiencyRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDocumentExtractionRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyFindingRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyRescrutinyComparisonRepository;
import in.gov.aicte.platform.scrutiny.service.ScrutinyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}")
@Tag(name = "Automated Scrutiny & Re-Scrutiny", description = "AICTE Automated Preliminary Scrutiny, Deficiency Notices, and Re-Scrutiny")
public class ScrutinyController {

    private final ScrutinyService scrutinyService;
    private final ApplicationRepository applicationRepository;
    private final ScrutinyAssessmentRepository assessmentRepository;
    private final ScrutinyFindingRepository findingRepository;
    private final ScrutinyDeficiencyRepository deficiencyRepository;
    private final ScrutinyRescrutinyComparisonRepository comparisonRepository;
    private final ScrutinyDocumentExtractionRepository extractionRepository;
    private final in.gov.aicte.platform.application.ApplicationSubmissionService submissionService;
    private final in.gov.aicte.platform.scrutiny.service.ReScrutinyService reScrutinyService;

    public ScrutinyController(
            ScrutinyService scrutinyService,
            ApplicationRepository applicationRepository,
            ScrutinyAssessmentRepository assessmentRepository,
            ScrutinyFindingRepository findingRepository,
            ScrutinyDeficiencyRepository deficiencyRepository,
            ScrutinyRescrutinyComparisonRepository comparisonRepository,
            ScrutinyDocumentExtractionRepository extractionRepository,
            in.gov.aicte.platform.application.ApplicationSubmissionService submissionService,
            in.gov.aicte.platform.scrutiny.service.ReScrutinyService reScrutinyService
    ) {
        this.scrutinyService = scrutinyService;
        this.applicationRepository = applicationRepository;
        this.assessmentRepository = assessmentRepository;
        this.findingRepository = findingRepository;
        this.deficiencyRepository = deficiencyRepository;
        this.comparisonRepository = comparisonRepository;
        this.extractionRepository = extractionRepository;
        this.submissionService = submissionService;
        this.reScrutinyService = reScrutinyService;
    }

    private Long resolveAppId(String idOrRef) {
        try {
            Long id = Long.parseLong(idOrRef);
            if (applicationRepository.existsById(id)) return id;
        } catch (NumberFormatException ignored) {
        }
        Application app = applicationRepository.findByApplicationReference(idOrRef)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + idOrRef));
        return app.getId();
    }

    @PostMapping("/scrutiny/run")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Run automated scrutiny", description = "Execute OCR/PDF extraction, AICTE statutory validation rules, and generate preliminary findings")
    public ResponseEntity<PreliminaryScrutinyReportResponse> runAutomatedScrutiny(
            @PathVariable String applicationId,
            Principal principal
    ) {
        Long appId = resolveAppId(applicationId);
        String actor = principal != null ? principal.getName() : "Scrutiny Officer";
        return ResponseEntity.ok(scrutinyService.runAutomatedScrutiny(appId, actor));
    }

    @GetMapping("/scrutiny/report")
    @Operation(summary = "Get preliminary scrutiny report", description = "Retrieve aggregated scrutiny assessment, findings, deficiencies, and comparison diffs")
    public ResponseEntity<PreliminaryScrutinyReportResponse> getScrutinyReport(
            @PathVariable String applicationId
    ) {
        Long appId = resolveAppId(applicationId);
        return ResponseEntity.ok(scrutinyService.getLatestReport(appId));
    }

    @GetMapping("/scrutiny/assessments")
    @Operation(summary = "List scrutiny assessments", description = "Retrieve historical assessment runs and cycles")
    public ResponseEntity<List<ScrutinyAssessmentResponse>> getAssessments(
            @PathVariable String applicationId
    ) {
        Long appId = resolveAppId(applicationId);
        List<ScrutinyAssessment> assessments = assessmentRepository.findByApplicationIdOrderByCreatedAtDesc(appId);
        return ResponseEntity.ok(assessments.stream().map(ScrutinyAssessmentResponse::from).toList());
    }

    @GetMapping("/scrutiny/findings")
    @Operation(summary = "List scrutiny findings", description = "Retrieve automated check findings for the latest or specific assessment")
    public ResponseEntity<List<ScrutinyFindingResponse>> getFindings(
            @PathVariable String applicationId,
            @RequestParam(required = false) Long assessmentId,
            @RequestParam(required = false, defaultValue = "false") boolean consolidated
    ) {
        Long appId = resolveAppId(applicationId);
        return ResponseEntity.ok(scrutinyService.getFindings(appId, assessmentId, consolidated));
    }

    @GetMapping("/scrutiny/extractions")
    @Operation(summary = "List document extractions", description = "Retrieve persisted OCR and text extraction records for the application")
    public ResponseEntity<List<ScrutinyDocumentExtractionResponse>> getExtractions(
            @PathVariable String applicationId,
            @RequestParam(required = false) Long assessmentId
    ) {
        Long appId = resolveAppId(applicationId);
        List<ScrutinyDocumentExtraction> list;
        if (assessmentId != null) {
            list = extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(assessmentId);
        } else {
            Optional<ScrutinyAssessment> latestOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId);
            if (latestOpt.isPresent()) {
                list = extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(latestOpt.get().getId());
            } else {
                list = extractionRepository.findByApplicationIdOrderByCreatedAtDesc(appId);
            }
        }
        return ResponseEntity.ok(list.stream().map(ScrutinyDocumentExtractionResponse::from).toList());
    }

    @GetMapping("/scrutiny/documents/{documentId}/preview")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Preview uploaded document for scrutiny", description = "Securely stream document bytes from MinIO for officer scrutiny inspection")
    public ResponseEntity<org.springframework.core.io.Resource> previewDocument(
            @PathVariable String applicationId,
            @PathVariable Long documentId
    ) {
        Long appId = resolveAppId(applicationId);
        in.gov.aicte.platform.application.ApplicationSubmissionService.DocumentDownloadPayload payload = submissionService.downloadDocument(appId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.fileSize())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + payload.originalFileName().replace("\"", "") + "\"")
                .body(payload.resource());
    }

    @PostMapping("/scrutiny/findings/{findingId}/review")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Review scrutiny finding", description = "Confirm potential finding as an official deficiency or dismiss with reasons")
    public ResponseEntity<ScrutinyFindingResponse> reviewFinding(
            @PathVariable String applicationId,
            @PathVariable Long findingId,
            @Valid @RequestBody ReviewFindingRequest request,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : "Scrutiny Officer";
        return ResponseEntity.ok(scrutinyService.reviewFinding(findingId, request, actor));
    }

    @GetMapping("/deficiencies")
    @Operation(summary = "List application deficiencies", description = "Retrieve all issued and historical deficiencies for the application")
    public ResponseEntity<List<ScrutinyDeficiencyResponse>> getDeficiencies(
            @PathVariable String applicationId,
            @RequestParam(required = false) String status
    ) {
        Long appId = resolveAppId(applicationId);
        List<ScrutinyDeficiency> list;
        if (status != null && !status.isBlank()) {
            list = deficiencyRepository.findByApplicationIdAndStatus(appId, status.toUpperCase());
        } else {
            list = deficiencyRepository.findByApplicationIdOrderByCreatedAtAsc(appId);
        }
        return ResponseEntity.ok(list.stream().map(ScrutinyDeficiencyResponse::from).toList());
    }

    @PostMapping("/deficiencies/issue")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Issue formal deficiency notice", description = "Confirm selected findings as formal deficiencies and send notice to institution")
    public ResponseEntity<List<ScrutinyDeficiencyResponse>> issueFormalDeficiencies(
            @PathVariable String applicationId,
            @RequestBody IssueDeficienciesRequest request,
            Principal principal
    ) {
        Long appId = resolveAppId(applicationId);
        String actor = principal != null ? principal.getName() : "Scrutiny Officer";
        return ResponseEntity.ok(scrutinyService.issueFormalDeficiencies(appId, request, actor));
    }

    @PostMapping(value = "/deficiencies/{deficiencyId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload corrected deficiency evidence", description = "Upload a revised document version associated with an issued deficiency while preserving original evidence")
    public ResponseEntity<ApplicationDocument> uploadCorrectedDocument(
            @PathVariable String applicationId,
            @PathVariable Long deficiencyId,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        Long appId = resolveAppId(applicationId);
        String actor = principal != null ? principal.getName() : "Institution Representative";
        return ResponseEntity.ok(scrutinyService.uploadCorrectedDocument(appId, deficiencyId, file, actor));
    }

    @PostMapping("/deficiencies/submit-responses")
    @Operation(summary = "Submit deficiency rectifications", description = "Submit institution explanations and uploaded revisions for automated re-scrutiny")
    public ResponseEntity<PreliminaryScrutinyReportResponse> submitDeficiencyResponses(
            @PathVariable String applicationId,
            @RequestBody SubmitDeficiencyResponsesRequest request,
            Principal principal
    ) {
        Long appId = resolveAppId(applicationId);
        String actor = principal != null ? principal.getName() : "Institution Representative";
        return ResponseEntity.ok(scrutinyService.submitDeficiencyResponses(appId, request, actor));
    }

    @PostMapping("/deficiencies/{deficiencyId}/review")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Review deficiency rectification", description = "Officer confirms rectification as VERIFIED_RESOLVED or rejects correction")
    public ResponseEntity<ScrutinyDeficiencyResponse> reviewDeficiency(
            @PathVariable String applicationId,
            @PathVariable Long deficiencyId,
            @Valid @RequestBody ReviewDeficiencyRequest request,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : "Scrutiny Officer";
        return ResponseEntity.ok(scrutinyService.reviewDeficiency(deficiencyId, request, actor));
    }

    @GetMapping("/scrutiny/comparison")
    @Operation(summary = "Get re-scrutiny comparisons", description = "Retrieve side-by-side evidence diffs between original and corrected documents")
    public ResponseEntity<List<RescrutinyComparisonResponse>> getComparisons(
            @PathVariable String applicationId,
            @RequestParam(required = false) Long assessmentId
    ) {
        Long appId = resolveAppId(applicationId);
        List<ScrutinyRescrutinyComparison> comparisons;
        if (assessmentId != null) {
            comparisons = comparisonRepository.findByAssessmentIdOrderByIdAsc(assessmentId);
        } else {
            Optional<ScrutinyAssessment> latestOpt = assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId);
            if (latestOpt.isPresent()) {
                comparisons = comparisonRepository.findByAssessmentIdOrderByIdAsc(latestOpt.get().getId());
            } else {
                comparisons = Collections.emptyList();
            }
            if (comparisons.isEmpty()) {
                // Application fallback: retrieve comparisons across recent re-scrutiny cycles
                comparisons = comparisonRepository.findByAssessmentApplicationIdOrderByCreatedAtDesc(appId);
            }
        }
        return ResponseEntity.ok(comparisons.stream().map(RescrutinyComparisonResponse::from).toList());
    }

    @PostMapping("/scrutiny/rescrutiny")
    @PreAuthorize("hasAnyRole('SCRUTINY_OFFICER', 'REGIONAL_OFFICER', 'SENIOR_MANAGEMENT', 'ADMINISTRATOR', 'ADMIN')")
    @Operation(summary = "Execute automated re-scrutiny", description = "Trigger comparative re-evaluation of uploaded corrected documents and generate evidence diffs")
    public ResponseEntity<PreliminaryScrutinyReportResponse> runRescrutiny(
            @PathVariable String applicationId,
            Principal principal
    ) {
        Long appId = resolveAppId(applicationId);
        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + appId));
        String actor = principal != null ? principal.getName() : "Scrutiny Officer";
        reScrutinyService.runAutomatedRescrutiny(application, actor);
        return ResponseEntity.ok(scrutinyService.getLatestReport(appId));
    }
}
