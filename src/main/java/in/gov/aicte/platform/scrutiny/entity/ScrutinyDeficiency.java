package in.gov.aicte.platform.scrutiny.entity;

import in.gov.aicte.platform.application.Application;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "scrutiny_deficiencies")
public class ScrutinyDeficiency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deficiency_reference", nullable = false, unique = true, length = 64)
    private String deficiencyReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private ScrutinyAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private ScrutinyFinding finding;

    @Column(name = "requirement_code", length = 64)
    private String requirementCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "rule_reference", nullable = false, length = 128)
    private String ruleReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "associated_document_id")
    private ApplicationDocument associatedDocument;

    @Column(name = "associated_document_version", nullable = false)
    private int associatedDocumentVersion = 1;

    @Column(name = "original_evidence_text", columnDefinition = "TEXT")
    private String originalEvidenceText;

    @Column(name = "officer_observation", columnDefinition = "TEXT")
    private String officerObservation;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ISSUED";

    @Column(name = "blocking", nullable = false)
    private boolean blocking = true;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "institution_explanation", columnDefinition = "TEXT")
    private String institutionExplanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_document_id")
    private ApplicationDocument correctedDocument;

    @Column(name = "corrected_document_version")
    private Integer correctedDocumentVersion;

    @Column(name = "reviewer_decision", length = 32)
    private String reviewerDecision;

    @Column(name = "reviewer_remarks", columnDefinition = "TEXT")
    private String reviewerRemarks;

    @Column(name = "issued_by", length = 128)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScrutinyDeficiency() {
    }

    public ScrutinyDeficiency(String deficiencyReference, Application application,
                              ScrutinyAssessment assessment, ScrutinyFinding finding,
                              String requirementCode, String title, String description,
                              String ruleReference, ApplicationDocument associatedDocument,
                              int associatedDocumentVersion, String originalEvidenceText,
                              String officerObservation, boolean blocking, LocalDate dueDate,
                              String issuedBy) {
        this.deficiencyReference = deficiencyReference;
        this.application = application;
        this.assessment = assessment;
        this.finding = finding;
        this.requirementCode = requirementCode;
        this.title = title;
        this.description = description;
        this.ruleReference = ruleReference;
        this.associatedDocument = associatedDocument;
        this.associatedDocumentVersion = associatedDocumentVersion;
        this.originalEvidenceText = originalEvidenceText;
        this.officerObservation = officerObservation;
        this.blocking = blocking;
        this.dueDate = dueDate;
        this.issuedBy = issuedBy;
        this.status = "ISSUED";
        this.issuedAt = Instant.now();
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (issuedAt == null) {
            issuedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeficiencyReference() {
        return deficiencyReference;
    }

    public void setDeficiencyReference(String deficiencyReference) {
        this.deficiencyReference = deficiencyReference;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public ScrutinyAssessment getAssessment() {
        return assessment;
    }

    public void setAssessment(ScrutinyAssessment assessment) {
        this.assessment = assessment;
    }

    public ScrutinyFinding getFinding() {
        return finding;
    }

    public void setFinding(ScrutinyFinding finding) {
        this.finding = finding;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public void setRequirementCode(String requirementCode) {
        this.requirementCode = requirementCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRuleReference() {
        return ruleReference;
    }

    public void setRuleReference(String ruleReference) {
        this.ruleReference = ruleReference;
    }

    public ApplicationDocument getAssociatedDocument() {
        return associatedDocument;
    }

    public void setAssociatedDocument(ApplicationDocument associatedDocument) {
        this.associatedDocument = associatedDocument;
    }

    public int getAssociatedDocumentVersion() {
        return associatedDocumentVersion;
    }

    public void setAssociatedDocumentVersion(int associatedDocumentVersion) {
        this.associatedDocumentVersion = associatedDocumentVersion;
    }

    public String getOriginalEvidenceText() {
        return originalEvidenceText;
    }

    public void setOriginalEvidenceText(String originalEvidenceText) {
        this.originalEvidenceText = originalEvidenceText;
    }

    public String getOfficerObservation() {
        return officerObservation;
    }

    public void setOfficerObservation(String officerObservation) {
        this.officerObservation = officerObservation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getInstitutionExplanation() {
        return institutionExplanation;
    }

    public void setInstitutionExplanation(String institutionExplanation) {
        this.institutionExplanation = institutionExplanation;
    }

    public ApplicationDocument getCorrectedDocument() {
        return correctedDocument;
    }

    public void setCorrectedDocument(ApplicationDocument correctedDocument) {
        this.correctedDocument = correctedDocument;
    }

    public Integer getCorrectedDocumentVersion() {
        return correctedDocumentVersion;
    }

    public void setCorrectedDocumentVersion(Integer correctedDocumentVersion) {
        this.correctedDocumentVersion = correctedDocumentVersion;
    }

    public String getReviewerDecision() {
        return reviewerDecision;
    }

    public void setReviewerDecision(String reviewerDecision) {
        this.reviewerDecision = reviewerDecision;
    }

    public String getReviewerRemarks() {
        return reviewerRemarks;
    }

    public void setReviewerRemarks(String reviewerRemarks) {
        this.reviewerRemarks = reviewerRemarks;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
