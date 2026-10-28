package in.gov.aicte.platform.evc.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignEvcMemberRequest(
        @NotNull(message = "EVC visit ID is required")
        Long evcVisitId,

        String committeeRole,

        @Size(max = 255, message = "Notes must not exceed 255 characters")
        String notes
) {
}

