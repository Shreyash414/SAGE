package in.gov.aicte.platform.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.dto.ApplicationResponse;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.workflow.dto.WorkflowActionRequest;
import in.gov.aicte.platform.workflow.dto.WorkflowTimelineItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import in.gov.aicte.platform.evc.EvcService;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final ApplicationRepository applicationRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final EvcService evcService;

    public WorkflowService(
            ApplicationRepository applicationRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper,
            EvcService evcService
    ) {
        this.applicationRepository = applicationRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.evcService = evcService;
    }

    @Transactional
    public ApplicationResponse executeAction(String idOrReference, WorkflowActionRequest request, String actorId) {
        Application application = findApplication(idOrReference);

        String action = request.actionType() != null ? request.actionType().trim().toUpperCase() : "";
        String fromStage = application.getCurrentStage() != null ? application.getCurrentStage() : "Scrutiny";
        String fromStatus = application.getStatus();
        String toStage;
        String toStatus;
        String deficienciesJson = null;

        String effectiveActor = (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM";
        if (effectiveActor.length() > 64) {
            effectiveActor = effectiveActor.substring(0, 64);
        }
        String actorDisplay = (request.actorName() != null && !request.actorName().isBlank()) ? request.actorName() : effectiveActor;
        if (actorDisplay.length() > 128) {
            actorDisplay = actorDisplay.substring(0, 128);
        }

        switch (action) {
            case "RETURN_FOR_CORRECTION" -> {
                if (!"Scrutiny".equalsIgnoreCase(fromStage) && !"Re-scrutiny".equalsIgnoreCase(fromStage)) {
                    throw new IllegalArgumentException(String.format("Cannot return application from stage '%s'. Must be in Scrutiny or Re-scrutiny.", fromStage));
                }
                if (request.reason() == null || request.reason().trim().isBlank()) {
                    throw new IllegalArgumentException("A mandatory reason is required to return an application for correction.");
                }

                toStage = "Re-scrutiny";
                toStatus = "Returned";

                List<String> defs = (request.newDeficiencies() != null && !request.newDeficiencies().isEmpty())
                        ? request.newDeficiencies()
                        : List.of(request.reason().trim());

                try {
                    deficienciesJson = objectMapper.writeValueAsString(defs);
                } catch (JsonProcessingException e) {
                    deficienciesJson = "[\"" + request.reason().replace("\"", "\\\"") + "\"]";
                }

                application.setCurrentStage(toStage);
                application.setStatus(toStatus);
                application.setOwnerName("Institution Representative");
                application.setOwnerId("USR-INST-01");
                application.setDeficienciesJson(deficienciesJson);
                if (request.observations() != null && !request.observations().isBlank()) {
                    application.setObservations(request.observations().trim());
                }
            }

            case "SUBMIT_CORRECTIONS" -> {
                if (!"Returned".equalsIgnoreCase(fromStatus)) {
                    throw new IllegalArgumentException(String.format("Cannot submit corrections for application with status '%s'. Status must be 'Returned'.", fromStatus));
                }
                if (request.reason() == null || request.reason().trim().isBlank()) {
                    throw new IllegalArgumentException("An explanatory note or reason is required when submitting rectifications.");
                }

                toStage = "Re-scrutiny";
                toStatus = "In progress";

                application.setCurrentStage(toStage);
                application.setStatus(toStatus);
                application.setOwnerName("B. Singh (Scrutiny Officer)");
                application.setOwnerId("USR-SO-01");
                application.setDeficienciesJson(null); // Cleared upon resubmission
            }

            case "COMPLETE_SCRUTINY" -> {
                if (!"Scrutiny".equalsIgnoreCase(fromStage) && !"Re-scrutiny".equalsIgnoreCase(fromStage)) {
                    throw new IllegalArgumentException(String.format("Cannot clear scrutiny from stage '%s'. Must be in Scrutiny or Re-scrutiny.", fromStage));
                }

                toStage = (request.nextStage() != null && !request.nextStage().isBlank()) ? request.nextStage().trim() : "EVC scheduling";
                toStatus = "Due soon";

                application.setCurrentStage(toStage);
                application.setStatus(toStatus);
                application.setOwnerName("C. Verma (EVC Coordinator)");
                application.setOwnerId("USR-EVC-01");
                if (request.observations() != null && !request.observations().isBlank()) {
                    application.setObservations(request.observations().trim());
                }
            }

            default -> throw new IllegalArgumentException("Unsupported workflow action: " + request.actionType());
        }

        Application savedApp = applicationRepository.save(application);

        // If stage transitioned to EVC scheduling, ensure an EVC work item exists
        if ("EVC scheduling".equalsIgnoreCase(toStage)) {
            evcService.ensureEvcVisitForApplication(savedApp);
        }

        // Record workflow transition
        WorkflowTransition transition = new WorkflowTransition(
                savedApp,
                action,
                fromStage,
                toStage,
                fromStatus,
                toStatus,
                effectiveActor,
                actorDisplay,
                request.actorRole(),
                request.reason(),
                request.observations(),
                deficienciesJson
        );
        workflowTransitionRepository.save(transition);

        // Record AuditEvent atomically in same transaction
        Map<String, Object> auditMeta = new LinkedHashMap<>();
        auditMeta.put("action", action);
        auditMeta.put("applicationReference", savedApp.getApplicationReference());
        auditMeta.put("fromStage", fromStage);
        auditMeta.put("toStage", toStage);
        auditMeta.put("fromStatus", fromStatus);
        auditMeta.put("toStatus", toStatus);
        auditMeta.put("reason", request.reason());
        auditMeta.put("actorName", actorDisplay);

        String auditMetadata;
        try {
            auditMetadata = objectMapper.writeValueAsString(auditMeta);
        } catch (JsonProcessingException e) {
            auditMetadata = "{}";
        }

        AuditEvent auditEvent = new AuditEvent(
                "WORKFLOW_ACTION_" + action,
                effectiveActor,
                "APPLICATION",
                savedApp.getId().toString(),
                auditMetadata
        );
        auditEventRepository.save(auditEvent);

        log.info("Workflow action executed: action={} appRef={} fromStage={} toStage={} actor={}",
                action, savedApp.getApplicationReference(), fromStage, toStage, effectiveActor);

        List<WorkflowTimelineItemResponse> timeline = getTimeline(savedApp.getId());
        return ApplicationResponse.from(savedApp, timeline);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationDetails(String idOrReference) {
        Application app = findApplication(idOrReference);
        List<WorkflowTimelineItemResponse> timeline = getTimeline(app.getId());
        return ApplicationResponse.from(app, timeline);
    }

    public List<WorkflowTimelineItemResponse> getTimeline(Long applicationId) {
        List<WorkflowTransition> transitions = workflowTransitionRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (transitions.isEmpty()) {
            return Collections.emptyList();
        }
        return transitions.stream().map(WorkflowTimelineItemResponse::from).toList();
    }

    public Application findApplication(String idOrReference) {
        if (idOrReference == null || idOrReference.isBlank()) {
            throw new ResourceNotFoundException("Application identifier cannot be empty.");
        }

        String trimmed = idOrReference.trim();
        if (trimmed.matches("\\d+")) {
            Long numericId = Long.parseLong(trimmed);
            return applicationRepository.findById(numericId)
                    .orElseGet(() -> applicationRepository.findByApplicationReference(trimmed)
                            .orElseThrow(() -> new ResourceNotFoundException("Application not found with identifier: " + trimmed)));
        }

        return applicationRepository.findByApplicationReference(trimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with reference: " + trimmed));
    }
}
