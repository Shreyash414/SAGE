package in.gov.aicte.platform.application.draft;

import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.institution.Institution;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "application_drafts")
public class ApplicationDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_reference", nullable = false, unique = true, length = 64)
    private String draftReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "applicant_id", nullable = false, length = 64)
    private String applicantId;

    @Column(name = "application_type", nullable = false, length = 64)
    private String applicationType;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "form_data_json", columnDefinition = "TEXT")
    private String formDataJson;

    @Column(name = "requirements_version", nullable = false, length = 32)
    private String requirementsVersion = "APH_2024_27_V1";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_application_id")
    private Application promotedApplication;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ApplicationDraft() {
    }

    public ApplicationDraft(
            Institution institution,
            String draftReference,
            String applicantId,
            String applicationType,
            String academicYear,
            String requirementsVersion
    ) {
        this.institution = institution;
        this.draftReference = draftReference;
        this.applicantId = applicantId;
        this.applicationType = applicationType;
        this.academicYear = academicYear;
        this.requirementsVersion = (requirementsVersion != null && !requirementsVersion.isBlank())
                ? requirementsVersion
                : "APH_2024_27_V1";
        this.status = "DRAFT";
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "DRAFT";
        }
        if (this.requirementsVersion == null) {
            this.requirementsVersion = "APH_2024_27_V1";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDraftReference() {
        return draftReference;
    }

    public void setDraftReference(String draftReference) {
        this.draftReference = draftReference;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(String applicantId) {
        this.applicantId = applicantId;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getFormDataJson() {
        return formDataJson;
    }

    public void setFormDataJson(String formDataJson) {
        this.formDataJson = formDataJson;
    }

    public String getRequirementsVersion() {
        return requirementsVersion;
    }

    public void setRequirementsVersion(String requirementsVersion) {
        this.requirementsVersion = requirementsVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Application getPromotedApplication() {
        return promotedApplication;
    }

    public void setPromotedApplication(Application promotedApplication) {
        this.promotedApplication = promotedApplication;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationDraft that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
