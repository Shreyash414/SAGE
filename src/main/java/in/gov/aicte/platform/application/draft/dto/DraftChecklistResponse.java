package in.gov.aicte.platform.application.draft.dto;

import in.gov.aicte.platform.application.dto.ApplicationChecklistResponse.ChecklistDocumentItem;
import in.gov.aicte.platform.application.dto.ApplicationChecklistResponse.ChecklistSectionItem;

import java.util.List;

public record DraftChecklistResponse(
        Long draftId,
        String draftReference,
        String applicationType,
        String status,
        boolean submittable,
        int completionPercentage,
        int formCompletionPercentage,
        int documentCompletionPercentage,
        List<String> blockingDeficiencies,
        List<ChecklistSectionItem> sections,
        List<ChecklistDocumentItem> documents
) {}
