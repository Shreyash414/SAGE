package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.admin.AdminService;
import in.gov.aicte.platform.admin.dto.AdminMetricsResponse;
import in.gov.aicte.platform.admin.dto.CreateUserRequest;
import in.gov.aicte.platform.admin.dto.UpdateUserRequest;
import in.gov.aicte.platform.admin.dto.UserResponse;
import in.gov.aicte.platform.admin.dto.UserStatusRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.common.exception.DuplicateResourceException;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DevTokenService devTokenService;

    private ObjectMapper objectMapper;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adminService = new AdminService(
                userRepository,
                auditEventRepository,
                passwordEncoder,
                objectMapper,
                devTokenService
        );
    }

    @Test
    @DisplayName("Search Users: Uses Specification filtering and maps to UserResponse")
    void shouldSearchUsersWithSpecification() {
        User user = new User("USR-SO-01", "B. Singh", "so@aicte-india.gov.in", "hash", "Scrutiny Officer", "ACTIVE", "Northern Region", null, null, "Desk Officer");
        org.springframework.data.domain.Page<User> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(user));
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);

        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        org.springframework.data.domain.Page<UserResponse> result = adminService.searchUsers("Singh", null, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo("USR-SO-01");
    }

    @Test
    @DisplayName("Create User: Rejects duplicate email with DuplicateResourceException")
    void shouldRejectDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "Jane Doe",
                "admin@aicte-india.gov.in",
                "Administrator",
                "Secret123!",
                "Platform-wide",
                null,
                null,
                "Admin"
        );

        when(userRepository.existsByEmailIgnoreCase("admin@aicte-india.gov.in")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createUser(request, "USR-ADM-01"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create User: Rejects unauthorized / invalid role")
    void shouldRejectInvalidRole() {
        CreateUserRequest request = new CreateUserRequest(
                "Jane Doe",
                "jane@example.gov.in",
                "SuperRootHacker",
                "Secret123!",
                "Platform-wide",
                null,
                null,
                "Admin"
        );

        when(userRepository.existsByEmailIgnoreCase("jane@example.gov.in")).thenReturn(false);

        assertThatThrownBy(() -> adminService.createUser(request, "USR-ADM-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role specified");
    }

    @Test
    @DisplayName("Create User: Saves user with hashed password and records audit event")
    void shouldCreateUserAndRecordAuditLog() {
        CreateUserRequest request = new CreateUserRequest(
                "Dr. New Officer",
                "new.officer@aicte-india.gov.in",
                "Scrutiny Officer",
                "Secret123!",
                "Northern Region",
                null,
                null,
                "Scrutiny Reviewer"
        );

        when(userRepository.existsByEmailIgnoreCase("new.officer@aicte-india.gov.in")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-bcrypt-value");

        User savedUser = new User(
                "USR-SO-123456",
                "Dr. New Officer",
                "new.officer@aicte-india.gov.in",
                "hashed-bcrypt-value",
                "Scrutiny Officer",
                "ACTIVE",
                "Northern Region",
                null,
                null,
                "Scrutiny Reviewer"
        );
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = adminService.createUser(request, "USR-ADM-01");

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("USR-SO-123456");
        assertThat(response.email()).isEqualTo("new.officer@aicte-india.gov.in");

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());

        AuditEvent loggedAudit = auditCaptor.getValue();
        assertThat(loggedAudit.getEventType()).isEqualTo("USER_CREATED");
        assertThat(loggedAudit.getActorId()).isEqualTo("USR-ADM-01");
        assertThat(loggedAudit.getTargetEntityId()).isEqualTo("USR-SO-123456");
        assertThat(loggedAudit.getMetadataJson()).doesNotContain("Secret123!");
        assertThat(loggedAudit.getMetadataJson()).doesNotContain("hashed-bcrypt-value");
    }

    @Test
    @DisplayName("Update User: Prevents administrator from demoting self to prevent administrative lockout")
    void shouldPreventAdminSelfDemotion() {
        User adminUser = new User(
                "USR-ADM-01",
                "System Administrator",
                "admin@aicte-india.gov.in",
                "hash",
                "Administrator",
                "ACTIVE",
                "Platform-wide",
                null,
                null,
                "Tech Admin"
        );
        when(userRepository.findByUserId("USR-ADM-01")).thenReturn(Optional.of(adminUser));

        UpdateUserRequest demotionRequest = new UpdateUserRequest(
                "System Administrator",
                "admin@aicte-india.gov.in",
                "Regional Officer", // Attempted demotion
                "Platform-wide",
                null,
                null,
                "Tech Admin"
        );

        assertThatThrownBy(() -> adminService.updateUser("USR-ADM-01", demotionRequest, "USR-ADM-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot demote their own account");
    }

    @Test
    @DisplayName("Status Change: Prevents deactivating root system administrator (USR-ADM-01)")
    void shouldPreventRootAdminDeactivation() {
        User rootAdmin = new User(
                "USR-ADM-01",
                "System Administrator",
                "admin@aicte-india.gov.in",
                "hash",
                "Administrator",
                "ACTIVE",
                "Platform-wide",
                null,
                null,
                "Tech Admin"
        );
        when(userRepository.findByUserId("USR-ADM-01")).thenReturn(Optional.of(rootAdmin));

        UserStatusRequest deactivateReq = new UserStatusRequest("DEACTIVATED", "Deactivation attempt");

        assertThatThrownBy(() -> adminService.updateUserStatus("USR-ADM-01", deactivateReq, "USR-ADM-02"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USR-ADM-01) cannot be deactivated");
    }

    @Test
    @DisplayName("Status Change: Deactivating user invalidates active tokens and logs audit event")
    void shouldDeactivateUserAndInvalidateTokens() {
        User normalUser = new User(
                "USR-SO-01",
                "B. Singh",
                "so@aicte-india.gov.in",
                "hash",
                "Scrutiny Officer",
                "ACTIVE",
                "Northern Region",
                null,
                null,
                "Desk Officer"
        );
        when(userRepository.findByUserId("USR-SO-01")).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserStatusRequest deactivateReq = new UserStatusRequest("DEACTIVATED", "Terminated contractor role");

        UserResponse res = adminService.updateUserStatus("USR-SO-01", deactivateReq, "USR-ADM-01");

        assertThat(res.status()).isEqualTo("DEACTIVATED");
        verify(devTokenService).revokeTokensForUser("USR-SO-01");

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());

        AuditEvent event = auditCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("USER_DEACTIVATED");
        assertThat(event.getTargetEntityId()).isEqualTo("USR-SO-01");
        assertThat(event.getMetadataJson()).contains("Terminated contractor role");
    }
}
