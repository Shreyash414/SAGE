package in.gov.aicte.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserStatusRequest(
        @NotBlank(message = "Target status is required")
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|DEACTIVATED)$", message = "Status must be ACTIVE, SUSPENDED, or DEACTIVATED")
        String status,

        @NotBlank(message = "Reason for status change is required for compliance audit")
        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason
) {
}
