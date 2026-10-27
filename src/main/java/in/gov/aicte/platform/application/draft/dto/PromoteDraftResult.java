package in.gov.aicte.platform.application.draft.dto;

import java.time.Instant;
import java.util.List;

public record PromoteDraftResult(
        boolean success,
        Long draftId,
        String draftReference,
        Long officialApplicationId,
        String officialApplicationReference,
        Long applicationId,
        String applicationReference,
        String status,
        String currentStage,
        String ownerName,
        Instant submittedAt,
        String submittedBy,
        List<String> validationErrors
) {}
