package in.gov.aicte.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;
import in.gov.aicte.platform.application.catalogue.DocumentRequirementDefinition;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.application.dto.ApplicationChecklistResponse;
import in.gov.aicte.platform.application.dto.ApplicationResponse;
import in.gov.aicte.platform.application.dto.ApplicationSubmissionResult;
import in.gov.aicte.platform.application.dto.DocumentUploadResponse;
import in.gov.aicte.platform.application.dto.SaveDraftRequest;
import in.gov.aicte.platform.application.validation.ApplicationSubmissionValidator;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import in.gov.aicte.platform.workflow.WorkflowService;
import in.gov.aicte.platform.workflow.WorkflowTransition;
import in.gov.aicte.platform.workflow.WorkflowTransitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ApplicationSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmissionService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;
    private final ApplicationSubmissionValidator validator;
    private final ApplicationTypeCatalogue catalogue;
    private final WorkflowTransitionRepository transitionRepository;
    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowService workflowService;
    private final in.gov.aicte.platform.evc.EvcService evcService;

    public ApplicationSubmissionService(
            ApplicationRepository applicationRepository,
            ApplicationDocumentRepository documentRepository,
            MinioStorageService minioStorageService,
            ApplicationSubmissionValidator validator,
            ApplicationTypeCatalogue catalogue,
            WorkflowTransitionRepository transitionRepository,
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            WorkflowService workflowService
    ) {
        this(applicationRepository, documentRepository, minioStorageService, validator, catalogue,
                transitionRepository, auditEventRepository, userRepository, objectMapper, workflowService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApplicationSubmissionService(
            ApplicationRepository applicationRepository,
            ApplicationDocumentRepository documentRepository,
            MinioStorageService minioStorageService,
            ApplicationSubmissionValidator validator,
            ApplicationTypeCatalogue catalogue,
            WorkflowTransitionRepository transitionRepository,
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            WorkflowService workflowService,
            @org.springframework.context.annotation.Lazy in.gov.aicte.platform.evc.EvcService evcService
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.minioStorageService = minioStorageService;
        this.validator = validator;
        this.catalogue = catalogue;
        this.transitionRepository = transitionRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
        this.evcService = evcService;
    }

    @Transactional
    public ApplicationResponse saveDraft(Long applicationId, SaveDraftRequest request, String actorId) {
        Application application = getAndAuthorizeApplication(applicationId);

        if (application.getSubmittedAt() != null || !"DRAFT".equalsIgnoreCase(application.getStatus())) {
            throw new IllegalStateException("Application is already submitted and cannot be modified as a draft.");
        }

        try {
            if (request.formData() != null) {
                String json = objectMapper.writeValueAsString(request.formData());
                application.setFormDataJson(json);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid form data JSON format", e);
        }

        if (request.observations() != null) {
            application.setObservations(request.observations().trim());
        }

        Application saved = applicationRepository.save(application);

        recordAudit(
                "APPLICATION_DRAFT_SAVED",
                actorId,
                saved.getId().toString(),
                Map.of("applicationReference", saved.getApplicationReference())
        );

        return workflowService.getApplicationDetails(saved.getId().toString());
    }

    @Transactional(readOnly = true)
    public ApplicationChecklistResponse getChecklist(Long applicationId) {
        Application application = getAndAuthorizeApplication(applicationId);
        return validator.generateChecklist(application);
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(
            Long applicationId,
            String requirementCode,
            MultipartFile file,
            String actorId
    ) {
        Application application = getAndAuthorizeApplication(applicationId);

        if (application.getSubmittedAt() != null || !"DRAFT".equalsIgnoreCase(application.getStatus())) {
            throw new IllegalStateException("Cannot upload documents: Application is already submitted.");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be empty.");
        }

        if (file.getSize() > 25 * 1024 * 1024L) {
            throw new IllegalArgumentException("File size exceeds maximum permitted limit of 25MB.");
        }

        // Verify requirement code against catalogue
        Optional<ApplicationTypeDefinition> typeDefOpt = catalogue.findByCode(application.getApplicationType());
        if (typeDefOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid application type for requirement verification: " + application.getApplicationType());
        }

        DocumentRequirementDefinition reqDef = typeDefOpt.get().documentRequirements().stream()
                .filter(r -> r.code().equalsIgnoreCase(requirementCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Requirement code '" + requirementCode + "' is not applicable for application type " + application.getApplicationType()));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file stream", e);
        }

        // Check if a document already exists for this requirement code
        Optional<ApplicationDocument> existingOpt = documentRepository.findByApplicationIdAndRequirementCode(applicationId, requirementCode);
        int nextVersion = existingOpt.map(d -> d.getVersion() + 1).orElse(1);
        String oldStoragePath = existingOpt.map(ApplicationDocument::getStoragePath).orElse(null);

        // Upload to MinIO
        MinioStorageService.StoredObjectResult uploadResult;
        try {
            uploadResult = minioStorageService.uploadDocument(
                    applicationId,
                    requirementCode,
                    nextVersion,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    bytes
            );
        } catch (Exception e) {
            log.error("Failed to store file in MinIO: {}", e.getMessage());
            throw new RuntimeException("Secure object storage upload failed.", e);
        }

        // Persist metadata in PostgreSQL with compensating cleanup on failure
        ApplicationDocument doc;
        try {
            if (existingOpt.isPresent()) {
                doc = existingOpt.get();
                doc.setDocumentName(reqDef.name());
                doc.setFileName(file.getOriginalFilename());
                doc.setOriginalFileName(file.getOriginalFilename());
                doc.setStoragePath(uploadResult.storageKey());
                doc.setFileSize(uploadResult.byteSize());
                doc.setContentType(uploadResult.contentType());
                doc.setFileHash(uploadResult.sha256Checksum());
                doc.setVersion(nextVersion);
                doc.setStatus("UPLOADED");
                doc.setUploadedBy(actorId != null ? actorId : "SYSTEM");
            } else {
                doc = new ApplicationDocument(
                        application,
                        requirementCode,
                        reqDef.name(),
                        file.getOriginalFilename(),
                        file.getOriginalFilename(),
                        uploadResult.storageKey(),
                        uploadResult.byteSize(),
                        uploadResult.contentType(),
                        uploadResult.sha256Checksum(),
                        nextVersion,
                        "UPLOADED",
                        actorId != null ? actorId : "SYSTEM"
                );
            }
            doc = documentRepository.save(doc);

            // Clean up superseded file from MinIO
            if (oldStoragePath != null && !oldStoragePath.equals(uploadResult.storageKey())) {
                minioStorageService.deleteDocument(oldStoragePath);
            }
        } catch (Exception ex) {
            log.error("Database save failed for document metadata, performing rollback delete in MinIO: {}", ex.getMessage());
            minioStorageService.deleteDocument(uploadResult.storageKey());
            throw ex;
        }

        recordAudit(
                "DOCUMENT_UPLOADED",
                actorId,
                application.getId().toString(),
                Map.of(
                        "requirementCode", requirementCode,
                        "documentId", doc.getId(),
                        "fileName", doc.getFileName(),
                        "fileHash", doc.getFileHash(),
                        "version", doc.getVersion()
                )
        );

        return DocumentUploadResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public DocumentDownloadPayload downloadDocument(Long applicationId, Long documentId) {
        Application application = getAndAuthorizeApplication(applicationId);

        ApplicationDocument doc = documentRepository.findByIdAndApplicationId(documentId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        InputStream stream = minioStorageService.downloadDocument(doc.getStoragePath());
        return new DocumentDownloadPayload(
                doc.getOriginalFileName(),
                doc.getContentType(),
                doc.getFileSize(),
                new InputStreamResource(stream)
        );
    }

    public record DocumentDownloadPayload(
            String originalFileName,
            String contentType,
            Long fileSize,
            Resource resource
    ) {}

    @Transactional
    public void deleteDocument(Long applicationId, Long documentId, String actorId) {
        Application application = getAndAuthorizeApplication(applicationId);

        if (application.getSubmittedAt() != null || !"DRAFT".equalsIgnoreCase(application.getStatus())) {
            throw new IllegalStateException("Cannot delete document: Application is already submitted.");
        }

        ApplicationDocument doc = documentRepository.findByIdAndApplicationId(documentId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        String storagePath = doc.getStoragePath();
        documentRepository.delete(doc);
        minioStorageService.deleteDocument(storagePath);

        recordAudit(
                "DOCUMENT_DELETED",
                actorId,
                application.getId().toString(),
                Map.of("requirementCode", doc.getRequirementCode(), "documentId", documentId)
        );
    }

    @Transactional
    public ApplicationSubmissionResult submitApplication(Long applicationId, String actorId) {
        Application application = getAndAuthorizeApplication(applicationId);

        if (application.getSubmittedAt() != null || !"DRAFT".equalsIgnoreCase(application.getStatus())) {
            throw new IllegalStateException("Application has already been submitted.");
        }

        // Authoritative completeness validation
        ApplicationSubmissionValidator.ValidationReport report = validator.validate(application);
        if (!report.valid()) {
            return new ApplicationSubmissionResult(
                    false,
                    application.getId(),
                    application.getApplicationReference(),
                    application.getStatus(),
                    application.getCurrentStage(),
                    application.getOwnerName(),
                    null,
                    null,
                    report.blockingErrors()
            );
        }

        // Prepare immutable snapshot
        List<ApplicationDocument> docs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(application.getId());
        List<Map<String, Object>> docsSnapshot = new ArrayList<>();
        for (ApplicationDocument d : docs) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.getId());
            dm.put("requirementCode", d.getRequirementCode());
            dm.put("documentName", d.getDocumentName());
            dm.put("originalFileName", d.getOriginalFileName());
            dm.put("fileSize", d.getFileSize());
            dm.put("contentType", d.getContentType());
            dm.put("fileHash", d.getFileHash());
            dm.put("version", d.getVersion());
            dm.put("uploadedAt", d.getUploadedAt());
            docsSnapshot.add(dm);
        }

        Map<String, Object> snapshotMap = new LinkedHashMap<>();
        snapshotMap.put("applicationId", application.getId());
        snapshotMap.put("applicationReference", application.getApplicationReference());
        snapshotMap.put("applicationType", application.getApplicationType());
        snapshotMap.put("academicYear", application.getAcademicYear());
        snapshotMap.put("institutionId", application.getInstitution().getId());
        snapshotMap.put("institutionName", application.getInstitution().getName());
        snapshotMap.put("formData", parseJson(application.getFormDataJson()));
        snapshotMap.put("documents", docsSnapshot);
        snapshotMap.put("submittedAt", Instant.now().toString());
        snapshotMap.put("submittedBy", actorId);

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshotMap);
        } catch (JsonProcessingException e) {
            snapshotJson = "{}";
        }

        // Update application state
        Instant now = Instant.now();
        application.setStatus("In progress");
        application.setCurrentStage("Scrutiny");
        application.setOwnerName("B. Singh (Scrutiny Officer)");
        application.setOwnerId("USR-SO-01");
        application.setDueDate(LocalDate.now().plusDays(15));
        application.setSubmittedAt(now);
        application.setSubmittedBy(actorId);
        application.setSubmittedSnapshotJson(snapshotJson);

        Application saved = applicationRepository.save(application);

        // Record workflow transition
        WorkflowTransition transition = new WorkflowTransition(
                saved,
                "SUBMIT_APPLICATION",
                "Draft",
                "Scrutiny",
                "DRAFT",
                "In progress",
                actorId != null ? actorId : "SYSTEM",
                actorId != null ? actorId : "Applicant",
                "ROLE_INSTITUTION_REPRESENTATIVE",
                "Application formally submitted with all required APH statutory documents and certifications.",
                null,
                null
        );
        transitionRepository.save(transition);

        // Record audit event
        recordAudit(
                "APPLICATION_SUBMITTED",
                actorId,
                saved.getId().toString(),
                Map.of(
                        "applicationReference", saved.getApplicationReference(),
                        "submittedAt", now.toString(),
                        "documentCount", docs.size()
                )
        );

        log.info("Application id={} reference='{}' successfully submitted by '{}' and queued for Scrutiny",
                saved.getId(), saved.getApplicationReference(), actorId);

        return new ApplicationSubmissionResult(
                true,
                saved.getId(),
                saved.getApplicationReference(),
                saved.getStatus(),
                saved.getCurrentStage(),
                saved.getOwnerName(),
                now,
                actorId,
                List.of()
        );
    }

    private Application getAndAuthorizeApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        // Enforce tenant authorization if institution user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof DevUserDto user) {
            boolean isInstitutionalUser = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().contains("INSTITUTION"));

            if (isInstitutionalUser && user.institutionId() != null && !user.institutionId().isBlank()) {
                Institution inst = application.getInstitution();
                boolean matchesIdentifier = user.institutionId().equalsIgnoreCase(inst.getInstitutionalIdentifier());
                boolean matchesId = user.institutionId().equals(inst.getId().toString());

                if (!matchesIdentifier && !matchesId) {
                    log.warn("Security violation: User {} of institution {} attempted to access application {} belonging to institution {}",
                            user.id(), user.institutionId(), applicationId, inst.getInstitutionalIdentifier());
                    throw new AccessDeniedException("Access denied: You are not authorized to view or modify applications of another institution.");
                }
            }
        }

        if (auth != null) {
            boolean isEvcMember = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_EVC_MEMBER".equalsIgnoreCase(a.getAuthority()));
            if (isEvcMember && evcService != null) {
                List<in.gov.aicte.platform.evc.EvcVisit> assigned = evcService.getVisitsAssignedToMember(auth.getName());
                boolean isAssignedToApp = assigned.stream()
                        .anyMatch(v -> v.getApplication() != null && Objects.equals(v.getApplication().getId(), applicationId));
                if (!isAssignedToApp) {
                    throw new AccessDeniedException("Access denied: You are not assigned to an EVC inspection for this application.");
                }
            }
        }

        return application;
    }

    private void recordAudit(String action, String actorId, String entityId, Map<String, Object> meta) {
        String effectiveActor = (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM";
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        AuditEvent event = new AuditEvent(action, effectiveActor, "APPLICATION", entityId, metadataJson);
        auditEventRepository.save(event);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
