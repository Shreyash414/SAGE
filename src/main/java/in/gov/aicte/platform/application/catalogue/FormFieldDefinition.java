package in.gov.aicte.platform.application.catalogue;

import java.util.List;

public record FormFieldDefinition(
        String name,
        String label,
        String type, // text, number, select, textarea, checkbox, date
        boolean required,
        String placeholder,
        List<String> options,
        String helperText
) {}
