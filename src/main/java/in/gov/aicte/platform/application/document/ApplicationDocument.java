package in.gov.aicte.platform.application.document;

import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.draft.ApplicationDraft;
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
import java.util.Objects;

@Entity
@Table(name = "application_documents")
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id")
    private ApplicationDraft draft;

    @Column(name = "requirement_code", nullable = false, length = 64)
    private String requirementCode;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "UPLOADED";

    @Column(name = "uploaded_by", nullable = false, length = 128)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ApplicationDocument() {
    }

    public ApplicationDocument(Application application, String requirementCode, String documentName,
                               String fileName, String originalFileName, String storagePath,
                               Long fileSize, String contentType, String fileHash,
                               int version, String status, String uploadedBy) {
        this.application = application;
        this.requirementCode = requirementCode;
        this.documentName = documentName;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.fileHash = fileHash;
        this.version = version;
        this.status = status;
        this.uploadedBy = uploadedBy;
    }

    public ApplicationDocument(ApplicationDraft draft, String requirementCode, String documentName,
                               String fileName, String originalFileName, String storagePath,
                               Long fileSize, String contentType, String fileHash,
                               int version, String status, String uploadedBy) {
        this.draft = draft;
        this.requirementCode = requirementCode;
        this.documentName = documentName;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.fileHash = fileHash;
        this.version = version;
        this.status = status;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.uploadedAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "UPLOADED";
        }
        if (this.version <= 0) {
            this.version = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public ApplicationDraft getDraft() {
        return draft;
    }

    public void setDraft(ApplicationDraft draft) {
        this.draft = draft;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationDocument that = (ApplicationDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
