package in.gov.aicte.platform.application.dto;

import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;

import java.util.List;

public record ApplicationTypeSummaryResponse(
        String code,
        String name,
        String category,
        String aphClause,
        String description,
        List<String> applicableLevels,
        int sectionCount,
        int documentCount
) {
    public static ApplicationTypeSummaryResponse from(ApplicationTypeDefinition def) {
        return new ApplicationTypeSummaryResponse(
                def.code(),
                def.name(),
                def.category(),
                def.aphClause(),
                def.description(),
                def.applicableLevels(),
                def.sections().size(),
                def.documentRequirements().size()
        );
    }
}
