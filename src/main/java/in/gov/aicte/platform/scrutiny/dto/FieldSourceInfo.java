package in.gov.aicte.platform.scrutiny.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldSourceInfo(
        String fieldKey,
        String fieldLabel,
        Object value,
        String source,
        String sourceLabel,
        String ruleOrPattern,
        String evidenceSnippet
) {
    public static final String SOURCE_ACTUAL_DOCUMENT_CONTENT = "ACTUAL_DOCUMENT_CONTENT";
    public static final String SOURCE_FILE_METADATA = "FILE_METADATA";
    public static final String SOURCE_APPLICATION_METADATA = "APPLICATION_METADATA";
    public static final String SOURCE_DERIVED_VALUE = "DERIVED_VALUE";

    public static FieldSourceInfo content(String key, String label, Object val, String rule, String evidence) {
        return new FieldSourceInfo(key, label, val, SOURCE_ACTUAL_DOCUMENT_CONTENT, "Actual Document Content", rule, evidence);
    }

    public static FieldSourceInfo fileMeta(String key, String label, Object val, String rule, String evidence) {
        return new FieldSourceInfo(key, label, val, SOURCE_FILE_METADATA, "File Metadata", rule, evidence);
    }

    public static FieldSourceInfo appMeta(String key, String label, Object val, String rule, String evidence) {
        return new FieldSourceInfo(key, label, val, SOURCE_APPLICATION_METADATA, "Application Metadata", rule, evidence);
    }

    public static FieldSourceInfo derived(String key, String label, Object val, String rule, String evidence) {
        return new FieldSourceInfo(key, label, val, SOURCE_DERIVED_VALUE, "Derived Value", rule, evidence);
    }
}
