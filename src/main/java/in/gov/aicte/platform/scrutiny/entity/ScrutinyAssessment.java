package in.gov.aicte.platform.scrutiny.entity;

import in.gov.aicte.platform.application.Application;
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

@Entity
@Table(name = "scrutiny_assessments")
public class ScrutinyAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_reference", nullable = false, unique = true, length = 64)
    private String assessmentReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "assessment_type", nullable = false, length = 32)
    private String assessmentType = "PRELIMINARY_SCRUTINY";

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber = 1;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "summary_outcome", nullable = false, length = 32)
    private String summaryOutcome = "PENDING";

    @Column(name = "rules_version", nullable = false, length = 32)
    private String rulesVersion = "APH_2024_27_V1";

    @Column(name = "assessed_by", nullable = false, length = 128)
    private String assessedBy = "SYSTEM_AUTOMATION";

    @Column(name = "total_checks", nullable = false)
    private int totalChecks = 0;

    @Column(name = "passed_checks", nullable = false)
    private int passedChecks = 0;

    @Column(name = "deficiency_checks", nullable = false)
    private int deficiencyChecks = 0;

    @Column(name = "review_checks", nullable = false)
    private int reviewChecks = 0;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScrutinyAssessment() {
    }

    public ScrutinyAssessment(String assessmentReference, Application application, Long institutionId,
                              String assessmentType, int cycleNumber, String rulesVersion, String assessedBy) {
        this.assessmentReference = assessmentReference;
        this.application = application;
        this.institutionId = institutionId;
        this.assessmentType = assessmentType;
        this.cycleNumber = cycleNumber;
        this.rulesVersion = rulesVersion;
        this.assessedBy = assessedBy;
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
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

    public String getAssessmentReference() {
        return assessmentReference;
    }

    public void setAssessmentReference(String assessmentReference) {
        this.assessmentReference = assessmentReference;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public String getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public void setCycleNumber(int cycleNumber) {
        this.cycleNumber = cycleNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummaryOutcome() {
        return summaryOutcome;
    }

    public void setSummaryOutcome(String summaryOutcome) {
        this.summaryOutcome = summaryOutcome;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    public String getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    public int getTotalChecks() {
        return totalChecks;
    }

    public void setTotalChecks(int totalChecks) {
        this.totalChecks = totalChecks;
    }

    public int getPassedChecks() {
        return passedChecks;
    }

    public void setPassedChecks(int passedChecks) {
        this.passedChecks = passedChecks;
    }

    public int getDeficiencyChecks() {
        return deficiencyChecks;
    }

    public void setDeficiencyChecks(int deficiencyChecks) {
        this.deficiencyChecks = deficiencyChecks;
    }

    public int getReviewChecks() {
        return reviewChecks;
    }

    public void setReviewChecks(int reviewChecks) {
        this.reviewChecks = reviewChecks;
    }

    public String getSummaryNotes() {
        return summaryNotes;
    }

    public void setSummaryNotes(String summaryNotes) {
        this.summaryNotes = summaryNotes;
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
