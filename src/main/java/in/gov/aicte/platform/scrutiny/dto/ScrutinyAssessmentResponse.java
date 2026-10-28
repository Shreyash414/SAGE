package in.gov.aicte.platform.scrutiny.dto;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;

import java.time.Instant;

public record ScrutinyAssessmentResponse(
        Long id,
        String assessmentReference,
        Long applicationId,
        String applicationReference,
        Long institutionId,
        String assessmentType,
        int cycleNumber,
        String status,
        String summaryOutcome,
        String rulesVersion,
        String assessedBy,
        int totalChecks,
        int passedChecks,
        int deficiencyChecks,
        int reviewChecks,
        String summaryNotes,
        Instant createdAt
) {
    public static ScrutinyAssessmentResponse from(ScrutinyAssessment assessment) {
        if (assessment == null) return null;
        return new ScrutinyAssessmentResponse(
                assessment.getId(),
                assessment.getAssessmentReference(),
                assessment.getApplication() != null ? assessment.getApplication().getId() : null,
                assessment.getApplication() != null ? assessment.getApplication().getApplicationReference() : null,
                assessment.getInstitutionId(),
                assessment.getAssessmentType(),
                assessment.getCycleNumber(),
                assessment.getStatus(),
                assessment.getSummaryOutcome(),
                assessment.getRulesVersion(),
                assessment.getAssessedBy(),
                assessment.getTotalChecks(),
                assessment.getPassedChecks(),
                assessment.getDeficiencyChecks(),
                assessment.getReviewChecks(),
                assessment.getSummaryNotes(),
                assessment.getCreatedAt()
        );
    }
}
