package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.auth.AuthController;
import in.gov.aicte.platform.auth.DevBearerAuthenticationFilter;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.auth.dto.TokenRequest;
import in.gov.aicte.platform.common.exception.GlobalExceptionHandler;
import in.gov.aicte.platform.config.SecurityConfig;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        DevTokenService.class,
        SecurityConfig.class,
        DevBearerAuthenticationFilter.class,
        GlobalExceptionHandler.class,
        BCryptPasswordEncoder.class
})
class PortalAuthenticationSeparationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User adminUser;
    private User scrutinyUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUserId("USR-ADM-01");
        adminUser.setFullName("System Administrator");
        adminUser.setEmail("admin@aicte-india.gov.in");
        adminUser.setPasswordHash(passwordEncoder.encode("SAGE@Admin2026!"));
        adminUser.setRole("Administrator");
        adminUser.setStatus("ACTIVE");
        adminUser.setCreatedAt(Instant.now());
        adminUser.setUpdatedAt(Instant.now());

        scrutinyUser = new User();
        scrutinyUser.setId(2L);
        scrutinyUser.setUserId("USR-SO-01");
        scrutinyUser.setFullName("B. Singh");
        scrutinyUser.setEmail("scrutiny.north@aicte-india.gov.in");
        scrutinyUser.setPasswordHash(passwordEncoder.encode("DemoUser@2026!"));
        scrutinyUser.setRole("Scrutiny Officer");
        scrutinyUser.setStatus("ACTIVE");
        scrutinyUser.setCreatedAt(Instant.now());
        scrutinyUser.setUpdatedAt(Instant.now());

        when(userRepository.findByEmailIgnoreCase("admin@aicte-india.gov.in")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByUserId("USR-ADM-01")).thenReturn(Optional.of(adminUser));

        when(userRepository.findByEmailIgnoreCase("scrutiny.north@aicte-india.gov.in")).thenReturn(Optional.of(scrutinyUser));
        when(userRepository.findByUserId("USR-SO-01")).thenReturn(Optional.of(scrutinyUser));

        when(userRepository.findAll()).thenReturn(java.util.List.of(adminUser, scrutinyUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Scenario 1: Ordinary user authenticates through regular login flow (/auth/token) — SUCCEEDS (200 OK)")
    void scenario1_ordinaryUserThroughRegularLoginFlow_succeeds() throws Exception {
        TokenRequest request = new TokenRequest("scrutiny.north@aicte-india.gov.in", "DemoUser@2026!", "Scrutiny Officer");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.user.id").value("USR-SO-01"))
                .andExpect(jsonPath("$.user.role").value("Scrutiny Officer"))
                .andExpect(jsonPath("$.user.email").value("scrutiny.north@aicte-india.gov.in"));
    }

    @Test
    @DisplayName("Scenario 2: Administrator authenticates through regular login flow (/auth/token) — REJECTED (403 Forbidden)")
    void scenario2_administratorThroughRegularLoginFlow_isRejected() throws Exception {
        TokenRequest request = new TokenRequest("admin@aicte-india.gov.in", "SAGE@Admin2026!", "Administrator");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value("Administrative accounts cannot sign in through this portal. Please use the authorized administrative console."));
    }

    @Test
    @DisplayName("Scenario 2 (Bypass Protection): Administrator spoofing role on regular portal — REJECTED (403 Forbidden)")
    void scenario2_administratorSpoofingOrdinaryRoleOnRegularPortal_isRejected() throws Exception {
        // Attempting to bypass by claiming to be 'Scrutiny Officer' while using admin account
        TokenRequest request = new TokenRequest("admin@aicte-india.gov.in", "SAGE@Admin2026!", "Scrutiny Officer");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value("Administrative accounts cannot sign in through this portal. Please use the authorized administrative console."));
    }

    @Test
    @DisplayName("Scenario 2 (Role-Only Payload): Client requesting Administrator role without username — REJECTED (403 Forbidden)")
    void scenario2_clientRequestingAdministratorRoleDirectly_isRejected() throws Exception {
        TokenRequest request = new TokenRequest(null, null, "Administrator");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value("Administrative accounts cannot sign in through this portal. Please use the authorized administrative console."));
    }

    @Test
    @DisplayName("Scenario 3: Administrator authenticates through admin login flow (/auth/admin/token) — SUCCEEDS (200 OK)")
    void scenario3_administratorThroughAdminLoginFlow_succeeds() throws Exception {
        TokenRequest request = new TokenRequest("admin@aicte-india.gov.in", "SAGE@Admin2026!", null);

        mockMvc.perform(post("/api/v1/auth/admin/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.user.id").value("USR-ADM-01"))
                .andExpect(jsonPath("$.user.role").value("Administrator"))
                .andExpect(jsonPath("$.user.email").value("admin@aicte-india.gov.in"));
    }

    @Test
    @DisplayName("Scenario 4: Ordinary user authenticates through admin login flow (/auth/admin/token) — REJECTED (403 Forbidden)")
    void scenario4_ordinaryUserThroughAdminLoginFlow_isRejected() throws Exception {
        TokenRequest request = new TokenRequest("scrutiny.north@aicte-india.gov.in", "DemoUser@2026!", null);

        mockMvc.perform(post("/api/v1/auth/admin/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value("Access Denied: Only authorized administrative accounts may authenticate via the SAGE Admin Panel."));
    }

    @Test
    @DisplayName("Scenario 4 (Bypass Protection): Ordinary user spoofing Administrator role on admin login flow — REJECTED (403 Forbidden)")
    void scenario4_ordinaryUserSpoofingAdminRoleOnAdminLoginFlow_isRejected() throws Exception {
        // Attempting to gain admin token by claiming role 'Administrator' with ordinary account
        TokenRequest request = new TokenRequest("scrutiny.north@aicte-india.gov.in", "DemoUser@2026!", "Administrator");

        mockMvc.perform(post("/api/v1/auth/admin/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value("Access Denied: Only authorized administrative accounts may authenticate via the SAGE Admin Panel."));
    }

    @Test
    @DisplayName("Direct API: Inactive ordinary user is rejected with 403 Action Prohibited")
    void directApi_inactiveUserIsRejected() throws Exception {
        User suspendedUser = new User();
        suspendedUser.setId(3L);
        suspendedUser.setUserId("USR-SUS-01");
        suspendedUser.setFullName("Suspended Officer");
        suspendedUser.setEmail("suspended@aicte-india.gov.in");
        suspendedUser.setPasswordHash(passwordEncoder.encode("Pass123!"));
        suspendedUser.setRole("Regional Officer");
        suspendedUser.setStatus("SUSPENDED");
        suspendedUser.setCreatedAt(Instant.now());
        suspendedUser.setUpdatedAt(Instant.now());

        when(userRepository.findByEmailIgnoreCase("suspended@aicte-india.gov.in")).thenReturn(Optional.of(suspendedUser));

        TokenRequest request = new TokenRequest("suspended@aicte-india.gov.in", "Pass123!", "Regional Officer");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Action Prohibited"));
    }
}
