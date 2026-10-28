package in.gov.aicte.platform.scrutiny.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewDeficiencyRequest(
        @NotBlank(message = "Reviewer decision is required (VERIFIED_RESOLVED or REJECTED)")
        String decision,

        String remarks
) {
}
