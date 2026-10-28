package in.gov.aicte.platform.scrutiny.ocr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing extracted document text, structured fields, and optical recognition metrics.
 *
 * Distinguishes native PDF embedded text (where optical confidence does not exist and is null / N/A)
 * from raster/scanned document OCR (where optical confidence is measured from Tess4J word tokens).
 */
public class ExtractedDocumentData {

    private String rawText = "";
    private String extractionMethod = "PDFBOX_SEARCHABLE_TEXT";
    private Double confidenceScore = null; // null for embedded digital text (no OCR performed) or unmeasured
    private String ocrStatus = "SUCCESS"; // SUCCESS, UNCERTAIN, EMPTY, UNAVAILABLE, FAILED
    private String errorMessage;
    private Integer textCharacterCount = 0;
    private Integer pageCount = 1;
    private String validationMetric;
    private Map<String, Object> fields = new LinkedHashMap<>();
    private List<LocalDate> datesFound = new ArrayList<>();

    public ExtractedDocumentData() {
    }

    public ExtractedDocumentData(String rawText, String extractionMethod, Double confidenceScore, String ocrStatus) {
        this.rawText = rawText != null ? rawText : "";
        this.extractionMethod = extractionMethod;
        this.confidenceScore = confidenceScore;
        this.ocrStatus = ocrStatus;
        this.textCharacterCount = this.rawText.length();
        this.pageCount = 1;
        this.validationMetric = confidenceScore != null
                ? String.format("OCR_MEASURED (%.1f%%)", confidenceScore * 100.0)
                : ("PDFBOX_SEARCHABLE_TEXT".equalsIgnoreCase(extractionMethod) ? "N/A (Embedded Text)" : null);
    }

    public ExtractedDocumentData(String rawText, String extractionMethod, Double confidenceScore,
                                 String ocrStatus, Integer textCharacterCount, Integer pageCount,
                                 String validationMetric) {
        this.rawText = rawText != null ? rawText : "";
        this.extractionMethod = extractionMethod;
        this.confidenceScore = confidenceScore;
        this.ocrStatus = ocrStatus;
        this.textCharacterCount = textCharacterCount != null ? textCharacterCount : this.rawText.length();
        this.pageCount = pageCount != null ? pageCount : 1;
        this.validationMetric = validationMetric;
    }

    public static ExtractedDocumentData embeddedText(String text, int pages) {
        String clean = text != null ? text.trim() : "";
        ExtractedDocumentData data = new ExtractedDocumentData(
                clean, "PDFBOX_SEARCHABLE_TEXT", null, "SUCCESS",
                clean.length(), pages, "N/A (Embedded Text)"
        );
        return data;
    }

    public static ExtractedDocumentData ocr(String text, Double confidence, int pages, int charCount, String method) {
        String clean = text != null ? text.trim() : "";
        String status = (confidence != null && confidence < 0.60) ? "UNCERTAIN" : "SUCCESS";
        String metric = confidence != null
                ? String.format("OCR_MEASURED (%.1f%%)", confidence * 100.0)
                : "OCR_MEASURED (NO_WORDS)";
        return new ExtractedDocumentData(
                clean, method, confidence, status, charCount, pages, metric
        );
    }

    public static ExtractedDocumentData empty(String reason) {
        ExtractedDocumentData data = new ExtractedDocumentData(
                "", "EMPTY_DOCUMENT", null, "EMPTY", 0, 1, "NO_TEXT_DETECTED"
        );
        data.setErrorMessage(reason);
        return data;
    }

    public static ExtractedDocumentData unavailable(String reason) {
        ExtractedDocumentData data = new ExtractedDocumentData(
                "", "OCR_RETIRED_UNAVAILABLE", null, "UNAVAILABLE", 0, 1, "N/A (OCR Engine Retired)"
        );
        data.setErrorMessage(reason);
        return data;
    }

    public static ExtractedDocumentData failed(String error) {
        ExtractedDocumentData data = new ExtractedDocumentData(
                "", "EXTRACTION_FAILED", null, "FAILED", 0, 1, "N/A (Extraction Failed)"
        );
        data.setErrorMessage(error);
        return data;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText != null ? rawText : "";
        if (this.textCharacterCount == null || this.textCharacterCount == 0) {
            this.textCharacterCount = this.rawText.length();
        }
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getTextCharacterCount() {
        return textCharacterCount;
    }

    public void setTextCharacterCount(Integer textCharacterCount) {
        this.textCharacterCount = textCharacterCount;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getValidationMetric() {
        return validationMetric;
    }

    public void setValidationMetric(String validationMetric) {
        this.validationMetric = validationMetric;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields != null ? fields : new LinkedHashMap<>();
    }

    public void addField(String key, Object value) {
        if (this.fields == null) {
            this.fields = new LinkedHashMap<>();
        }
        if (value != null) {
            this.fields.put(key, value);
        }
    }

    public Object getField(String key) {
        return fields != null ? fields.get(key) : null;
    }

    public String getStringField(String key) {
        Object val = getField(key);
        return val != null ? val.toString() : null;
    }

    public LocalDate getDateField(String key) {
        Object val = getField(key);
        if (val instanceof LocalDate) {
            return (LocalDate) val;
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public List<LocalDate> getDatesFound() {
        return datesFound;
    }

    public void setDatesFound(List<LocalDate> datesFound) {
        this.datesFound = datesFound != null ? datesFound : new ArrayList<>();
    }
}
