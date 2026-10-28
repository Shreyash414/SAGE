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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.gov.aicte.platform.evc.EvcMemberAssignment;
import in.gov.aicte.platform.evc.EvcMemberAssignmentRepository;
import in.gov.aicte.platform.evc.EvcMemberProfile;
import in.gov.aicte.platform.evc.EvcMemberProfileRepository;
import in.gov.aicte.platform.evc.EvcVisit;
import in.gov.aicte.platform.evc.EvcVisitRepository;
import in.gov.aicte.platform.evc.dto.AdminCreateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AdminUpdateEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.AssignEvcMemberRequest;
import in.gov.aicte.platform.evc.dto.EvcMemberAssignmentResponse;
import in.gov.aicte.platform.evc.dto.EvcMemberProfileResponse;

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
            "Senior Management",
            "EVC Member"
    );

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final DevTokenService devTokenService;
    private final EvcMemberProfileRepository evcMemberProfileRepository;
    private final EvcMemberAssignmentRepository evcMemberAssignmentRepository;
    private final EvcVisitRepository evcVisitRepository;

    public AdminService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            DevTokenService devTokenService
    ) {
        this(userRepository, auditEventRepository, passwordEncoder, objectMapper, devTokenService, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            DevTokenService devTokenService,
            @org.springframework.context.annotation.Lazy EvcMemberProfileRepository evcMemberProfileRepository,
            @org.springframework.context.annotation.Lazy EvcMemberAssignmentRepository evcMemberAssignmentRepository,
            @org.springframework.context.annotation.Lazy EvcVisitRepository evcVisitRepository
    ) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.devTokenService = devTokenService;
        this.evcMemberProfileRepository = evcMemberProfileRepository;
        this.evcMemberAssignmentRepository = evcMemberAssignmentRepository;
        this.evcVisitRepository = evcVisitRepository;
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

        if ("EVC Member".equalsIgnoreCase(targetRole) && evcMemberProfileRepository != null) {
            String memberId = "MEM-" + generatedUserId;
            EvcMemberProfile profile = new EvcMemberProfile(
                    memberId,
                    savedUser,
                    savedUser.getFullName(),
                    savedUser.getDesignation() != null ? savedUser.getDesignation() : "Expert Committee Member",
                    savedUser.getInstitutionName() != null ? savedUser.getInstitutionName() : "AICTE Expert Panel",
                    "Technical Committee Evaluation",
                    "VERIFIED",
                    "AVAILABLE",
                    null
            );
            evcMemberProfileRepository.save(profile);
        }

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

    @Transactional(readOnly = true)
    public List<EvcMemberProfileResponse> listEvcMembers(String search, String status, String region) {
        if (evcMemberProfileRepository == null) return Collections.emptyList();
        List<EvcMemberProfile> profiles = evcMemberProfileRepository.findAll();
        return profiles.stream()
                .filter(p -> {
                    if (status != null && !status.isBlank()) {
                        if (!status.equalsIgnoreCase(p.getVerificationStatus()) && !status.equalsIgnoreCase(p.getAvailabilityStatus())) {
                            return false;
                        }
                    }
                    if (region != null && !region.isBlank()) {
                        String userRegion = p.getUser() != null ? p.getUser().getRegion() : null;
                        if (userRegion == null || !userRegion.equalsIgnoreCase(region.trim())) {
                            return false;
                        }
                    }
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase();
                        boolean matchName = p.getFullName() != null && p.getFullName().toLowerCase().contains(s);
                        boolean matchEmail = p.getUser() != null && p.getUser().getEmail() != null && p.getUser().getEmail().toLowerCase().contains(s);
                        boolean matchOrg = p.getOrganization() != null && p.getOrganization().toLowerCase().contains(s);
                        boolean matchExp = p.getExpertise() != null && p.getExpertise().toLowerCase().contains(s);
                        boolean matchMemId = p.getMemberId() != null && p.getMemberId().toLowerCase().contains(s);
                        if (!matchName && !matchEmail && !matchOrg && !matchExp && !matchMemId) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(p -> {
                    long assignedCount = evcMemberAssignmentRepository != null ? evcMemberAssignmentRepository.countByMemberId(p.getId()) : 0;
                    return EvcMemberProfileResponse.from(p, assignedCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EvcMemberProfileResponse getEvcMemberDetail(String idOrMemberId) {
        EvcMemberProfile profile = findEvcMemberProfile(idOrMemberId);
        long count = evcMemberAssignmentRepository != null ? evcMemberAssignmentRepository.countByMemberId(profile.getId()) : 0;
        return EvcMemberProfileResponse.from(profile, count);
    }

    public EvcMemberProfileResponse createEvcMember(AdminCreateEvcMemberRequest request, String actingAdminId) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' already exists.");
        }

        String generatedUserId = "USR-EVCM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password().trim()
                : "SAGE@" + UUID.randomUUID().toString().substring(0, 8);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = new User(
                generatedUserId,
                request.fullName().trim(),
                email,
                encodedPassword,
                "EVC Member",
                "ACTIVE",
                request.region() != null ? request.region().trim() : "Northern Region",
                null,
                request.organization().trim(),
                request.designation().trim()
        );
        User savedUser = userRepository.save(newUser);

        String memberId = "MEM-" + generatedUserId;
        EvcMemberProfile profile = new EvcMemberProfile(
                memberId,
                savedUser,
                request.fullName().trim(),
                request.designation().trim(),
                request.organization().trim(),
                request.expertise().trim(),
                "VERIFIED",
                "AVAILABLE",
                request.phone() != null ? request.phone().trim() : null
        );
        EvcMemberProfile savedProfile = evcMemberProfileRepository.save(profile);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("action", "CREATE_EVC_MEMBER");
        meta.put("memberId", memberId);
        meta.put("userId", generatedUserId);
        meta.put("email", email);
        recordAuditLog("EVC_MEMBER_CREATED", actingAdminId, "EVC_MEMBER", memberId, "SUCCESS", meta);

        return EvcMemberProfileResponse.from(savedProfile, 0);
    }

    public EvcMemberProfileResponse updateEvcMemberProfile(String idOrMemberId, AdminUpdateEvcMemberRequest request, String actingAdminId) {
        EvcMemberProfile profile = findEvcMemberProfile(idOrMemberId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName().trim());
            if (profile.getUser() != null) {
                profile.getUser().setFullName(request.fullName().trim());
            }
        }
        if (request.designation() != null && !request.designation().isBlank()) {
            profile.setDesignation(request.designation().trim());
            if (profile.getUser() != null) {
                profile.getUser().setDesignation(request.designation().trim());
            }
        }
        if (request.organization() != null && !request.organization().isBlank()) {
            profile.setOrganization(request.organization().trim());
        }
        if (request.expertise() != null && !request.expertise().isBlank()) {
            profile.setExpertise(request.expertise().trim());
        }
        if (request.verificationStatus() != null && !request.verificationStatus().isBlank()) {
            profile.setVerificationStatus(request.verificationStatus().trim().toUpperCase());
        }
        if (request.availabilityStatus() != null && !request.availabilityStatus().isBlank()) {
            profile.setAvailabilityStatus(request.availabilityStatus().trim().toUpperCase());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone().trim());
        }
        if (request.region() != null && profile.getUser() != null) {
            profile.getUser().setRegion(request.region().trim());
        }

        EvcMemberProfile saved = evcMemberProfileRepository.save(profile);
        if (profile.getUser() != null) {
            userRepository.save(profile.getUser());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("action", "UPDATE_EVC_MEMBER");
        meta.put("memberId", profile.getMemberId());
        recordAuditLog("EVC_MEMBER_UPDATED", actingAdminId, "EVC_MEMBER", profile.getMemberId(), "SUCCESS", meta);

        long count = evcMemberAssignmentRepository != null ? evcMemberAssignmentRepository.countByMemberId(saved.getId()) : 0;
        return EvcMemberProfileResponse.from(saved, count);
    }

    public EvcMemberAssignmentResponse assignMemberToVisit(String memberId, AssignEvcMemberRequest request, String actingAdminId) {
        EvcMemberProfile profile = findEvcMemberProfile(memberId);
        EvcVisit visit = evcVisitRepository.findById(request.evcVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("EVC visit not found with ID: " + request.evcVisitId()));

        if (evcMemberAssignmentRepository.existsByEvcVisitIdAndMemberId(visit.getId(), profile.getId())) {
            throw new DuplicateResourceException("Member " + profile.getMemberId() + " is already assigned to EVC visit " + visit.getEvcReference());
        }

        EvcMemberAssignment assignment = new EvcMemberAssignment(
                visit,
                profile,
                request.committeeRole() != null ? request.committeeRole().trim() : "MEMBER",
                request.notes() != null ? request.notes().trim() : null
        );
        EvcMemberAssignment saved = evcMemberAssignmentRepository.save(assignment);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("action", "ASSIGN_EVC_MEMBER");
        meta.put("memberId", profile.getMemberId());
        meta.put("evcVisitId", visit.getId());
        meta.put("evcReference", visit.getEvcReference());
        recordAuditLog("EVC_MEMBER_ASSIGNED", actingAdminId, "EVC_MEMBER", profile.getMemberId(), "SUCCESS", meta);

        return EvcMemberAssignmentResponse.from(saved);
    }

    public void removeMemberFromVisit(String memberId, Long visitId, String actingAdminId) {
        EvcMemberProfile profile = findEvcMemberProfile(memberId);
        EvcMemberAssignment assignment = evcMemberAssignmentRepository.findByEvcVisitIdAndMemberId(visitId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found for member " + memberId + " and visit " + visitId));

        evcMemberAssignmentRepository.delete(assignment);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("action", "UNASSIGN_EVC_MEMBER");
        meta.put("memberId", profile.getMemberId());
        meta.put("evcVisitId", visitId);
        recordAuditLog("EVC_MEMBER_UNASSIGNED", actingAdminId, "EVC_MEMBER", profile.getMemberId(), "SUCCESS", meta);
    }

    @Transactional(readOnly = true)
    public List<EvcMemberAssignmentResponse> listMemberAssignments(String memberId) {
        EvcMemberProfile profile = findEvcMemberProfile(memberId);
        return evcMemberAssignmentRepository.findByMemberId(profile.getId())
                .stream()
                .map(EvcMemberAssignmentResponse::from)
                .toList();
    }

    private EvcMemberProfile findEvcMemberProfile(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("EVC member identifier cannot be empty.");
        }
        if (evcMemberProfileRepository == null) {
            throw new ResourceNotFoundException("EVC Member repository not configured.");
        }
        return evcMemberProfileRepository.findByMemberId(identifier.trim())
                .or(() -> evcMemberProfileRepository.findByUserUserId(identifier.trim()))
                .or(() -> {
                    try {
                        Long id = Long.parseLong(identifier.trim());
                        return evcMemberProfileRepository.findById(id);
                    } catch (NumberFormatException e) {
                        return evcMemberProfileRepository.findByUserEmailIgnoreCase(identifier.trim());
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("EVC member profile not found for: " + identifier));
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
            case "EVC Member" -> "EVCM";
            default -> "USR";
        };
    }
}
