package in.gov.aicte.platform.application.catalogue;

import java.util.List;

public record DocumentRequirementDefinition(
        String code,
        String name,
        String description,
        String aphClause,
        boolean mandatory,
        List<String> allowedMimeTypes,
        long maxSizeBytes
) {}
