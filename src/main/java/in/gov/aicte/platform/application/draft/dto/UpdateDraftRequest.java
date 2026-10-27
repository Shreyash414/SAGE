package in.gov.aicte.platform.application.draft.dto;

import java.util.Map;

public record UpdateDraftRequest(
        Map<String, Object> formData,
        String academicYear
) {}
