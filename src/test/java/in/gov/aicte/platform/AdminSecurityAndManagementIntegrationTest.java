package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.admin.AdminController;
import in.gov.aicte.platform.admin.AdminService;
import in.gov.aicte.platform.admin.dto.AdminMetricsResponse;
import in.gov.aicte.platform.admin.dto.CreateUserRequest;
import in.gov.aicte.platform.admin.dto.UpdateUserRequest;
import in.gov.aicte.platform.admin.dto.UserDetailResponse;
import in.gov.aicte.platform.admin.dto.UserResponse;
import in.gov.aicte.platform.admin.dto.UserStatusRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.auth.DevBearerAuthenticationFilter;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.common.exception.DuplicateResourceException;
import in.gov.aicte.platform.common.exception.GlobalExceptionHandler;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.config.SecurityConfig;
import in.gov.aicte.platform.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, DevBearerAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminSecurityAndManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private DevTokenService devTokenService;

    @MockitoBean
    private UserRepository userRepository;

    private UserResponse createSampleUserResponse(String userId, String email, String role, String status) {
        return new UserResponse(
                1L,
                userId,
                "Test Officer",
                email,
                role,
                status,
                "Northern Region",
                null,
                null,
                "Officer Desk",
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Security: Unauthenticated request to /api/v1/admin/users is rejected with 401/403")
    void shouldRejectUnauthenticatedRequestToAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "USR-SO-01", roles = "SCRUTINY_OFFICER")
    @DisplayName("Security: Authenticated non-admin user (Scrutiny Officer) is forbidden (403) from admin endpoints")
    void shouldForbidNonAdminUserFromListingUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-RO-01", roles = "REGIONAL_OFFICER")
    @DisplayName("Security: Authenticated Regional Officer is forbidden (403) from creating users")
    void shouldForbidNonAdminFromCreatingUsers() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Jane Doe",
                "jane@example.gov.in",
                "Regional Officer",
                "Password123!",
                "Northern Region",
                null,
                null,
                "Desk Officer"
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin: Authorized administrator can list users with pagination")
    void shouldAllowAdministratorToListUsers() throws Exception {
        UserResponse sample = createSampleUserResponse("USR-SO-01", "so@example.gov.in", "Scrutiny Officer", "ACTIVE");
        Page<UserResponse> page = new PageImpl<>(List.of(sample));

        when(adminService.searchUsers(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("USR-SO-01"))
                .andExpect(jsonPath("$.content[0].email").value("so@example.gov.in"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin: Authorized administrator can create a new user")
    void shouldAllowAdministratorToCreateUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Prof. Arvind Sharma",
                "arvind@example.gov.in",
                "Regional Officer",
                "SAGE@Pass123",
                "Northern Region",
                null,
                null,
                "Regional Director"
        );

        UserResponse created = createSampleUserResponse("USR-RO-NEW", "arvind@example.gov.in", "Regional Officer", "ACTIVE");
        when(adminService.createUser(any(CreateUserRequest.class), eq("USR-ADM-01"))).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("USR-RO-NEW"))
                .andExpect(jsonPath("$.email").value("arvind@example.gov.in"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(adminService).createUser(any(CreateUserRequest.class), eq("USR-ADM-01"));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin Validation: Creating a user with invalid email returns 400 Bad Request")
    void shouldRejectInvalidEmailOnUserCreation() throws Exception {
        CreateUserRequest invalidRequest = new CreateUserRequest(
                "Bad User",
                "not-an-email",
                "Regional Officer",
                "Password123!",
                "Northern Region",
                null,
                null,
                "Desk"
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin Validation: Duplicate email throws 409 Conflict ProblemDetail")
    void shouldReturn409OnDuplicateEmail() throws Exception {
        CreateUserRequest duplicateRequest = new CreateUserRequest(
                "Duplicate User",
                "admin@aicte-india.gov.in",
                "Administrator",
                "Password123!",
                "Platform-wide",
                null,
                null,
                "Admin"
        );

        when(adminService.createUser(any(CreateUserRequest.class), anyString()))
                .thenThrow(new DuplicateResourceException("An account with email 'admin@aicte-india.gov.in' already exists."));

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with email 'admin@aicte-india.gov.in' already exists."));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin: Administrator can change user account status (e.g. SUSPENDED/DEACTIVATED)")
    void shouldAllowAdministratorToChangeUserStatus() throws Exception {
        UserStatusRequest statusRequest = new UserStatusRequest("DEACTIVATED", "Audit review deficiency identified");
        UserResponse updated = createSampleUserResponse("USR-SO-01", "so@example.gov.in", "Scrutiny Officer", "DEACTIVATED");

        when(adminService.updateUserStatus(eq("USR-SO-01"), any(UserStatusRequest.class), eq("USR-ADM-01"))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/users/USR-SO-01/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        verify(adminService).updateUserStatus(eq("USR-SO-01"), any(UserStatusRequest.class), eq("USR-ADM-01"));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin: Administrator can query audit logs with pagination")
    void shouldAllowAdministratorToQueryAuditLogs() throws Exception {
        AuditEvent sampleAudit = new AuditEvent(
                "USER_STATUS_CHANGED",
                "USR-ADM-01",
                "USER",
                "USR-SO-01",
                "SUCCESS",
                "REQ-1234",
                "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"DEACTIVATED\"}"
        );
        Page<AuditEvent> auditPage = new PageImpl<>(List.of(sampleAudit));

        when(adminService.getAuditLogs(any(), any(), any(), any(), any(Pageable.class))).thenReturn(auditPage);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("USER_STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[0].actorId").value("USR-ADM-01"))
                .andExpect(jsonPath("$.content[0].targetEntityId").value("USR-SO-01"));
    }

    @Test
    @WithMockUser(username = "USR-SO-01", roles = "SCRUTINY_OFFICER")
    @DisplayName("Security: Ordinary user cannot query administrative audit logs (403 Forbidden)")
    void shouldForbidOrdinaryUserFromReadingAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMIN")
    @DisplayName("Admin: Administrator can fetch metrics dashboard counters")
    void shouldAllowAdministratorToGetMetrics() throws Exception {
        AdminMetricsResponse metrics = new AdminMetricsResponse(
                7L, 6L, 1L, 0L, Map.of("Administrator", 1L, "Regional Officer", 2L), 42L
        );
        when(adminService.getMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/v1/admin/metrics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(7))
                .andExpect(jsonPath("$.activeUsers").value(6))
                .andExpect(jsonPath("$.totalAuditEvents").value(42));
    }
}
