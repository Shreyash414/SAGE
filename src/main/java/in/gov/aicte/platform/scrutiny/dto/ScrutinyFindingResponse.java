package in.gov.aicte.platform.scrutiny.dto;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record ScrutinyFindingResponse(
        Long id,
        Long assessmentId,
        Long applicationId,
        Long documentId,
        String documentName,
        String requirementCode,
        String ruleCode,
        String ruleName,
        String ruleReference,
        String findingType,
        String outcome,
        String severity,
        String expectedCondition,
        String actualValue,
        String explanation,
        String recommendedAction,
        String officerDecision,
        String officerRemarks,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        boolean isGrouped,
        List<String> affectedCategories,
        List<String> affectedCategoryNames,
        List<Long> affectedDocumentIds,
        List<ScrutinyFindingResponse> underlyingFindings
) {
    public ScrutinyFindingResponse(
            Long id,
            Long assessmentId,
            Long applicationId,
            Long documentId,
            String documentName,
            String requirementCode,
            String ruleCode,
            String ruleName,
            String ruleReference,
            String findingType,
            String outcome,
            String severity,
            String expectedCondition,
            String actualValue,
            String explanation,
            String recommendedAction,
            String officerDecision,
            String officerRemarks,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt
    ) {
        this(id, assessmentId, applicationId, documentId, documentName, requirementCode, ruleCode,
                ruleName, ruleReference, findingType, outcome, severity, expectedCondition,
                actualValue, explanation, recommendedAction, officerDecision, officerRemarks,
                reviewedBy, reviewedAt, createdAt, false, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static ScrutinyFindingResponse from(ScrutinyFinding f) {
        return from(f, f != null ? f.getDocumentName() : null);
    }

    public static ScrutinyFindingResponse from(ScrutinyFinding f, String documentName) {
        if (f == null) return null;
        String resolvedDocName = (documentName != null && !documentName.isBlank()) ? documentName : f.getDocumentName();
        return new ScrutinyFindingResponse(
                f.getId(),
                f.getAssessment() != null ? f.getAssessment().getId() : null,
                f.getApplicationId(),
                f.getDocumentId(),
                resolvedDocName,
                f.getRequirementCode(),
                f.getRuleCode(),
                f.getRuleName(),
                f.getRuleReference(),
                f.getFindingType(),
                f.getOutcome(),
                f.getSeverity(),
                f.getExpectedCondition(),
                f.getActualValue(),
                f.getExplanation(),
                f.getRecommendedAction(),
                f.getOfficerDecision(),
                f.getOfficerRemarks(),
                f.getReviewedBy(),
                f.getReviewedAt(),
                f.getCreatedAt()
        );
    }
}
