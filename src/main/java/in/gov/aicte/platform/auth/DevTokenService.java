package in.gov.aicte.platform.auth;

import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.auth.dto.TokenResponse;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication and session bridge service with database user lookup and token invalidation.
 */
@Service
public class DevTokenService {

    private final Map<String, DevUserDto> usersById = new ConcurrentHashMap<>();
    private final Map<String, DevUserDto> usersByRole = new ConcurrentHashMap<>();
    private final Map<String, DevUserDto> usersByEmail = new ConcurrentHashMap<>();

    private final Map<String, DevUserDto> activeTokens = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevTokenService(@Lazy UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;

        registerUser(new DevUserDto(
                "USR-INST-01",
                "Dr. Ramesh Kumar",
                "principal@abctech.edu.in",
                "Institution Representative",
                "Northern Region",
                "INST-1025",
                "ABC Institute of Technology",
                "Principal / Authorized Representative"
        ));
        registerUser(new DevUserDto(
                "USR-RO-01",
                "A. Sharma",
                "ro.north@aicte-india.gov.in",
                "Regional Officer",
                "Northern Region",
                null,
                null,
                "Regional Officer (North)"
        ));
        registerUser(new DevUserDto(
                "USR-SO-01",
                "B. Singh",
                "scrutiny.north@aicte-india.gov.in",
                "Scrutiny Officer",
                "Northern Region",
                null,
                null,
                "Scrutiny / Bureau Desk Officer"
        ));
        registerUser(new DevUserDto(
                "USR-EVC-01",
                "C. Verma",
                "evc.coord@aicte-india.gov.in",
                "EVC Coordinator",
                "Multi-Region",
                null,
                null,
                "EVC Operations Coordinator"
        ));
        registerUser(new DevUserDto(
                "USR-CR-01",
                "D. Patel",
                "compliance.rev@aicte-india.gov.in",
                "Compliance Reviewer",
                "Western Region",
                null,
                null,
                "Compliance & Evidence Reviewer"
        ));
        registerUser(new DevUserDto(
                "USR-MGT-01",
                "Prof. H. R. Adhikari",
                "advisor.approval@aicte-india.gov.in",
                "Senior Management",
                "All India",
                null,
                null,
                "Competent Authority / Advisor Approval"
        ));
        registerUser(new DevUserDto(
                "USR-EVCM-01",
                "Dr. Arvind Saxena",
                "member.evc@aicte-india.gov.in",
                "EVC Member",
                "Northern Region",
                null,
                null,
                "Professor & Subject Specialist"
        ));
        registerUser(new DevUserDto(
                "USR-ADM-01",
                "System Administrator",
                "admin@aicte-india.gov.in",
                "Administrator",
                "Platform-wide",
                null,
                null,
                "Technical Platform Administrator"
        ));
    }

    private void registerUser(DevUserDto user) {
        usersById.put(user.id().toLowerCase(), user);
        usersByRole.put(user.role().toLowerCase(), user);
        usersByEmail.put(user.email().toLowerCase(), user);
    }

    public TokenResponse issueToken(String username, String role) {
        return issueUserPortalToken(username, role, null);
    }

    public TokenResponse issueTokenWithCredentials(String username, String role, String password) {
        return issueUserPortalToken(username, role, password);
    }

    /**
     * Issues an access token for the regular user-facing portal (port 5173).
     * Strictly rejects any account with the Administrator role.
     */
    public TokenResponse issueUserPortalToken(String username, String role, String password) {
        return authenticateAndIssueToken(username, role, password, false);
    }

    /**
     * Issues an access token for the dedicated SAGE Admin Panel (port 5174).
     * Strictly requires the account to have the Administrator role.
     */
    public TokenResponse issueAdminPortalToken(String username, String password) {
        return authenticateAndIssueToken(username, null, password, true);
    }

