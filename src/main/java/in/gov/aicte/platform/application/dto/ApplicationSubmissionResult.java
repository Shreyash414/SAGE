package in.gov.aicte.platform.application.dto;

import java.time.Instant;
import java.util.List;

public record ApplicationSubmissionResult(
        boolean success,
        Long applicationId,
        String applicationReference,
        String status,
        String currentStage,
        String ownerName,
        Instant submittedAt,
        String submittedBy,
        List<String> validationErrors
) {}
