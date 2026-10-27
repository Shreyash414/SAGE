package in.gov.aicte.platform.application.draft.dto;

import in.gov.aicte.platform.application.dto.InstitutionSummaryResponse;

import java.time.Instant;
import java.util.Map;

public record DraftResponse(
        Long id,
        String draftReference,
        String applicationType,
        String academicYear,
        Map<String, Object> formData,
        String status,
        InstitutionSummaryResponse institution,
        String applicantId,
        int completionPercentage,
        int formCompletionPercentage,
        int documentCompletionPercentage,
        boolean submittable,
        Long promotedApplicationId,
        String promotedApplicationReference,
        Instant createdAt,
        Instant updatedAt,
        String requirementsVersion
) {}
