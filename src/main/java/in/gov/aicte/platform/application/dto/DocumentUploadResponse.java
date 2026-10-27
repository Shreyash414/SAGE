package in.gov.aicte.platform.application.dto;

import in.gov.aicte.platform.application.document.ApplicationDocument;

import java.time.Instant;

public record DocumentUploadResponse(
        Long id,
        Long applicationId,
        Long draftId,
        String requirementCode,
        String documentName,
        String fileName,
        String originalFileName,
        Long fileSize,
        String contentType,
        String fileHash,
        int version,
        String status,
        String uploadedBy,
        Instant uploadedAt
) {
    public Long getDocumentId() {
        return id;
    }

    public static DocumentUploadResponse from(ApplicationDocument doc) {
        return new DocumentUploadResponse(
                doc.getId(),
                doc.getApplication() != null ? doc.getApplication().getId() : null,
                doc.getDraft() != null ? doc.getDraft().getId() : null,
                doc.getRequirementCode(),
                doc.getDocumentName(),
                doc.getFileName(),
                doc.getOriginalFileName(),
                doc.getFileSize(),
                doc.getContentType(),
                doc.getFileHash(),
                doc.getVersion(),
                doc.getStatus(),
                doc.getUploadedBy(),
                doc.getUploadedAt()
        );
    }
}
