package in.gov.aicte.platform.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        Long institutionId,

        @Size(max = 64, message = "Institutional identifier must not exceed 64 characters")
        String institutionalIdentifier,

        @NotBlank(message = "Application reference is required")
        @Size(max = 64, message = "Application reference must not exceed 64 characters")
        String applicationReference,

        @NotBlank(message = "Application type is required")
        @Size(max = 64, message = "Application type must not exceed 64 characters")
        String applicationType,

        @NotBlank(message = "Academic year is required")
        @Size(max = 16, message = "Academic year must not exceed 16 characters")
        String academicYear
) {
    public CreateApplicationRequest(Long institutionId, String applicationReference, String applicationType, String academicYear) {
        this(institutionId, null, applicationReference, applicationType, academicYear);
    }

    public CreateApplicationRequest {
        if (institutionalIdentifier != null) {
            institutionalIdentifier = institutionalIdentifier.trim();
        }
        if (applicationReference != null) {
            applicationReference = applicationReference.trim();
        }
        if (applicationType != null) {
            applicationType = applicationType.trim();
        }
        if (academicYear != null) {
            academicYear = academicYear.trim();
        }
    }
}
