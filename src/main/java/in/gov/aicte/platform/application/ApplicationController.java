package in.gov.aicte.platform.application;

import in.gov.aicte.platform.application.dto.ApplicationChecklistResponse;
import in.gov.aicte.platform.application.dto.ApplicationResponse;
import in.gov.aicte.platform.application.dto.ApplicationSubmissionResult;
import in.gov.aicte.platform.application.dto.CreateApplicationRequest;
import in.gov.aicte.platform.application.dto.DocumentUploadResponse;
import in.gov.aicte.platform.application.dto.SaveDraftRequest;
import in.gov.aicte.platform.common.pagination.PageResponse;
import in.gov.aicte.platform.common.pagination.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Application lifecycle management and submission endpoints")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationSubmissionService submissionService;

    public ApplicationController(
            ApplicationService applicationService,
            ApplicationSubmissionService submissionService
    ) {
        this.applicationService = applicationService;
        this.submissionService = submissionService;
    }

    @GetMapping
    @Operation(summary = "List applications", description = "Retrieve a paginated list of applications with optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications successfully retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<PageResponse<ApplicationResponse>> listApplications(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(applicationService.listApplications(institutionId, status, academicYear, region, stage, q, pageable));
    }

    @PostMapping
    @Operation(summary = "Create application", description = "Register a new application linked to a canonical institution")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application successfully created"),
            @ApiResponse(responseCode = "400", description = "Validation failed for request fields"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "404", description = "Referenced institution does not exist"),
            @ApiResponse(responseCode = "409", description = "Duplicate application reference conflict")
    })
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        ApplicationResponse response = applicationService.createApplication(request, actorId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application details", description = "Retrieve application details including linked institution information and workflow timeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application found and returned"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable String id) {
        return ResponseEntity.ok(applicationService.getApplicationByIdOrReference(id));
    }

    @PutMapping("/{id}/draft")
    @Operation(summary = "Save application draft", description = "Update draft form data and remarks without triggering submission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft saved successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed JSON payload"),
            @ApiResponse(responseCode = "403", description = "Application already submitted or unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationResponse> saveDraft(
            @PathVariable Long id,
            @RequestBody SaveDraftRequest request,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        return ResponseEntity.ok(submissionService.saveDraft(id, request, actorId));
    }

    @GetMapping("/{id}/checklist")
    @Operation(summary = "Get application checklist", description = "Authoritative completeness verification and readiness report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checklist evaluated successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution access"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationChecklistResponse> getChecklist(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getChecklist(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload application document", description = "Upload a required statutory document to private MinIO storage and register in catalog")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or requirement code"),
            @ApiResponse(responseCode = "403", description = "Application already submitted or unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @PathVariable Long id,
            @RequestParam("requirementCode") String requirementCode,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        return ResponseEntity.ok(submissionService.uploadDocument(id, requirementCode, file, actorId));
    }

    @GetMapping("/{id}/documents/{documentId}")
    @Operation(summary = "Download application document", description = "Securely stream document bytes from MinIO storage")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document streamed successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution access"),
            @ApiResponse(responseCode = "404", description = "Document or application not found")
    })
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
        ApplicationSubmissionService.DocumentDownloadPayload payload = submissionService.downloadDocument(id, documentId);

        String contentDisposition = String.format("attachment; filename=\"%s\"", payload.originalFileName().replace("\"", ""));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(payload.resource());
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @Operation(summary = "Delete application document", description = "Remove an uploaded statutory document from MinIO and database (only while in draft)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Application already submitted or unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Document or application not found")
    })
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        submissionService.deleteDocument(id, documentId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit application for scrutiny", description = "Validate mandatory fields/documents, generate immutable snapshot, and queue for scrutiny")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application submitted successfully and queued for scrutiny"),
            @ApiResponse(responseCode = "400", description = "Incomplete application requirements blocking submission"),
            @ApiResponse(responseCode = "403", description = "Application already submitted or unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationSubmissionResult> submitApplication(
            @PathVariable Long id,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        ApplicationSubmissionResult result = submissionService.submitApplication(id, actorId);
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "Execute workflow action", description = "Perform a state transition such as return for correction, resubmit, or complete scrutiny")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow action executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid action, missing mandatory fields, or invalid state prerequisite"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking conflict")
    })
    public ResponseEntity<ApplicationResponse> executeAction(
            @PathVariable String id,
            @Valid @RequestBody in.gov.aicte.platform.workflow.dto.WorkflowActionRequest request,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "SYSTEM";
        return ResponseEntity.ok(applicationService.executeWorkflowAction(id, request, actorId));
    }
}
