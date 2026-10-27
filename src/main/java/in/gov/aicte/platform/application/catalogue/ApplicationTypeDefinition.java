package in.gov.aicte.platform.application.catalogue;

import java.util.List;

public record ApplicationTypeDefinition(
        String code,
        String name,
        String category,
        String aphClause,
        String description,
        List<String> applicableLevels,
        List<FormSectionDefinition> sections,
        List<DocumentRequirementDefinition> documentRequirements
) {}
