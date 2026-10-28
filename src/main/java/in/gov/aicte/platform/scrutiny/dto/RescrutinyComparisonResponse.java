package in.gov.aicte.platform.scrutiny.dto;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;

import java.time.Instant;

public record RescrutinyComparisonResponse(
        Long id,
        Long assessmentId,
        String assessmentReference,
        Long deficiencyId,
        String deficiencyReference,
        String deficiencyTitle,
        String requirementCode,
        Long originalDocumentId,
        int originalDocumentVersion,
        String originalDocumentName,
        String originalExtractedValue,
        Long correctedDocumentId,
        int correctedDocumentVersion,
        String correctedDocumentName,
        String correctedExtractedValue,
        String comparisonStatus,
        String comparisonSummary,
        String remainingIssues,
        String officerActionRecommendation,
        Instant createdAt
) {
    public static RescrutinyComparisonResponse from(ScrutinyRescrutinyComparison c) {
        if (c == null) return null;
        String reqCode = null;
        if (c.getDeficiency() != null && c.getDeficiency().getRequirementCode() != null) {
            reqCode = c.getDeficiency().getRequirementCode();
        } else if (c.getOriginalDocument() != null) {
            reqCode = c.getOriginalDocument().getRequirementCode();
        } else if (c.getCorrectedDocument() != null) {
            reqCode = c.getCorrectedDocument().getRequirementCode();
        }

        String recAction;
        String status = c.getComparisonStatus() != null ? c.getComparisonStatus() : "";
        if ("RECTIFIED_PENDING_CONFIRMATION".equals(status)) {
            recAction = "Substantiated correction verified. Recommended to mark VERIFIED_RESOLVED upon officer confirmation.";
        } else if ("DEFICIENCY_PERSISTS".equals(status)) {
            recAction = "Deficiency unresolved. Maintain formal deficiency or require re-upload.";
        } else if ("NEW_DEFICIENCY_DETECTED".equals(status)) {
            recAction = "New deficiency flagged in corrected upload. Require immediate remediation.";
        } else {
            recAction = "Manual inspection of physical seals/signatures required by Scrutiny Officer.";
        }

        return new RescrutinyComparisonResponse(
                c.getId(),
                c.getAssessment() != null ? c.getAssessment().getId() : null,
                c.getAssessment() != null ? c.getAssessment().getAssessmentReference() : null,
                c.getDeficiency() != null ? c.getDeficiency().getId() : null,
                c.getDeficiency() != null ? c.getDeficiency().getDeficiencyReference() : null,
                c.getDeficiency() != null ? c.getDeficiency().getTitle() : null,
                reqCode,
                c.getOriginalDocument() != null ? c.getOriginalDocument().getId() : null,
                c.getOriginalDocumentVersion(),
                c.getOriginalDocument() != null ? c.getOriginalDocument().getDocumentName() : null,
                c.getOriginalExtractedValue(),
                c.getCorrectedDocument() != null ? c.getCorrectedDocument().getId() : null,
                c.getCorrectedDocumentVersion(),
                c.getCorrectedDocument() != null ? c.getCorrectedDocument().getDocumentName() : null,
                c.getCorrectedExtractedValue(),
                c.getComparisonStatus(),
                c.getComparisonSummary(),
                c.getRemainingIssues(),
                recAction,
                c.getCreatedAt()
        );
    }
}
