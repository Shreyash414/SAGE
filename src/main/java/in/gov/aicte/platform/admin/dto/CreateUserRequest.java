package in.gov.aicte.platform.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 128, message = "Full name must not exceed 128 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 128, message = "Email must not exceed 128 characters")
        String email,

        @NotBlank(message = "Role is required")
        @Size(max = 64, message = "Role must not exceed 64 characters")
        String role,

        @Size(min = 8, message = "Password must be at least 8 characters if provided")
        String password,

        @Size(max = 64, message = "Region must not exceed 64 characters")
        String region,

        @Size(max = 64, message = "Institution ID must not exceed 64 characters")
        String institutionId,

        @Size(max = 255, message = "Institution name must not exceed 255 characters")
        String institutionName,

        @Size(max = 128, message = "Designation must not exceed 128 characters")
        String designation
) {
}
