package in.gov.aicte.platform.scrutiny.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewFindingRequest(
        @NotBlank(message = "Decision is required (CONFIRMED_DEFICIENCY or DISMISSED)")
        String decision,

        String remarks,

        String customTitle,

        String customDescription,

        Boolean blocking,

        Boolean applyToAllDuplicates
) {
    public ReviewFindingRequest(String decision, String remarks, String customTitle, String customDescription, Boolean blocking) {
        this(decision, remarks, customTitle, customDescription, blocking, false);
    }

    public ReviewFindingRequest(String decision, String remarks, Boolean applyToAllDuplicates) {
        this(decision, remarks, null, null, null, applyToAllDuplicates);
    }
}
