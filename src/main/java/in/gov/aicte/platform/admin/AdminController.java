package in.gov.aicte.platform.admin;

import in.gov.aicte.platform.admin.dto.AdminMetricsResponse;
import in.gov.aicte.platform.admin.dto.CreateUserRequest;
import in.gov.aicte.platform.admin.dto.UpdateUserRequest;
import in.gov.aicte.platform.admin.dto.UserDetailResponse;
import in.gov.aicte.platform.admin.dto.UserResponse;
import in.gov.aicte.platform.admin.dto.UserStatusRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.common.pagination.PageResponse;
import in.gov.aicte.platform.common.pagination.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import in.gov.aicte.platform.evc.dto.AdminCreateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AdminUpdateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AssignEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.EvcMemberAssignmentResponse;
import in.gov.aicte.platform.evc.dto.EvcMemberProfileResponse;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
@Tag(name = "Admin Panel", description = "Privileged administrator user-management and audit endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @Operation(summary = "Search & list users", description = "Returns paginated list of registered users with multi-attribute filtering")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserResponse> usersPage = adminService.searchUsers(search, role, status, region, pageable);
        return ResponseEntity.ok(PageResponse.from(usersPage));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user details", description = "Returns detailed profile and recent audit history for a specific user")
    public ResponseEntity<UserDetailResponse> getUserDetail(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getUserDetail(userId));
    }

    @PostMapping("/users")
    @Operation(summary = "Create user", description = "Provisions a new user account with validated fields and permitted role")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        UserResponse created = adminService.createUser(request, actingAdminId);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.userId())).body(created);
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Update user", description = "Updates permitted user profile fields and role assignments")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(adminService.updateUser(userId, request, actingAdminId));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Change user status", description = "Activates, suspends, or deactivates user accounts with mandatory audit reason")
    public ResponseEntity<UserResponse> changeUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(adminService.updateUserStatus(userId, request, actingAdminId));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Returns paginated administrative audit logs with criteria filtering")
    public ResponseEntity<PageResponse<AuditEvent>> getAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String targetEntityType,
            @RequestParam(required = false) String targetEntityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEvent> logsPage = adminService.getAuditLogs(eventType, actorId, targetEntityType, targetEntityId, pageable);
        return ResponseEntity.ok(PageResponse.from(logsPage));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get platform administrative metrics", description = "Returns live user directory and compliance audit counters")
    public ResponseEntity<AdminMetricsResponse> getMetrics() {
        return ResponseEntity.ok(adminService.getMetrics());
    }

    @GetMapping("/activity")
    @Operation(summary = "Get user activity overview", description = "Returns recent user authentication events and administrative changes")
    public ResponseEntity<Map<String, Object>> getActivity() {
        return ResponseEntity.ok(adminService.getRecentActivity());
    }

    // =========================================================================
    // Dedicated EVC Member Account & Committee Management
    // =========================================================================

    @GetMapping("/evc-members")
    @Operation(summary = "List EVC members", description = "Returns list of registered EVC committee members and their profiles")
    public ResponseEntity<List<EvcMemberProfileResponse>> listEvcMembers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String region
    ) {
        return ResponseEntity.ok(adminService.listEvcMembers(search, status, region));
    }

    @GetMapping("/evc-members/{idOrMemberId}")
    @Operation(summary = "Get EVC member detail", description = "Returns profile and workload details for an EVC member")
    public ResponseEntity<EvcMemberProfileResponse> getEvcMemberDetail(@PathVariable String idOrMemberId) {
        return ResponseEntity.ok(adminService.getEvcMemberDetail(idOrMemberId));
    }

    @PostMapping("/evc-members")
    @Operation(summary = "Create EVC member", description = "Provisions an EVC Member user account and initialized profile")
    public ResponseEntity<EvcMemberProfileResponse> createEvcMember(
            @Valid @RequestBody AdminCreateEvcMemberRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        EvcMemberProfileResponse created = adminService.createEvcMember(request, actingAdminId);
        return ResponseEntity.created(URI.create("/api/v1/admin/evc-members/" + created.memberId())).body(created);
    }

    @PutMapping("/evc-members/{idOrMemberId}")
    @Operation(summary = "Update EVC member profile", description = "Updates designation, organization, expertise, and status of an EVC member")
    public ResponseEntity<EvcMemberProfileResponse> updateEvcMember(
            @PathVariable String idOrMemberId,
            @Valid @RequestBody AdminUpdateEvcMemberRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(adminService.updateEvcMemberProfile(idOrMemberId, request, actingAdminId));
    }

    @GetMapping("/evc-members/{memberId}/assignments")
    @Operation(summary = "List member inspection assignments", description = "Returns visits assigned to an EVC member")
    public ResponseEntity<List<EvcMemberAssignmentResponse>> listMemberAssignments(@PathVariable String memberId) {
        return ResponseEntity.ok(adminService.listMemberAssignments(memberId));
    }

    @PostMapping("/evc-members/{memberId}/assign")
    @Operation(summary = "Assign member to inspection visit", description = "Links an EVC member to an EVC inspection visit panel")
    public ResponseEntity<EvcMemberAssignmentResponse> assignMemberToVisit(
            @PathVariable String memberId,
            @Valid @RequestBody AssignEvcMemberRequest request,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(adminService.assignMemberToVisit(memberId, request, actingAdminId));
    }

    @DeleteMapping("/evc-members/{memberId}/assign/{visitId}")
    @Operation(summary = "Remove member from inspection visit", description = "Unlinks an EVC member from an inspection visit panel")
    public ResponseEntity<Void> removeMemberFromVisit(
            @PathVariable String memberId,
            @PathVariable Long visitId,
            Authentication authentication
    ) {
        String actingAdminId = authentication != null ? authentication.getName() : "ADMIN";
        adminService.removeMemberFromVisit(memberId, visitId, actingAdminId);
        return ResponseEntity.noContent().build();
    }
}
