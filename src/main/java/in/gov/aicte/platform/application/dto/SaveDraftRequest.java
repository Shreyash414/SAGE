package in.gov.aicte.platform.application.dto;

import java.util.Map;

public record SaveDraftRequest(
        Map<String, Object> formData,
        String observations
) {}
