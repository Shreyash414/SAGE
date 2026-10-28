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
@Table(name = "scrutiny_rescrutiny_comparisons")
public class ScrutinyRescrutinyComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private ScrutinyAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deficiency_id", nullable = false)
    private ScrutinyDeficiency deficiency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_document_id")
    private ApplicationDocument originalDocument;

    @Column(name = "original_document_version", nullable = false)
    private int originalDocumentVersion = 1;

    @Column(name = "original_extracted_value", columnDefinition = "TEXT")
    private String originalExtractedValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_document_id")
    private ApplicationDocument correctedDocument;

    @Column(name = "corrected_document_version", nullable = false)
    private int correctedDocumentVersion = 2;

    @Column(name = "corrected_extracted_value", columnDefinition = "TEXT")
    private String correctedExtractedValue;

    @Column(name = "comparison_status", nullable = false, length = 64)
    private String comparisonStatus = "RECTIFIED_PENDING_CONFIRMATION";

    @Column(name = "comparison_summary", nullable = false, columnDefinition = "TEXT")
    private String comparisonSummary;

    @Column(name = "remaining_issues", columnDefinition = "TEXT")
    private String remainingIssues;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ScrutinyRescrutinyComparison() {
    }

    public ScrutinyRescrutinyComparison(ScrutinyAssessment assessment, ScrutinyDeficiency deficiency,
                                        ApplicationDocument originalDocument, int originalDocumentVersion,
                                        String originalExtractedValue, ApplicationDocument correctedDocument,
                                        int correctedDocumentVersion, String correctedExtractedValue,
                                        String comparisonStatus, String comparisonSummary, String remainingIssues) {
        this.assessment = assessment;
        this.deficiency = deficiency;
        this.originalDocument = originalDocument;
        this.originalDocumentVersion = originalDocumentVersion;
        this.originalExtractedValue = originalExtractedValue;
        this.correctedDocument = correctedDocument;
        this.correctedDocumentVersion = correctedDocumentVersion;
        this.correctedExtractedValue = correctedExtractedValue;
        this.comparisonStatus = comparisonStatus;
        this.comparisonSummary = comparisonSummary;
        this.remainingIssues = remainingIssues;
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

    public ScrutinyDeficiency getDeficiency() {
        return deficiency;
    }

    public void setDeficiency(ScrutinyDeficiency deficiency) {
        this.deficiency = deficiency;
    }

    public ApplicationDocument getOriginalDocument() {
        return originalDocument;
    }

    public void setOriginalDocument(ApplicationDocument originalDocument) {
        this.originalDocument = originalDocument;
    }

    public int getOriginalDocumentVersion() {
        return originalDocumentVersion;
    }

    public void setOriginalDocumentVersion(int originalDocumentVersion) {
        this.originalDocumentVersion = originalDocumentVersion;
    }

    public String getOriginalExtractedValue() {
        return originalExtractedValue;
    }

    public void setOriginalExtractedValue(String originalExtractedValue) {
        this.originalExtractedValue = originalExtractedValue;
    }

    public ApplicationDocument getCorrectedDocument() {
        return correctedDocument;
    }

    public void setCorrectedDocument(ApplicationDocument correctedDocument) {
        this.correctedDocument = correctedDocument;
    }

    public int getCorrectedDocumentVersion() {
        return correctedDocumentVersion;
    }

    public void setCorrectedDocumentVersion(int correctedDocumentVersion) {
        this.correctedDocumentVersion = correctedDocumentVersion;
    }

    public String getCorrectedExtractedValue() {
        return correctedExtractedValue;
    }

    public void setCorrectedExtractedValue(String correctedExtractedValue) {
        this.correctedExtractedValue = correctedExtractedValue;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(String comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public String getComparisonSummary() {
        return comparisonSummary;
    }

    public void setComparisonSummary(String comparisonSummary) {
        this.comparisonSummary = comparisonSummary;
    }

    public String getRemainingIssues() {
        return remainingIssues;
    }

    public void setRemainingIssues(String remainingIssues) {
        this.remainingIssues = remainingIssues;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
