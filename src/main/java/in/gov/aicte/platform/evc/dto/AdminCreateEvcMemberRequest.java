package in.gov.aicte.platform.evc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateEvcMemberRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 128)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        String password,

        @NotBlank(message = "Professional designation is required")
        @Size(max = 128)
        String designation,

        @NotBlank(message = "Organization is required")
        @Size(max = 255)
        String organization,

        @NotBlank(message = "Expertise / discipline is required")
        @Size(max = 255)
        String expertise,

        @Size(max = 64)
        String region,

        @Size(max = 32)
        String phone
) {
}