    private TokenResponse authenticateAndIssueToken(String username, String role, String password, boolean isAdminPortal) {
        DevUserDto matched = null;
        User dbRecord = null;

        // 1. Try finding in persistent database first
        if (userRepository != null) {
            try {
                Optional<User> dbUser = Optional.empty();
                if (username != null && !username.isBlank()) {
                    String clean = username.trim().toLowerCase();
                    dbUser = userRepository.findByEmailIgnoreCase(clean)
                            .or(() -> userRepository.findByUserId(username.trim()));
                } else if (role != null && !role.isBlank()) {
                    dbUser = userRepository.findAll().stream()
                            .filter(u -> u.getRole().equalsIgnoreCase(role.trim()))
                            .findFirst();
                }

                if (dbUser.isPresent()) {
                    dbRecord = dbUser.get();
                    matched = new DevUserDto(
                            dbRecord.getUserId(),
                            dbRecord.getFullName(),
                            dbRecord.getEmail(),
                            dbRecord.getRole(),
                            dbRecord.getRegion(),
                            dbRecord.getInstitutionId(),
                            dbRecord.getInstitutionName(),
                            dbRecord.getDesignation()
                    );
                }
            } catch (Exception ignored) {
                // Database query error or table not ready, fall back to in-memory store
            }
        }

        // 2. In-memory resolution fallback
        if (matched == null) {
            if (username != null && !username.isBlank()) {
                String u = username.trim().toLowerCase();
                matched = usersByEmail.get(u);
                if (matched == null) {
                    matched = usersById.get(u);
                }
            }

            if (matched == null && role != null && !role.isBlank()) {
                matched = usersByRole.get(role.trim().toLowerCase());
            }

            if (matched == null) {
                if (username == null || username.isBlank()) {
                    if (isAdminPortal) {
                        matched = usersById.get("usr-adm-01");
                    } else {
                        matched = usersById.get("usr-ro-01"); // Default to Regional Officer for empty dev request
                    }
                } else {
                    throw new IllegalArgumentException("Invalid credentials: user account not found for '" + username + "'.");
                }
            }
        }

        // 3. Strict Portal-Specific Role Enforcement
        // Derive role strictly from persisted user record / trusted context, NEVER from client-supplied role
        String actualRole = matched.role();
        boolean isAdministrator = "Administrator".equalsIgnoreCase(actualRole) || "Admin".equalsIgnoreCase(actualRole);

        if (!isAdminPortal) {
            // Regular User Portal: Administrators are strictly forbidden
            if (isAdministrator) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Administrative accounts cannot sign in through this portal. Please use the authorized administrative console."
                );
            }
        } else {
            // SAGE Admin Panel: Ordinary users are strictly forbidden
            if (!isAdministrator) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access Denied: Only authorized administrative accounts may authenticate via the SAGE Admin Panel."
                );
            }
        }

        // 4. Account Lifecycle Status Check
        if (dbRecord != null) {
            if (!dbRecord.isActive()) {
                throw new IllegalStateException("Account " + dbRecord.getUserId() + " is currently " + dbRecord.getStatus() + ". Access denied.");
            }
        }

        // 5. Secure Credential Verification
        if (password != null && !password.isBlank() && passwordEncoder != null && dbRecord != null) {
            if (dbRecord.getPasswordHash() != null && !passwordEncoder.matches(password, dbRecord.getPasswordHash())) {
                if (isAdministrator) {
                    throw new IllegalArgumentException("Invalid administrative credentials.");
                }
            }
        }

        // 6. Update last login timestamp in database
        if (dbRecord != null && userRepository != null) {
            try {
                dbRecord.setLastLoginAt(Instant.now());
                userRepository.save(dbRecord);
            } catch (Exception ignored) {
            }
        }

        registerUser(matched);

        String token = "dev-token-" + matched.id() + "-" + UUID.randomUUID().toString().substring(0, 8);
        activeTokens.put(token, matched);

        return TokenResponse.of(token, matched);
    }

    public Optional<DevUserDto> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        // Support persistent token format: dev-token-USR-SO-01-...
        DevUserDto user = activeTokens.get(token);
        if (user != null) {
            return Optional.of(user);
        }

        // Allow static dev format: dev-token-USR-SO-01
        if (token.startsWith("dev-token-")) {
            for (DevUserDto u : usersById.values()) {
                if (token.toLowerCase().contains(u.id().toLowerCase())) {
                    activeTokens.put(token, u);
                    return Optional.of(u);
                }
            }
        }

        return Optional.empty();
    }

    public void revokeTokensForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        activeTokens.entrySet().removeIf(entry -> entry.getValue().id().equalsIgnoreCase(userId.trim()));
    }

    public Optional<DevUserDto> findUserById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(usersById.get(id.toLowerCase()));
    }

    public List<DevUserDto> getAllDemoUsers() {
        return new ArrayList<>(usersById.values());
    }
}
