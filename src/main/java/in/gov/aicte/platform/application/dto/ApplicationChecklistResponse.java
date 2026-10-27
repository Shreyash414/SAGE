package in.gov.aicte.platform.application.dto;

import java.time.Instant;
import java.util.List;

public record ApplicationChecklistResponse(
        Long applicationId,
        String applicationReference,
        String applicationType,
        String status,
        boolean submittable,
        int completionPercentage,
        int formCompletionPercentage,
        int documentCompletionPercentage,
        List<String> blockingDeficiencies,
        List<ChecklistSectionItem> sections,
        List<ChecklistDocumentItem> documents
) {
    public record ChecklistSectionItem(
            String id,
            String title,
            boolean complete,
            int totalFields,
            int completedFields,
            List<String> missingFields
    ) {}

    public record ChecklistDocumentItem(
            String requirementCode,
            String name,
            String aphClause,
            boolean mandatory,
            boolean uploaded,
            Long documentId,
            String fileName,
            Long fileSize,
            String fileHash,
            int version,
            Instant uploadedAt
    ) {}
}
