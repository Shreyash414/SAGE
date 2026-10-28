package in.gov.aicte.platform.evc.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEvcMemberProfileRequest(
        @Pattern(regexp = "AVAILABLE|UNAVAILABLE|ON_LEAVE", message = "Availability status must be AVAILABLE, UNAVAILABLE, or ON_LEAVE")
        String availabilityStatus,

        @Size(max = 32, message = "Phone number must not exceed 32 characters")
        String phone
) {
}

