package in.gov.aicte.platform.scrutiny.dto;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;

import java.time.Instant;
import java.time.LocalDate;

public record ScrutinyDeficiencyResponse(
        Long id,
        String deficiencyReference,
        Long applicationId,
        String applicationReference,
        Long assessmentId,
        Long findingId,
        String requirementCode,
        String title,
        String description,
        String ruleReference,
        Long associatedDocumentId,
        String associatedDocumentName,
        int associatedDocumentVersion,
        String originalEvidenceText,
        String officerObservation,
        String status,
        boolean blocking,
        LocalDate dueDate,
        String institutionExplanation,
        Long correctedDocumentId,
        String correctedDocumentName,
        Integer correctedDocumentVersion,
        String reviewerDecision,
        String reviewerRemarks,
        String issuedBy,
        Instant issuedAt,
        Instant respondedAt,
        Instant resolvedAt,
        Instant updatedAt
) {
    public static ScrutinyDeficiencyResponse from(ScrutinyDeficiency d) {
        if (d == null) return null;
        return new ScrutinyDeficiencyResponse(
                d.getId(),
                d.getDeficiencyReference(),
                d.getApplication() != null ? d.getApplication().getId() : null,
                d.getApplication() != null ? d.getApplication().getApplicationReference() : null,
                d.getAssessment() != null ? d.getAssessment().getId() : null,
                d.getFinding() != null ? d.getFinding().getId() : null,
                d.getRequirementCode(),
                d.getTitle(),
                d.getDescription(),
                d.getRuleReference(),
                d.getAssociatedDocument() != null ? d.getAssociatedDocument().getId() : null,
                d.getAssociatedDocument() != null ? d.getAssociatedDocument().getDocumentName() : null,
                d.getAssociatedDocumentVersion(),
                d.getOriginalEvidenceText(),
                d.getOfficerObservation(),
                d.getStatus(),
                d.isBlocking(),
                d.getDueDate(),
                d.getInstitutionExplanation(),
                d.getCorrectedDocument() != null ? d.getCorrectedDocument().getId() : null,
                d.getCorrectedDocument() != null ? d.getCorrectedDocument().getDocumentName() : null,
                d.getCorrectedDocumentVersion(),
                d.getReviewerDecision(),
                d.getReviewerRemarks(),
                d.getIssuedBy(),
                d.getIssuedAt(),
                d.getRespondedAt(),
                d.getResolvedAt(),
                d.getUpdatedAt()
        );
    }
}
