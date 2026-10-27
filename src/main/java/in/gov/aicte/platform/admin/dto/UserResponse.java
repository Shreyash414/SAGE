package in.gov.aicte.platform.admin.dto;

import in.gov.aicte.platform.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String userId,
        String fullName,
        String email,
        String role,
        String status,
        String region,
        String institutionId,
        String institutionName,
        String designation,
        int failedLoginAttempts,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getRegion(),
                user.getInstitutionId(),
                user.getInstitutionName(),
                user.getDesignation(),
                user.getFailedLoginAttempts(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
