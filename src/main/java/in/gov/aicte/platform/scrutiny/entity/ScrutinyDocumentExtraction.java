package in.gov.aicte.platform.scrutiny.entity;

import in.gov.aicte.platform.application.document.ApplicationDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "scrutiny_document_extractions")
public class ScrutinyDocumentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private ScrutinyAssessment assessment;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private ApplicationDocument document;

    @Column(name = "requirement_code", nullable = false, length = 64)
    private String requirementCode;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "document_version", nullable = false)
    private int documentVersion = 1;

    @Column(name = "extraction_method", nullable = false, length = 64)
    private String extractionMethod = "PDFBOX_SEARCHABLE_TEXT";

    @Column(name = "extracted_text_snippet", columnDefinition = "TEXT")
    private String extractedTextSnippet;

    @Column(name = "extracted_fields_json", columnDefinition = "TEXT")
    private String extractedFieldsJson;

    @Column(name = "confidence_score", nullable = true)
    private Double confidenceScore;

    @Column(name = "ocr_status", nullable = false, length = 32)
    private String ocrStatus = "SUCCESS";

    @Column(name = "text_character_count")
    private Integer textCharacterCount;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "validation_metric", length = 128)
    private String validationMetric;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ScrutinyDocumentExtraction() {
    }

    public ScrutinyDocumentExtraction(ScrutinyAssessment assessment, Long applicationId,
                                      ApplicationDocument document, String requirementCode,
                                      String documentName, int documentVersion,
                                      String extractionMethod, String extractedTextSnippet,
                                      String extractedFieldsJson, Double confidenceScore,
                                      String ocrStatus) {
        this.assessment = assessment;
        this.applicationId = applicationId;
        this.document = document;
        this.requirementCode = requirementCode;
        this.documentName = documentName;
        this.documentVersion = documentVersion;
        this.extractionMethod = extractionMethod;
        this.extractedTextSnippet = extractedTextSnippet;
        this.extractedFieldsJson = extractedFieldsJson;
        this.confidenceScore = confidenceScore;
        this.ocrStatus = ocrStatus;
        this.textCharacterCount = extractedTextSnippet != null ? extractedTextSnippet.length() : 0;
        this.pageCount = 1;
        this.validationMetric = confidenceScore != null
                ? String.format("OCR_MEASURED (%.1f%%)", confidenceScore * 100.0)
                : ("PDFBOX_SEARCHABLE_TEXT".equalsIgnoreCase(extractionMethod) ? "N/A (Embedded Text)" : null);
    }

    public ScrutinyDocumentExtraction(ScrutinyAssessment assessment, Long applicationId,
                                      ApplicationDocument document, String requirementCode,
                                      String documentName, int documentVersion,
                                      String extractionMethod, String extractedTextSnippet,
                                      String extractedFieldsJson, Double confidenceScore,
                                      String ocrStatus, Integer textCharacterCount,
                                      Integer pageCount, String validationMetric) {
        this(assessment, applicationId, document, requirementCode, documentName, documentVersion,
                extractionMethod, extractedTextSnippet, extractedFieldsJson, confidenceScore, ocrStatus);
        this.textCharacterCount = textCharacterCount != null ? textCharacterCount : (extractedTextSnippet != null ? extractedTextSnippet.length() : 0);
        this.pageCount = pageCount != null ? pageCount : 1;
        this.validationMetric = validationMetric;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScrutinyAssessment getAssessment() {
        return assessment;
    }

    public void setAssessment(ScrutinyAssessment assessment) {
        this.assessment = assessment;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public ApplicationDocument getDocument() {
        return document;
    }

    public void setDocument(ApplicationDocument document) {
        this.document = document;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public void setRequirementCode(String requirementCode) {
        this.requirementCode = requirementCode;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public int getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(int documentVersion) {
        this.documentVersion = documentVersion;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public String getExtractedTextSnippet() {
        return extractedTextSnippet;
    }

    public String getRawText() {
        return extractedTextSnippet;
    }

    public void setExtractedTextSnippet(String extractedTextSnippet) {
        this.extractedTextSnippet = extractedTextSnippet;
    }

    public void setRawText(String rawText) {
        this.extractedTextSnippet = rawText;
    }

    public String getExtractedFieldsJson() {
        return extractedFieldsJson;
    }

    public void setExtractedFieldsJson(String extractedFieldsJson) {
        this.extractedFieldsJson = extractedFieldsJson;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
