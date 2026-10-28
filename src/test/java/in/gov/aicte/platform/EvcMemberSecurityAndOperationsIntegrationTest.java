package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.admin.AdminController;
import in.gov.aicte.platform.admin.AdminService;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.auth.AuthController;
import in.gov.aicte.platform.auth.DevBearerAuthenticationFilter;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.auth.dto.TokenRequest;
import in.gov.aicte.platform.common.exception.GlobalExceptionHandler;
import in.gov.aicte.platform.config.SecurityConfig;
import in.gov.aicte.platform.evc.EvcController;
import in.gov.aicte.platform.evc.EvcMemberAssignment;
import in.gov.aicte.platform.evc.EvcMemberAssignmentRepository;
import in.gov.aicte.platform.evc.EvcMemberController;
import in.gov.aicte.platform.evc.EvcMemberProfile;
import in.gov.aicte.platform.evc.EvcMemberProfileRepository;
import in.gov.aicte.platform.evc.EvcService;
import in.gov.aicte.platform.evc.EvcVisit;
import in.gov.aicte.platform.evc.dto.AdminCreateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AdminUpdateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AssignEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.EvcMemberAssignmentResponse;
import in.gov.aicte.platform.evc.dto.EvcMemberProfileResponse;
import in.gov.aicte.platform.evc.dto.EvcMetricsResponse;
import in.gov.aicte.platform.evc.dto.EvcResponse;
import in.gov.aicte.platform.evc.dto.ScheduleEvcRequest;
import in.gov.aicte.platform.evc.dto.UpdateEvcMemberProfileRequest;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        EvcMemberController.class,
        EvcController.class,
        AdminController.class
})
@Import({
        DevTokenService.class,
        SecurityConfig.class,
        DevBearerAuthenticationFilter.class,
        GlobalExceptionHandler.class,
        BCryptPasswordEncoder.class
})
class EvcMemberSecurityAndOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private EvcService evcService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private EvcMemberProfileRepository profileRepository;

    @MockitoBean
    private EvcMemberAssignmentRepository assignmentRepository;

    @MockitoBean
    private ApplicationDocumentRepository documentRepository;

    private User evcMemberUser;
    private EvcMemberProfile evcMemberProfile;
    private EvcVisit sampleVisit;

    @BeforeEach
    void setUp() {
        evcMemberUser = new User(
                "USR-EVCM-01",
                "Dr. Arvind Saxena",
                "member.evc@aicte-india.gov.in",
                "$2a$10$VlW85MjZH3jPH85CCMKn3.LJOU8CeIm9PsKYrjTTKikunbbpWDeai",
                "EVC Member",
                "ACTIVE",
                "Northern Region",
                null,
                "IIT Delhi",
                "Professor of CSE"
        );

        evcMemberProfile = new EvcMemberProfile(
                "MEM-EVCM-01",
                evcMemberUser,
                "Dr. Arvind Saxena",
                "Professor of Computer Science & Engineering",
                "Indian Institute of Technology Delhi",
                "Computer Science & Engineering, Artificial Intelligence",
                "VERIFIED",
                "AVAILABLE",
                "+91 98765 43210"
        );

        sampleVisit = new EvcVisit(
                null,
                "EVC-101",
                "C. Verma",
                "USR-EVC-01"
        );
        sampleVisit.setVisitDate(LocalDate.now().plusDays(5));
        sampleVisit.setStatus("Scheduled");
    }

    // -------------------------------------------------------------------------
    // Phase 2: Role Authentication, Token Issuance, and Portal Partitioning
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("User portal issues valid bearer token for EVC Member role")
    void shouldAuthenticateEvcMemberThroughUserPortal() throws Exception {
        TokenRequest request = new TokenRequest("member.evc@aicte-india.gov.in", "EVC Member", null);

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.user.role").value("EVC Member"))
                .andExpect(jsonPath("$.user.email").value("member.evc@aicte-india.gov.in"));
    }

    @Test
    @DisplayName("Admin portal strictly rejects EVC Member authentication with 403 Forbidden")
    void shouldRejectEvcMemberFromAdminPortal() throws Exception {
        TokenRequest request = new TokenRequest("member.evc@aicte-india.gov.in", null, null);

        mockMvc.perform(post("/api/v1/auth/admin/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access Denied: Only authorized administrative accounts may authenticate via the SAGE Admin Panel."));
    }

    // -------------------------------------------------------------------------
    // Phase 4 & 5: Backend Authorization and Dedicated EVC Member Endpoints
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("GET /api/v1/evc/member/profile returns authenticated member's profile")
    void shouldReturnProfileForAuthenticatedMember() throws Exception {
        when(profileRepository.findByUserUserId("USR-EVCM-01")).thenReturn(Optional.of(evcMemberProfile));
        when(assignmentRepository.countByMemberId(any())).thenReturn(2L);

        mockMvc.perform(get("/api/v1/evc/member/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value("MEM-EVCM-01"))
                .andExpect(jsonPath("$.fullName").value("Dr. Arvind Saxena"))
                .andExpect(jsonPath("$.role").value("EVC Member"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.assignedVisitsCount").value(2));
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/evc/member/profile returns 401 Unauthorized")
    void shouldDenyUnauthenticatedAccessToMemberProfile() throws Exception {
        mockMvc.perform(get("/api/v1/evc/member/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "USR-INST-01", roles = "INSTITUTION_REPRESENTATIVE")
    @DisplayName("Non-member roles return 403 Forbidden when calling member endpoints")
    void shouldDenyNonMemberRolesFromMemberEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/evc/member/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("EVC Member can self-update availability status and phone")
    void shouldAllowSelfServiceProfileUpdate() throws Exception {
        when(profileRepository.findByUserUserId("USR-EVCM-01")).thenReturn(Optional.of(evcMemberProfile));
        when(profileRepository.save(any(EvcMemberProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.countByMemberId(any())).thenReturn(2L);

        UpdateEvcMemberProfileRequest request = new UpdateEvcMemberProfileRequest("UNAVAILABLE", "+91 99999 88888");

        mockMvc.perform(put("/api/v1/evc/member/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.phone").value("+91 99999 88888"));
    }

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("GET /api/v1/evc/member/dashboard returns aggregated KPI and visit information")
    void shouldReturnMemberDashboardData() throws Exception {
        when(profileRepository.findByUserUserId("USR-EVCM-01")).thenReturn(Optional.of(evcMemberProfile));
        when(assignmentRepository.countByMemberId(any())).thenReturn(1L);
        when(evcService.getMetrics()).thenReturn(new EvcMetricsResponse(1, 0, 1, 0, 0, 0));
        when(evcService.getVisitsAssignedToMember("USR-EVCM-01")).thenReturn(List.of(sampleVisit));

        mockMvc.perform(get("/api/v1/evc/member/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.memberId").value("MEM-EVCM-01"))
                .andExpect(jsonPath("$.metrics.totalCases").value(1))
                .andExpect(jsonPath("$.assignedInspections").isArray())
                .andExpect(jsonPath("$.upcomingVisits").isArray());
    }

    // -------------------------------------------------------------------------
    // Phase 4: Strict Role Boundary Enforcements (No coordinator/admin escalation)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("EVC Member cannot execute coordinator schedule operations (403 Forbidden)")
    void shouldDenyMemberFromCoordinatorSchedulingOperations() throws Exception {
        ScheduleEvcRequest request = new ScheduleEvcRequest(
                LocalDate.now().plusDays(10),
                "Coordinator",
                "Physical",
                List.of("Expert 1"),
                "Dr. Arvind Saxena",
                "EVC Member"
        );

        mockMvc.perform(post("/api/v1/evc/EVC-101/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("EVC Member cannot execute coordinator completion operations (403 Forbidden)")
    void shouldDenyMemberFromCoordinatorCompletionOperations() throws Exception {
        mockMvc.perform(post("/api/v1/evc/EVC-101/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("EVC Member cannot access administrator endpoints (403 Forbidden)")
    void shouldDenyMemberFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USR-EVCM-01", roles = "EVC_MEMBER")
    @DisplayName("EVC Member is denied access to documents for visits not assigned to them (403 Forbidden)")
    void shouldDenyAccessToDocumentsForUnassignedVisit() throws Exception {
        when(evcService.findVisitByIdOrReference("EVC-999")).thenReturn(sampleVisit);
        when(evcService.isMemberAssignedToVisit("USR-EVCM-01", sampleVisit)).thenReturn(false);

        mockMvc.perform(get("/api/v1/evc/member/inspections/EVC-999/documents"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied: You are not assigned to this EVC inspection visit."));
    }

    // -------------------------------------------------------------------------
    // Phase 3: Administrator Management of EVC Members
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMINISTRATOR")
    @DisplayName("Administrator can list all registered EVC members")
    void shouldAllowAdminToListEvcMembers() throws Exception {
        EvcMemberProfileResponse response = EvcMemberProfileResponse.from(evcMemberProfile, 2);
        when(adminService.listEvcMembers(null, null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/admin/evc-members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value("MEM-EVCM-01"))
                .andExpect(jsonPath("$[0].fullName").value("Dr. Arvind Saxena"));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMINISTRATOR")
    @DisplayName("Administrator can create an EVC member account and profile")
    void shouldAllowAdminToCreateEvcMember() throws Exception {
        AdminCreateEvcMemberRequest request = new AdminCreateEvcMemberRequest(
                "Dr. Neha Sharma",
                "neha.sharma@iitd.ac.in",
                "Secure@Pass123",
                "Associate Professor",
                "IIT Delhi",
                "Mechanical Engineering, Robotics",
                "Northern Region",
                "+91 98111 22233"
        );

        EvcMemberProfileResponse created = new EvcMemberProfileResponse(
                2L,
                "MEM-USR-EVCM-02",
                "USR-EVCM-02",
                "Dr. Neha Sharma",
                "neha.sharma@iitd.ac.in",
                "EVC Member",
                "ACTIVE",
                "Associate Professor",
                "IIT Delhi",
                "Mechanical Engineering, Robotics",
                "VERIFIED",
                "AVAILABLE",
                "+91 98111 22233",
                "Northern Region",
                0,
                Instant.now(),
                Instant.now()
        );

        when(adminService.createEvcMember(any(AdminCreateEvcMemberRequest.class), anyString())).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/evc-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value("MEM-USR-EVCM-02"))
                .andExpect(jsonPath("$.fullName").value("Dr. Neha Sharma"))
                .andExpect(jsonPath("$.role").value("EVC Member"));
    }

    @Test
    @WithMockUser(username = "USR-ADM-01", roles = "ADMINISTRATOR")
    @DisplayName("Administrator can assign an EVC member to an inspection visit panel")
    void shouldAllowAdminToAssignMemberToVisit() throws Exception {
        AssignEvcMemberRequest request = new AssignEvcMemberRequest(
                101L,
                "SUBJECT_SPECIALIST",
                "Assigned for physical lab verification"
        );

        EvcMemberAssignmentResponse response = new EvcMemberAssignmentResponse(
                1L,
                101L,
                "EVC-101",
                "APP-101",
                "Delhi Engineering College",
                "MEM-EVCM-01",
                "Dr. Arvind Saxena",
                "SUBJECT_SPECIALIST",
                "Assigned for physical lab verification",
                Instant.now()
        );

        when(adminService.assignMemberToVisit(eq("MEM-EVCM-01"), any(AssignEvcMemberRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/evc-members/MEM-EVCM-01/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evcReference").value("EVC-101"))
                .andExpect(jsonPath("$.committeeRole").value("SUBJECT_SPECIALIST"));
    }
}
