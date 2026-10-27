package in.gov.aicte.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.dto.ApplicationResponse;
import in.gov.aicte.platform.application.dto.CreateApplicationRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.common.exception.DuplicateResourceException;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.common.pagination.PageResponse;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import in.gov.aicte.platform.workflow.WorkflowService;
import in.gov.aicte.platform.workflow.dto.WorkflowActionRequest;
import in.gov.aicte.platform.workflow.dto.WorkflowTimelineItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.gov.aicte.platform.auth.dto.DevUserDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowService workflowService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            InstitutionRepository institutionRepository,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper,
            WorkflowService workflowService
    ) {
        this.applicationRepository = applicationRepository;
        this.institutionRepository = institutionRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, String actorId) {
        String reference = request.applicationReference().trim();

        if (applicationRepository.existsByApplicationReference(reference)) {
            throw new DuplicateResourceException("Application already exists with reference: " + reference);
        }

        Institution institution = null;
        if (request.institutionId() != null) {
            institution = institutionRepository.findById(request.institutionId()).orElse(null);
        }
        if (institution == null && request.institutionalIdentifier() != null && !request.institutionalIdentifier().isBlank()) {
            institution = institutionRepository.findByInstitutionalIdentifier(request.institutionalIdentifier().trim()).orElse(null);
        }
        if (institution == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof DevUserDto devUser && devUser.institutionId() != null) {
                institution = institutionRepository.findByInstitutionalIdentifier(devUser.institutionId()).orElse(null);
            }
        }
        if (institution == null) {
            String target = (request.institutionId() != null) ? String.valueOf(request.institutionId()) : request.institutionalIdentifier();
            throw new ResourceNotFoundException("Institution not found with id: " + target);
        }

        Application application = new Application(
                institution,
                reference,
                request.applicationType().trim(),
                request.academicYear().trim(),
                "DRAFT" // Initial prototype status
        );
        Application saved = applicationRepository.save(application);

        String effectiveActor = (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM";
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("action", "CREATE_APPLICATION");
        metadata.put("applicationReference", saved.getApplicationReference());
        metadata.put("institutionId", institution.getId());
        metadata.put("applicationType", saved.getApplicationType());
        metadata.put("academicYear", saved.getAcademicYear());

        String auditMetadata;
        try {
            auditMetadata = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit metadata for application id={}", saved.getId(), e);
            auditMetadata = "{}";
        }

        AuditEvent auditEvent = new AuditEvent(
                "APPLICATION_CREATED",
                effectiveActor,
                "APPLICATION",
                saved.getId().toString(),
                auditMetadata
        );
        auditEventRepository.save(auditEvent);

        log.info("Application created id={} reference='{}' for institutionId={} by actor='{}'",
                saved.getId(), saved.getApplicationReference(), institution.getId(), effectiveActor);
        return ApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(Long id) {
        return workflowService.getApplicationDetails(id.toString());
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationByIdOrReference(String idOrReference) {
        return workflowService.getApplicationDetails(idOrReference);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> listApplications(
            Long institutionId,
            String status,
            String academicYear,
            Pageable pageable
    ) {
        return listApplications(institutionId, status, academicYear, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> listApplications(
            Long institutionId,
            String status,
            String academicYear,
            String region,
            String stage,
            String search,
            Pageable pageable
    ) {
        Page<Application> page = applicationRepository.findAll(
                ApplicationSpecifications.withFilters(institutionId, status, academicYear, region, stage, search),
                pageable
        );
        return PageResponse.from(page.map(app -> {
            List<WorkflowTimelineItemResponse> timeline = Collections.emptyList();
            return ApplicationResponse.from(app, timeline);
        }));
    }

    @Transactional
    public ApplicationResponse executeWorkflowAction(String idOrReference, WorkflowActionRequest request, String actorId) {
        return workflowService.executeAction(idOrReference, request, actorId);
    }
}
