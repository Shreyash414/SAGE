package in.gov.aicte.platform.application.catalogue;

import java.util.List;

public record FormSectionDefinition(
        String id,
        String title,
        String description,
        List<FormFieldDefinition> fields
) {}
