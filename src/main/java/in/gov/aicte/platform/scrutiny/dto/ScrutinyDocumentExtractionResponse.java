package in.gov.aicte.platform.scrutiny.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScrutinyDocumentExtractionResponse(
        Long id,
        Long assessmentId,
        Long applicationId,
        Long documentId,
        String requirementCode,
        String documentName,
        int documentVersion,
        String extractionMethod,
        String extractedTextSnippet,
        String extractedFieldsJson,
        Double confidenceScore,
        String ocrStatus,
        String errorMessage,
        Integer textCharacterCount,
        Integer pageCount,
        String validationMetric,
        Instant createdAt,
        String originalFileName,
        String storagePath,
        String fileHash,
        Long fileSize,
        String contentType,
        String assessmentReference,
        Integer cycleNumber,
        String downloadUrl,
        Map<String, FieldSourceInfo> fieldSources
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ScrutinyDocumentExtractionResponse(
            Long id,
            Long assessmentId,
            Long applicationId,
            Long documentId,
            String requirementCode,
            String documentName,
            int documentVersion,
            String extractionMethod,
            String extractedTextSnippet,
            String extractedFieldsJson,
            Double confidenceScore,
            String ocrStatus,
            String errorMessage,
            Integer textCharacterCount,
            Integer pageCount,
            String validationMetric,
            Instant createdAt
    ) {
        this(
                id, assessmentId, applicationId, documentId, requirementCode, documentName,
                documentVersion, extractionMethod, extractedTextSnippet, extractedFieldsJson,
                confidenceScore, ocrStatus, errorMessage, textCharacterCount, pageCount,
                validationMetric, createdAt,
                null, null, null, null, null, null, null, null, Collections.emptyMap()
        );
    }

    public ScrutinyDocumentExtractionResponse(
            Long id,
            Long assessmentId,
            Long applicationId,
            Long documentId,
            String requirementCode,
            String documentName,
            int documentVersion,
            String extractionMethod,
            String extractedTextSnippet,
            String extractedFieldsJson,
            Double confidenceScore,
            String ocrStatus,
            String errorMessage,
            Instant createdAt
    ) {
        this(
                id, assessmentId, applicationId, documentId, requirementCode, documentName,
                documentVersion, extractionMethod, extractedTextSnippet, extractedFieldsJson,
                confidenceScore, ocrStatus, errorMessage,
                extractedTextSnippet != null ? extractedTextSnippet.length() : 0,
                1,
                confidenceScore != null ? String.format("OCR_MEASURED (%.1f%%)", confidenceScore * 100.0) : "N/A (Embedded Text)",
                createdAt
        );
    }

    public static ScrutinyDocumentExtractionResponse from(ScrutinyDocumentExtraction ext) {
        if (ext == null) return null;

        ApplicationDocument doc = ext.getDocument();
        ScrutinyAssessment asmt = ext.getAssessment();

        String originalFileName = doc != null ? doc.getOriginalFileName() : null;
        String storagePath = doc != null ? doc.getStoragePath() : null;
        String fileHash = doc != null ? doc.getFileHash() : null;
        Long fileSize = doc != null ? doc.getFileSize() : null;
        String contentType = doc != null ? doc.getContentType() : null;
        int version = doc != null ? doc.getVersion() : ext.getDocumentVersion();

        String asmtRef = asmt != null ? asmt.getAssessmentReference() : null;
        Integer cycleNumber = asmt != null ? asmt.getCycleNumber() : 1;

        String downloadUrl = (ext.getApplicationId() != null && doc != null && doc.getId() != null)
                ? "/api/v1/applications/" + ext.getApplicationId() + "/documents/" + doc.getId() + "?inline=true"
                : null;

        Map<String, FieldSourceInfo> fieldSources = buildFieldSources(ext, doc, asmt);

        return new ScrutinyDocumentExtractionResponse(
                ext.getId(),
                asmt != null ? asmt.getId() : null,
                ext.getApplicationId(),
                doc != null ? doc.getId() : null,
                ext.getRequirementCode(),
                ext.getDocumentName(),
                version,
                ext.getExtractionMethod(),
                ext.getExtractedTextSnippet(),
                ext.getExtractedFieldsJson(),
                ext.getConfidenceScore(),
                ext.getOcrStatus(),
                ext.getErrorMessage(),
                ext.getTextCharacterCount() != null ? ext.getTextCharacterCount() : (ext.getExtractedTextSnippet() != null ? ext.getExtractedTextSnippet().length() : 0),
                ext.getPageCount() != null ? ext.getPageCount() : 1,
                ext.getValidationMetric() != null ? ext.getValidationMetric() : (ext.getConfidenceScore() != null ? String.format("OCR_MEASURED (%.1f%%)", ext.getConfidenceScore() * 100.0) : "N/A (Embedded Text)"),
                ext.getCreatedAt(),
                originalFileName,
                storagePath,
                fileHash,
                fileSize,
                contentType,
                asmtRef,
                cycleNumber,
                downloadUrl,
                fieldSources
        );
    }

    private static Map<String, FieldSourceInfo> buildFieldSources(
            ScrutinyDocumentExtraction ext,
            ApplicationDocument doc,
            ScrutinyAssessment asmt
    ) {
        Map<String, FieldSourceInfo> sources = new LinkedHashMap<>();

        // 1. Add file metadata entries
        if (doc != null) {
            if (doc.getOriginalFileName() != null) {
                sources.put("originalFileName", FieldSourceInfo.fileMeta(
                        "originalFileName", "Original File Name", doc.getOriginalFileName(),
                        "Multipart upload header filename", doc.getOriginalFileName()
                ));
            }
            if (doc.getFileSize() != null) {
                sources.put("fileSize", FieldSourceInfo.fileMeta(
                        "fileSize", "File Size", doc.getFileSize() + " bytes",
                        "Binary byte size in MinIO storage", doc.getFileSize() + " bytes"
                ));
            }
            if (doc.getFileHash() != null) {
                sources.put("fileHash", FieldSourceInfo.fileMeta(
                        "fileHash", "File SHA-256 Digest", doc.getFileHash(),
                        "Cryptographic SHA-256 digest of document bytes", doc.getFileHash()
                ));
            }
            if (doc.getStoragePath() != null) {
                sources.put("storagePath", FieldSourceInfo.fileMeta(
                        "storagePath", "MinIO Storage Object Key", doc.getStoragePath(),
                        "Object storage path in S3/MinIO bucket", doc.getStoragePath()
                ));
            }
            if (doc.getContentType() != null) {
                sources.put("contentType", FieldSourceInfo.fileMeta(
                        "contentType", "MIME Content Type", doc.getContentType(),
                        "HTTP Content-Type detected on document ingestion", doc.getContentType()
                ));
            }
            sources.put("documentVersion", FieldSourceInfo.appMeta(
                    "documentVersion", "Document Version", "v" + doc.getVersion(),
                    "Statutory submission document version sequence", "v" + doc.getVersion()
            ));
        }

        if (ext.getPageCount() != null) {
            sources.put("pageCount", FieldSourceInfo.fileMeta(
                    "pageCount", "Page Count", ext.getPageCount() + (ext.getPageCount() == 1 ? " page" : " pages"),
                    "PDF page tree inspection", ext.getPageCount() + (ext.getPageCount() == 1 ? " page" : " pages")
            ));
        }

        if (asmt != null && asmt.getAssessmentReference() != null) {
            sources.put("assessmentReference", FieldSourceInfo.appMeta(
                    "assessmentReference", "Assessment Reference", asmt.getAssessmentReference(),
                    "Scrutiny assessment cycle reference identifier", asmt.getAssessmentReference()
            ));
        }

        // 2. Parse extracted fields json and inspect _fieldSources or keys
        if (ext.getExtractedFieldsJson() != null && !ext.getExtractedFieldsJson().isBlank()) {
            try {
                Map<String, Object> rawMap = OBJECT_MAPPER.readValue(
                        ext.getExtractedFieldsJson(),
                        new TypeReference<Map<String, Object>>() {}
                );

                // If _fieldSources is present, deserialize those directly
                Object fsObj = rawMap.get("_fieldSources");
                if (fsObj instanceof Map<?, ?> rawFsMap) {
                    for (Map.Entry<?, ?> entry : rawFsMap.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (entry.getValue() instanceof Map<?, ?> rawInfo) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> infoMap = (Map<String, Object>) rawInfo;
                            sources.put(key, new FieldSourceInfo(
                                    key,
                                    String.valueOf(infoMap.getOrDefault("fieldLabel", key)),
                                    infoMap.get("value"),
                                    String.valueOf(infoMap.getOrDefault("source", FieldSourceInfo.SOURCE_DERIVED_VALUE)),
                                    String.valueOf(infoMap.getOrDefault("sourceLabel", "Derived Value")),
                                    String.valueOf(infoMap.getOrDefault("ruleOrPattern", "Scrutiny extraction rule")),
                                    String.valueOf(infoMap.getOrDefault("evidenceSnippet", ""))
                            ));
                        }
                    }
                }

                // For any other field key in rawMap not yet in sources, map its source accurately
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    String k = entry.getKey();
                    if ("_fieldSources".equals(k) || sources.containsKey(k)) {
                        continue;
                    }
                    Object val = entry.getValue();
                    if (val == null) continue;

                    switch (k) {
                        case "expiryDate" -> sources.put(k, FieldSourceInfo.content(
                                k, "Expiry Date", val,
                                "Contextual expiry regex pattern (valid up to / expires on / expiry date)",
                                "Valid upto " + val
                        ));
                        case "issueDate" -> sources.put(k, FieldSourceInfo.content(
                                k, "Issue Date", val,
                                "Contextual issue date regex pattern (dated / date of issue / issued on)",
                                "Dated " + val
                        ));
                        case "certificateNumber" -> sources.put(k, FieldSourceInfo.content(
                                k, "Certificate / Reference Number", val,
                                "Statutory certificate/NOC/reference number pattern",
                                String.valueOf(val)
                        ));
                        case "panNumber" -> sources.put(k, FieldSourceInfo.content(
                                k, "PAN Number", val,
                                "Income Tax PAN pattern [A-Z]{5}[0-9]{4}[A-Z]",
                                String.valueOf(val)
                        ));
                        case "pinCode" -> sources.put(k, FieldSourceInfo.content(
                                k, "PIN Code", val,
                                "Indian Postal PIN code pattern [1-9][0-9]{5}",
                                String.valueOf(val)
                        ));
                        case "landAreaMatched" -> sources.put(k, FieldSourceInfo.content(
                                k, "Land Area", val,
                                "Land area boundary pattern (acres / hectares / sq.m)",
                                String.valueOf(val)
                        ));
                        case "institutionName" -> sources.put(k, FieldSourceInfo.content(
                                k, "Institution Name", val,
                                "Institution header and entity naming pattern",
                                String.valueOf(val)
                        ));
                        case "trustName" -> sources.put(k, FieldSourceInfo.content(
                                k, "Trust / Society Name", val,
                                "Sponsoring Trust / Society entity pattern",
                                String.valueOf(val)
                        ));
                        case "issuingAuthority" -> sources.put(k, FieldSourceInfo.content(
                                k, "Issuing Authority", val,
                                "Statutory authority catalog matching",
                                String.valueOf(val)
                        ));
                        case "categoryPlausible" -> sources.put(k, FieldSourceInfo.derived(
                                k, "Category Plausibility", val,
                                "Statutory requirement domain signal verification & academic lab manual filter",
                                Boolean.TRUE.equals(val) ? "Document contains statutory domain terminology" : "Category signals not matched"
                        ));
                        case "contentWarning" -> sources.put(k, FieldSourceInfo.derived(
                                k, "Content Warning", val,
                                "Unrelated coursework / lab manual keyword scanner",
                                String.valueOf(val)
                        ));
                        default -> sources.put(k, FieldSourceInfo.derived(
                                k, k, val,
                                "Scrutiny automated rule parser",
                                String.valueOf(val)
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return sources;
    }
}
