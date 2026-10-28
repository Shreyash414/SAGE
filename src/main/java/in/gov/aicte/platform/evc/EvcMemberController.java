package in.gov.aicte.platform.evc;

import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.dto.DocumentUploadResponse;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.evc.dto.EvcMemberDashboardResponse;
import in.gov.aicte.platform.evc.dto.EvcMemberProfileResponse;
import in.gov.aicte.platform.evc.dto.EvcMetricsResponse;
import in.gov.aicte.platform.evc.dto.EvcResponse;
import in.gov.aicte.platform.evc.dto.UpdateEvcMemberProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evc/member")
@PreAuthorize("hasRole('EVC_MEMBER')")
@Tag(name = "EVC Member Workspace", description = "Endpoints dedicated to authenticated Expert Visiting Committee Members")
@SecurityRequirement(name = "bearerAuth")
public class EvcMemberController {

    private final EvcService evcService;
    private final EvcMemberProfileRepository profileRepository;
    private final EvcMemberAssignmentRepository assignmentRepository;
    private final ApplicationDocumentRepository documentRepository;

    public EvcMemberController(
            EvcService evcService,
            EvcMemberProfileRepository profileRepository,
            EvcMemberAssignmentRepository assignmentRepository,
            ApplicationDocumentRepository documentRepository
    ) {
        this.evcService = evcService;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get EVC Member Profile", description = "Retrieve authorized profile details of the authenticated EVC member")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized: EVC_MEMBER role required"),
            @ApiResponse(responseCode = "404", description = "Member profile not found")
    })
    public ResponseEntity<EvcMemberProfileResponse> getProfile() {
        String userId = getAuthenticatedUserId();
        EvcMemberProfile profile = profileRepository.findByUserUserId(userId)
                .or(() -> profileRepository.findByMemberId(userId))
                .or(() -> profileRepository.findByUserEmailIgnoreCase(userId))
                .orElseThrow(() -> new ResourceNotFoundException("EVC member profile not found for user: " + userId));

        long assignedCount = assignmentRepository.countByMemberId(profile.getId());
        return ResponseEntity.ok(EvcMemberProfileResponse.from(profile, assignedCount));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update EVC Member Profile", description = "Update permitted self-service fields (availabilityStatus, phone) of the authenticated EVC member")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized: EVC_MEMBER role required")
    })
    public ResponseEntity<EvcMemberProfileResponse> updateProfile(
            @Valid @RequestBody UpdateEvcMemberProfileRequest request
    ) {
        String userId = getAuthenticatedUserId();
        EvcMemberProfile profile = profileRepository.findByUserUserId(userId)
                .or(() -> profileRepository.findByMemberId(userId))
                .or(() -> profileRepository.findByUserEmailIgnoreCase(userId))
                .orElseThrow(() -> new ResourceNotFoundException("EVC member profile not found for user: " + userId));

        if (request.availabilityStatus() != null && !request.availabilityStatus().isBlank()) {
            profile.setAvailabilityStatus(request.availabilityStatus().trim().toUpperCase());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone().trim());
        }

        EvcMemberProfile saved = profileRepository.save(profile);
        long assignedCount = assignmentRepository.countByMemberId(saved.getId());
        return ResponseEntity.ok(EvcMemberProfileResponse.from(saved, assignedCount));
    }

    @GetMapping("/inspections")
    @Operation(summary = "Get Assigned Inspections", description = "List all EVC visits assigned to the authenticated member")
    public ResponseEntity<List<EvcResponse>> getAssignedInspections() {
        String userId = getAuthenticatedUserId();
        List<EvcVisit> visits = evcService.getVisitsAssignedToMember(userId);
        return ResponseEntity.ok(visits.stream().map(EvcResponse::from).toList());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get EVC Member Dashboard Data", description = "Retrieve aggregated KPIs, profile, and inspection assignments for the authenticated member")
    public ResponseEntity<EvcMemberDashboardResponse> getDashboard() {
        String userId = getAuthenticatedUserId();

        EvcMemberProfile profile = profileRepository.findByUserUserId(userId)
                .or(() -> profileRepository.findByMemberId(userId))
                .or(() -> profileRepository.findByUserEmailIgnoreCase(userId))
                .orElse(null);

        long assignedCount = profile != null ? assignmentRepository.countByMemberId(profile.getId()) : 0;
        EvcMemberProfileResponse profileResponse = profile != null ? EvcMemberProfileResponse.from(profile, assignedCount) : null;

        EvcMetricsResponse metrics = evcService.getMetrics();
        List<EvcVisit> assignedVisits = evcService.getVisitsAssignedToMember(userId);
        List<EvcResponse> inspections = assignedVisits.stream().map(EvcResponse::from).toList();

        List<EvcResponse> upcomingVisits = inspections.stream()
                .filter(i -> "Scheduled".equalsIgnoreCase(i.status()))
                .toList();

        return ResponseEntity.ok(new EvcMemberDashboardResponse(
                profileResponse,
                metrics,
                inspections,
                upcomingVisits
        ));
    }

    @GetMapping("/inspections/{idOrReference}/documents")
    @Operation(summary = "Get Assigned Inspection Documents", description = "Retrieve statutory and scrutiny documents for an assigned EVC visit")
    public ResponseEntity<List<DocumentUploadResponse>> getInspectionDocuments(@PathVariable String idOrReference) {
        String userId = getAuthenticatedUserId();
        EvcVisit visit = evcService.findVisitByIdOrReference(idOrReference);

        if (!evcService.isMemberAssignedToVisit(userId, visit)) {
            throw new AccessDeniedException("Access denied: You are not assigned to this EVC inspection visit.");
        }

        if (visit.getApplication() == null) {
            throw new ResourceNotFoundException("No application dossier linked to EVC visit: " + idOrReference);
        }

        List<ApplicationDocument> docs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(visit.getApplication().getId());
        return ResponseEntity.ok(docs.stream().map(DocumentUploadResponse::from).toList());
    }

    private String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        return auth.getName();
    }
}

