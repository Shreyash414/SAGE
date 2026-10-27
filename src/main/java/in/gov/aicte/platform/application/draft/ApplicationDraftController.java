package in.gov.aicte.platform.application.draft;

import in.gov.aicte.platform.application.draft.dto.CreateDraftRequest;
import in.gov.aicte.platform.application.draft.dto.DraftChecklistResponse;
import in.gov.aicte.platform.application.draft.dto.DraftResponse;
import in.gov.aicte.platform.application.draft.dto.PromoteDraftResult;
import in.gov.aicte.platform.application.draft.dto.UpdateDraftRequest;
import in.gov.aicte.platform.application.dto.DocumentUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/application-drafts")
@Tag(name = "Application Drafts", description = "Persistent application drafts with institution isolation and safe promotion")
public class ApplicationDraftController {

    private final ApplicationDraftService draftService;

    public ApplicationDraftController(ApplicationDraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    @Operation(summary = "Create application draft", description = "Initialize a persistent draft tied to the authenticated institution")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft created successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution")
    })
    public ResponseEntity<DraftResponse> createDraft(
            @RequestBody(required = false) CreateDraftRequest request,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        CreateDraftRequest effectiveRequest = (request != null) ? request : new CreateDraftRequest(null, null, null);
        DraftResponse response = draftService.createDraft(effectiveRequest, actorId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "List institution drafts", description = "Retrieve all pending drafts belonging to the caller's institution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Drafts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<DraftResponse>> listMyDrafts() {
        return ResponseEntity.ok(draftService.listMyDrafts());
    }

    @GetMapping("/{idOrReference}")
    @Operation(summary = "Get draft details", description = "Retrieve a draft by ID or reference with institution isolation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized: draft belongs to another institution"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<DraftResponse> getDraft(@PathVariable String idOrReference) {
        return ResponseEntity.ok(draftService.getDraft(idOrReference));
    }

    @PutMapping("/{idOrReference}")
    @Operation(summary = "Update draft form data", description = "Persist draft form data without triggering official submission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft updated successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution or draft already promoted"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<DraftResponse> updateDraft(
            @PathVariable String idOrReference,
            @RequestBody UpdateDraftRequest request,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        return ResponseEntity.ok(draftService.updateDraft(idOrReference, request, actorId));
    }

    @DeleteMapping("/{idOrReference}")
    @Operation(summary = "Delete draft", description = "Discard an un-promoted draft and its associated MinIO documents")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Draft deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution or draft already promoted"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String idOrReference,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        draftService.deleteDraft(idOrReference, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{idOrReference}/checklist")
    @Operation(summary = "Get draft checklist", description = "Retrieve statutory requirements, document statuses, and completeness breakdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checklist evaluated successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<DraftChecklistResponse> getDraftChecklist(@PathVariable String idOrReference) {
        return ResponseEntity.ok(draftService.getDraftChecklist(idOrReference));
    }

    @PostMapping(value = "/{idOrReference}/documents/{requirementCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload draft document", description = "Upload a statutory document to MinIO linked to this draft")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or requirement code"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution or draft already promoted"),
            @ApiResponse(responseCode = "404", description = "Draft not found")
    })
    public ResponseEntity<DocumentUploadResponse> uploadDraftDocument(
            @PathVariable String idOrReference,
            @PathVariable String requirementCode,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        return ResponseEntity.ok(draftService.uploadDraftDocument(idOrReference, requirementCode, file, actorId));
    }

    @GetMapping("/{idOrReference}/documents/{documentId}")
    @Operation(summary = "Download draft document", description = "Stream uploaded document bytes from MinIO")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document streamed successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution"),
            @ApiResponse(responseCode = "404", description = "Draft or document not found")
    })
    public ResponseEntity<Resource> downloadDraftDocument(
            @PathVariable String idOrReference,
            @PathVariable Long documentId
    ) {
        ApplicationDraftService.DocumentDownloadPayload payload = draftService.downloadDraftDocument(idOrReference, documentId);

        String contentDisposition = String.format("attachment; filename=\"%s\"", payload.originalFileName().replace("\"", ""));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(payload.resource());
    }

    @DeleteMapping("/{idOrReference}/documents/{documentId}")
    @Operation(summary = "Delete draft document", description = "Remove an uploaded document from MinIO and draft record")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution or draft already promoted"),
            @ApiResponse(responseCode = "404", description = "Draft or document not found")
    })
    public ResponseEntity<Void> deleteDraftDocument(
            @PathVariable String idOrReference,
            @PathVariable Long documentId,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        draftService.deleteDraftDocument(idOrReference, documentId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{idOrReference}/promote")
    @Operation(summary = "Promote and submit draft", description = "Validate 100% completeness, promote draft to official application, and queue for scrutiny")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application successfully promoted and submitted"),
            @ApiResponse(responseCode = "403", description = "Unauthorized institution or draft already promoted"),
            @ApiResponse(responseCode = "404", description = "Draft not found"),
            @ApiResponse(responseCode = "422", description = "Incomplete draft requirements blocking submission")
    })
    public ResponseEntity<PromoteDraftResult> promoteDraft(
            @PathVariable String idOrReference,
            Principal principal
    ) {
        String actorId = (principal != null) ? principal.getName() : "Applicant";
        PromoteDraftResult result = draftService.promoteDraft(idOrReference, actorId);
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
