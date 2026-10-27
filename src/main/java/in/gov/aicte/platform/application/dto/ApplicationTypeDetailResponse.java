package in.gov.aicte.platform.application.dto;

import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;
import in.gov.aicte.platform.application.catalogue.DocumentRequirementDefinition;
import in.gov.aicte.platform.application.catalogue.FormSectionDefinition;

import java.util.List;

public record ApplicationTypeDetailResponse(
        String code,
        String name,
        String category,
        String aphClause,
        String description,
        List<String> applicableLevels,
        List<FormSectionDefinition> sections,
        List<DocumentRequirementDefinition> documentRequirements
) {
    public static ApplicationTypeDetailResponse from(ApplicationTypeDefinition def) {
        return new ApplicationTypeDetailResponse(
                def.code(),
                def.name(),
                def.category(),
                def.aphClause(),
                def.description(),
                def.applicableLevels(),
                def.sections(),
                def.documentRequirements()
        );
    }
}
