package in.gov.aicte.platform.evc.dto;

import in.gov.aicte.platform.evc.EvcMemberProfile;
import in.gov.aicte.platform.user.User;

import java.time.Instant;

public record EvcMemberProfileResponse(
        Long id,
        String memberId,
        String userId,
        String fullName,
        String email,
        String role,
        String status,
        String designation,
        String organization,
        String expertise,
        String verificationStatus,
        String availabilityStatus,
        String phone,
        String region,
        long assignedVisitsCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static EvcMemberProfileResponse from(EvcMemberProfile profile, long assignedCount) {
        User user = profile.getUser();
        return new EvcMemberProfileResponse(
                profile.getId(),
                profile.getMemberId(),
                user != null ? user.getUserId() : null,
                profile.getFullName(),
                user != null ? user.getEmail() : null,
                user != null ? user.getRole() : "EVC Member",
                user != null ? user.getStatus() : "ACTIVE",
                profile.getDesignation(),
                profile.getOrganization(),
                profile.getExpertise(),
                profile.getVerificationStatus(),
                profile.getAvailabilityStatus(),
                profile.getPhone(),
                user != null ? user.getRegion() : null,
                assignedCount,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}

