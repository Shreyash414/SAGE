package in.gov.aicte.platform.application.draft.dto;

import java.util.Map;

public record CreateDraftRequest(
        String applicationType,
        String academicYear,
        Map<String, Object> initialFormData
) {}
