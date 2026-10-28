package in.gov.aicte.platform.evc.dto;

import jakarta.validation.constraints.Size;

public record AdminUpdateEvcMemberRequest(
        @Size(max = 128)
        String fullName,

        @Size(max = 128)
        String designation,

        @Size(max = 255)
        String organization,

        @Size(max = 255)
        String expertise,

        @Size(max = 32)
        String verificationStatus,

        @Size(max = 64)
        String availabilityStatus,

        @Size(max = 32)
        String phone,

        @Size(max = 64)
        String region
) {
}

