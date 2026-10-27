package in.gov.aicte.platform.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.admin.dto.AdminMetricsResponse;
import in.gov.aicte.platform.admin.dto.CreateUserRequest;
import in.gov.aicte.platform.admin.dto.UpdateUserRequest;
import in.gov.aicte.platform.admin.dto.UserDetailResponse;
import in.gov.aicte.platform.admin.dto.UserResponse;
import in.gov.aicte.platform.admin.dto.UserStatusRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.audit.AuditEventSpecifications;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.common.exception.DuplicateResourceException;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import in.gov.aicte.platform.user.UserSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "Administrator",
            "Institution Representative",
            "Regional Officer",
            "Scrutiny Officer",
            "EVC Coordinator",
            "Compliance Reviewer",
            "Senior Management"
    );

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final DevTokenService devTokenService;

    public AdminService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            DevTokenService devTokenService
    ) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.devTokenService = devTokenService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String search, String role, String status, String region, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String cleanRole = (role != null && !role.isBlank()) ? role.trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim() : null;
        String cleanRegion = (region != null && !region.isBlank()) ? region.trim() : null;

        return userRepository.findAll(
                UserSpecifications.withFilters(cleanSearch, cleanRole, cleanStatus, cleanRegion),
                pageable
        ).map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(String userId) {
        User user = findUserByIdOrUserId(userId);
        List<AuditEvent> recentAudits = auditEventRepository.findByTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc("USER", user.getUserId());
        return new UserDetailResponse(UserResponse.fromEntity(user), recentAudits);
    }

    public UserResponse createUser(CreateUserRequest request, String actingAdminId) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' already exists.");
        }

        String targetRole = request.role().trim();
        if (!ALLOWED_ROLES.contains(targetRole)) {
            throw new IllegalArgumentException("Invalid role specified. Permitted roles: " + ALLOWED_ROLES);
        }

        // Generate systematic User ID
        String prefix = getRolePrefix(targetRole);
        String generatedUserId = "USR-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password().trim()
                : "SAGE@" + UUID.randomUUID().toString().substring(0, 8);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = new User(
                generatedUserId,
                request.fullName().trim(),
                email,
                encodedPassword,
                targetRole,
                "ACTIVE",
                request.region() != null ? request.region().trim() : null,
                request.institutionId() != null ? request.institutionId().trim() : null,
                request.institutionName() != null ? request.institutionName().trim() : null,
                request.designation() != null ? request.designation().trim() : null
        );

        User savedUser = userRepository.save(newUser);

        // Record Audit Event
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("action", "CREATE_USER");
        auditMetadata.put("createdUserId", savedUser.getUserId());
        auditMetadata.put("fullName", savedUser.getFullName());
        auditMetadata.put("email", savedUser.getEmail());
        auditMetadata.put("role", savedUser.getRole());
        auditMetadata.put("region", savedUser.getRegion());
        auditMetadata.put("designation", savedUser.getDesignation());
        auditMetadata.put("status", savedUser.getStatus());

        recordAuditLog("USER_CREATED", actingAdminId, "USER", savedUser.getUserId(), "SUCCESS", auditMetadata);

        return UserResponse.fromEntity(savedUser);
    }

    public UserResponse updateUser(String userId, UpdateUserRequest request, String actingAdminId) {
        User user = findUserByIdOrUserId(userId);
        String newEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCaseAndUserIdNot(newEmail, user.getUserId())) {
            throw new DuplicateResourceException("Another account already uses email '" + newEmail + "'.");
        }

        String targetRole = request.role().trim();
        if (!ALLOWED_ROLES.contains(targetRole)) {
            throw new IllegalArgumentException("Invalid role specified. Permitted roles: " + ALLOWED_ROLES);
        }

        // Prevent self-lockout / self-demotion
        if (actingAdminId != null && actingAdminId.equalsIgnoreCase(user.getUserId()) && !"Administrator".equalsIgnoreCase(targetRole)) {
            throw new IllegalArgumentException("Administrators cannot demote their own account to prevent administrative lockout.");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!user.getFullName().equals(request.fullName().trim())) {
            changes.put("fullName", Map.of("before", user.getFullName(), "after", request.fullName().trim()));
            user.setFullName(request.fullName().trim());
        }
        if (!user.getEmail().equalsIgnoreCase(newEmail)) {
            changes.put("email", Map.of("before", user.getEmail(), "after", newEmail));
            user.setEmail(newEmail);
        }
        if (!user.getRole().equals(targetRole)) {
            changes.put("role", Map.of("before", user.getRole(), "after", targetRole));
            user.setRole(targetRole);
        }
        if (request.region() != null && !request.region().trim().equals(user.getRegion())) {
            changes.put("region", Map.of("before", String.valueOf(user.getRegion()), "after", request.region().trim()));
            user.setRegion(request.region().trim());
        }
        if (request.institutionId() != null && !request.institutionId().trim().equals(user.getInstitutionId())) {
            changes.put("institutionId", Map.of("before", String.valueOf(user.getInstitutionId()), "after", request.institutionId().trim()));
            user.setInstitutionId(request.institutionId().trim());
        }
        if (request.institutionName() != null && !request.institutionName().trim().equals(user.getInstitutionName())) {
            changes.put("institutionName", Map.of("before", String.valueOf(user.getInstitutionName()), "after", request.institutionName().trim()));
            user.setInstitutionName(request.institutionName().trim());
        }
        if (request.designation() != null && !request.designation().trim().equals(user.getDesignation())) {
            changes.put("designation", Map.of("before", String.valueOf(user.getDesignation()), "after", request.designation().trim()));
            user.setDesignation(request.designation().trim());
        }

        User updatedUser = userRepository.save(user);

        if (!changes.isEmpty()) {
            Map<String, Object> auditMetadata = new LinkedHashMap<>();
            auditMetadata.put("action", "UPDATE_USER");
            auditMetadata.put("targetUserId", updatedUser.getUserId());
            auditMetadata.put("fieldChanges", changes);

            recordAuditLog("USER_UPDATED", actingAdminId, "USER", updatedUser.getUserId(), "SUCCESS", auditMetadata);
        }

        return UserResponse.fromEntity(updatedUser);
    }

    public UserResponse updateUserStatus(String userId, UserStatusRequest request, String actingAdminId) {
        User user = findUserByIdOrUserId(userId);
        String targetStatus = request.status().toUpperCase().trim();

        // Prevent deactivating root administrator or self
        if ("USR-ADM-01".equalsIgnoreCase(user.getUserId()) && !"ACTIVE".equals(targetStatus)) {
            throw new IllegalArgumentException("The primary system administrator account (USR-ADM-01) cannot be deactivated or suspended.");
        }
        if (actingAdminId != null && actingAdminId.equalsIgnoreCase(user.getUserId()) && !"ACTIVE".equals(targetStatus)) {
            throw new IllegalArgumentException("Administrators cannot suspend or deactivate their own active account.");
        }

        String previousStatus = user.getStatus();
        user.setStatus(targetStatus);
        User savedUser = userRepository.save(user);

        // Invalidate in-memory tokens for suspended/deactivated users
        if (!"ACTIVE".equals(targetStatus)) {
            devTokenService.revokeTokensForUser(savedUser.getUserId());
        }

        String eventType = switch (targetStatus) {
            case "DEACTIVATED" -> "USER_DEACTIVATED";
            case "SUSPENDED" -> "USER_SUSPENDED";
            default -> "USER_ACTIVATED";
        };

        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("action", "CHANGE_USER_STATUS");
        auditMetadata.put("previousStatus", previousStatus);
        auditMetadata.put("newStatus", targetStatus);
        auditMetadata.put("reason", request.reason().trim());

        recordAuditLog(eventType, actingAdminId, "USER", savedUser.getUserId(), "SUCCESS", auditMetadata);

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> getAuditLogs(String eventType, String actorId, String targetEntityType, String targetEntityId, Pageable pageable) {
        String cleanEventType = (eventType != null && !eventType.isBlank()) ? eventType.trim() : null;
        String cleanActor = (actorId != null && !actorId.isBlank()) ? actorId.trim() : null;
        String cleanEntityType = (targetEntityType != null && !targetEntityType.isBlank()) ? targetEntityType.trim() : null;
        String cleanEntityId = (targetEntityId != null && !targetEntityId.isBlank()) ? targetEntityId.trim() : null;

        return auditEventRepository.findAll(
                AuditEventSpecifications.withFilters(cleanEventType, cleanActor, cleanEntityType, cleanEntityId),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminMetricsResponse getMetrics() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus("ACTIVE");
        long suspendedUsers = userRepository.countByStatus("SUSPENDED");
        long deactivatedUsers = userRepository.countByStatus("DEACTIVATED");
        long totalAuditEvents = auditEventRepository.count();

        Map<String, Long> roleCounts = new HashMap<>();
        for (String role : ALLOWED_ROLES) {
            roleCounts.put(role, userRepository.countByRole(role));
        }

        return new AdminMetricsResponse(
                totalUsers,
                activeUsers,
                suspendedUsers,
                deactivatedUsers,
                roleCounts,
                totalAuditEvents
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRecentActivity() {
        List<User> recentLogins = userRepository.findTop10ByLastLoginAtIsNotNullOrderByLastLoginAtDesc();
        List<AuditEvent> recentAudits = auditEventRepository.findTop15ByOrderByCreatedAtDesc();

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("recentLogins", recentLogins.stream().map(UserResponse::fromEntity).toList());
        activity.put("recentAuditEvents", recentAudits);
        return activity;
    }

    private User findUserByIdOrUserId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("User identifier cannot be empty.");
        }
        return userRepository.findByUserId(identifier)
                .or(() -> {
                    try {
                        Long id = Long.parseLong(identifier);
                        return userRepository.findById(id);
                    } catch (NumberFormatException e) {
                        return userRepository.findByEmailIgnoreCase(identifier);
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));
    }

    private void recordAuditLog(String eventType, String actorId, String targetEntityType, String targetEntityId, String outcome, Map<String, Object> metadata) {
        try {
            String json = metadata != null ? objectMapper.writeValueAsString(metadata) : "{}";
            AuditEvent auditEvent = new AuditEvent(
                    eventType,
                    actorId != null ? actorId : "SYSTEM",
                    targetEntityType,
                    targetEntityId,
                    outcome,
                    UUID.randomUUID().toString().substring(0, 8),
                    json
            );
            auditEventRepository.save(auditEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log metadata for event {}: {}", eventType, e.getMessage());
        }
    }

    private String getRolePrefix(String role) {
        return switch (role) {
            case "Administrator" -> "ADM";
            case "Institution Representative" -> "INST";
            case "Regional Officer" -> "RO";
            case "Scrutiny Officer" -> "SO";
            case "EVC Coordinator" -> "EVC";
            case "Compliance Reviewer" -> "CR";
            case "Senior Management" -> "MGT";
            default -> "USR";
        };
    }
}
