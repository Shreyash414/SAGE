package in.gov.aicte.platform.evc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import in.gov.aicte.platform.evc.dto.CompleteVisitRequest;
import in.gov.aicte.platform.evc.dto.EvcMetricsResponse;
import in.gov.aicte.platform.evc.dto.EvcResponse;
import in.gov.aicte.platform.evc.dto.RescheduleEvcRequest;
import in.gov.aicte.platform.evc.dto.ScheduleEvcRequest;
import in.gov.aicte.platform.evc.dto.SubmitEvcReportRequest;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import in.gov.aicte.platform.workflow.WorkflowTransition;
import in.gov.aicte.platform.workflow.WorkflowTransitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvcService {

    private static final Logger log = LoggerFactory.getLogger(EvcService.class);

    private final EvcVisitRepository evcVisitRepository;
    private final ApplicationRepository applicationRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final AuditEventRepository auditEventRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final EvcMemberProfileRepository evcMemberProfileRepository;
    private final EvcMemberAssignmentRepository evcMemberAssignmentRepository;

    public EvcService(
            EvcVisitRepository evcVisitRepository,
            ApplicationRepository applicationRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            AuditEventRepository auditEventRepository,
            InstitutionRepository institutionRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this(evcVisitRepository, applicationRepository, workflowTransitionRepository, auditEventRepository, institutionRepository, userRepository, objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EvcService(
            EvcVisitRepository evcVisitRepository,
            ApplicationRepository applicationRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            AuditEventRepository auditEventRepository,
            InstitutionRepository institutionRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @org.springframework.context.annotation.Lazy EvcMemberProfileRepository evcMemberProfileRepository,
            @org.springframework.context.annotation.Lazy EvcMemberAssignmentRepository evcMemberAssignmentRepository
    ) {
        this.evcVisitRepository = evcVisitRepository;
        this.applicationRepository = applicationRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.auditEventRepository = auditEventRepository;
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.evcMemberProfileRepository = evcMemberProfileRepository;
        this.evcMemberAssignmentRepository = evcMemberAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<EvcResponse> listVisits(String status, String region, String q) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<EvcVisit> source;

        if (isInstitutionRepresentative(auth)) {
            Institution inst = resolveAuthenticatedInstitution(auth);
            source = evcVisitRepository.findByApplicationInstitutionId(inst.getId());
        } else if (isEvcMember(auth)) {
            source = getVisitsAssignedToMember(auth.getName());
        } else {
            source = evcVisitRepository.findAll();
        }

        return source.stream()
                .filter(v -> {
                    if (status != null && !status.isBlank()) {
                        if (!status.equalsIgnoreCase(v.getStatus())) {
                            return false;
                        }
                    }
                    if (region != null && !region.isBlank()) {
                        String reg = (v.getApplication() != null && v.getApplication().getInstitution() != null)
                                ? v.getApplication().getInstitution().getRegion()
                                : null;
                        if (reg == null || !reg.equalsIgnoreCase(region.trim())) {
                            return false;
                        }
                    }
                    if (q != null && !q.isBlank()) {
                        String term = q.trim().toLowerCase();
                        String ref = v.getEvcReference() != null ? v.getEvcReference().toLowerCase() : "";
                        String appRef = (v.getApplication() != null && v.getApplication().getApplicationReference() != null)
                                ? v.getApplication().getApplicationReference().toLowerCase()
                                : "";
                        String instName = (v.getApplication() != null && v.getApplication().getInstitution() != null)
                                ? v.getApplication().getInstitution().getName().toLowerCase()
                                : "";
                        String coord = v.getCoordinatorName() != null ? v.getCoordinatorName().toLowerCase() : "";

                        return ref.contains(term) || appRef.contains(term) || instName.contains(term) || coord.contains(term);
                    }
                    return true;
                })
                .map(EvcResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EvcResponse getVisitByIdOrReference(String idOrReference) {
        EvcVisit visit = findVisit(idOrReference);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isInstitutionRepresentative(auth)) {
            Institution inst = resolveAuthenticatedInstitution(auth);
            Institution visitInst = (visit.getApplication() != null) ? visit.getApplication().getInstitution() : null;
            if (visitInst == null || (!Objects.equals(visitInst.getId(), inst.getId())
                    && !Objects.equals(visitInst.getInstitutionalIdentifier(), inst.getInstitutionalIdentifier()))) {
                throw new AccessDeniedException("Access denied: EVC visit belongs to another institution.");
            }
        } else if (isEvcMember(auth)) {
            if (!isMemberAssignedToVisit(auth.getName(), visit)) {
                throw new AccessDeniedException("Access denied: You are not assigned to this EVC inspection visit.");
            }
        }

        return EvcResponse.from(visit);
    }

    @Transactional(readOnly = true)
    public EvcMetricsResponse getMetrics() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<EvcVisit> visits;
        if (isInstitutionRepresentative(auth)) {
            Institution inst = resolveAuthenticatedInstitution(auth);
            visits = evcVisitRepository.findByApplicationInstitutionId(inst.getId());
        } else if (isEvcMember(auth)) {
            visits = getVisitsAssignedToMember(auth.getName());
        } else {
            visits = evcVisitRepository.findAll();
        }
        LocalDate today = LocalDate.now();

        long totalCases = visits.size();
        long pendingScheduling = visits.stream()
                .filter(v -> "Awaiting scheduling".equalsIgnoreCase(v.getStatus()))
                .count();
        long upcomingVisits = visits.stream()
                .filter(v -> "Scheduled".equalsIgnoreCase(v.getStatus()))
                .count();
        long completedVisits = visits.stream()
                .filter(v -> "Completed".equalsIgnoreCase(v.getStatus()) || "Complete for stage".equalsIgnoreCase(v.getStatus()))
                .count();
        long reportsPending = visits.stream()
                .filter(v -> "Report pending".equalsIgnoreCase(v.getStatus()) ||
                        ("Completed".equalsIgnoreCase(v.getStatus()) && !"Complete for stage".equalsIgnoreCase(v.getStatus())))
                .count();
        long overdueTasks = visits.stream()
                .filter(v -> "Overdue".equalsIgnoreCase(v.getUrgency()) ||
                        (v.getDeadline() != null && v.getDeadline().isBefore(today) && !"Complete for stage".equalsIgnoreCase(v.getStatus())))
                .count();

        return new EvcMetricsResponse(
                totalCases,
                pendingScheduling,
                upcomingVisits,
                completedVisits,
                reportsPending,
                overdueTasks
        );
    }

    @Transactional
    public EvcResponse scheduleVisit(String idOrReference, ScheduleEvcRequest request, String actorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isInstitutionRepresentative(auth)) {
            throw new AccessDeniedException("Institution representatives are not authorized to schedule EVC visits.");
        }
        if (isEvcMember(auth)) {
            throw new AccessDeniedException("EVC members are not authorized to schedule EVC visits.");
        }

        EvcVisit visit = findVisit(idOrReference);

        if ("Completed".equalsIgnoreCase(visit.getStatus()) || "Complete for stage".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalArgumentException(String.format("Cannot schedule an inspection that is already '%s'. Revert visit status first if rescheduling is needed.", visit.getStatus()));
        }

        if (request.visitDate() == null) {
            throw new IllegalArgumentException("A confirmed inspection visit date is required.");
        }

        visit.setVisitDate(request.visitDate());
        if (request.coordinator() != null && !request.coordinator().isBlank()) {
            visit.setCoordinatorName(request.coordinator().trim());
        }
        if (request.inspectionType() != null && !request.inspectionType().isBlank()) {
            visit.setInspectionType(request.inspectionType().trim());
        }

        if (request.experts() != null && !request.experts().isEmpty()) {
            try {
                visit.setExpertsJson(objectMapper.writeValueAsString(request.experts()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize experts JSON: {}", e.getMessage());
            }
        }

        visit.setStatus("Scheduled");
        visit.setUrgency("In progress");

        EvcVisit saved = evcVisitRepository.save(visit);

        AuthenticatedActor actor = resolveAuthenticatedActor(actorId);

        // Record audit with authoritative authenticated identity
        saveAuditEvent("EVC_VISIT_SCHEDULED", actor.id(), "EVC_VISIT", saved.getEvcReference(), Map.of(
                "visitDate", saved.getVisitDate().toString(),
                "coordinator", saved.getCoordinatorName(),
                "inspectionType", saved.getInspectionType(),
                "actorName", actor.displayName(),
                "actorRole", actor.role()
        ));

        return EvcResponse.from(saved);
    }

    @Transactional
    public EvcResponse completeVisit(String idOrReference, CompleteVisitRequest request, String actorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isInstitutionRepresentative(auth)) {
            throw new AccessDeniedException("Institution representatives are not authorized to complete EVC visits.");
        }
        if (isEvcMember(auth)) {
            throw new AccessDeniedException("EVC members are not authorized to complete EVC visits.");
        }

        EvcVisit visit = findVisit(idOrReference);

        if (!"Scheduled".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalArgumentException(String.format("Only visits in 'Scheduled' state can be marked as completed. Current status is '%s'.", visit.getStatus()));
        }

        visit.setStatus("Completed");
        visit.setReportStatus("Report pending submission");
        if (request.completionNotes() != null && !request.completionNotes().isBlank()) {
            visit.setCompletionNotes(request.completionNotes().trim());
        }

        EvcVisit saved = evcVisitRepository.save(visit);

        AuthenticatedActor actor = resolveAuthenticatedActor(actorId);

        // Record audit with authoritative authenticated identity
        saveAuditEvent("EVC_VISIT_COMPLETED", actor.id(), "EVC_VISIT", saved.getEvcReference(), Map.of(
                "status", "Completed",
                "completionNotes", saved.getCompletionNotes() != null ? saved.getCompletionNotes() : "",
                "actorName", actor.displayName(),
                "actorRole", actor.role()
        ));

        return EvcResponse.from(saved);
    }

    @Transactional
    public EvcResponse submitReport(String idOrReference, SubmitEvcReportRequest request, String actorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isInstitutionRepresentative(auth)) {
            throw new AccessDeniedException("Institution representatives are not authorized to submit EVC inspection reports.");
        }
        if (isEvcMember(auth)) {
            throw new AccessDeniedException("EVC members are not authorized to submit reports directly. Operational coordinator review required.");
        }

        EvcVisit visit = findVisit(idOrReference);

        if (!"Completed".equalsIgnoreCase(visit.getStatus()) && !"Report pending".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalArgumentException(String.format("Inspection report can only be submitted after visit completion. Current status is '%s'.", visit.getStatus()));
        }

        if (request.reportOutcome() == null || request.reportOutcome().isBlank()) {
            throw new IllegalArgumentException("Inspection report recommendation/outcome is mandatory.");
        }

        visit.setStatus("Complete for stage");
        visit.setReportStatus("Report Submitted & Verified");
        visit.setReportOutcome(request.reportOutcome().trim());
        if (request.reportNotes() != null && !request.reportNotes().isBlank()) {
            visit.setReportNotes(request.reportNotes().trim());
        }

        EvcVisit saved = evcVisitRepository.save(visit);

        AuthenticatedActor actor = resolveAuthenticatedActor(actorId);

        // Advance linked Application to 'Committee review'
        Application application = saved.getApplication();
        if (application != null) {
            String fromStage = application.getCurrentStage() != null ? application.getCurrentStage() : "EVC scheduling";
            String fromStatus = application.getStatus();
            String toStage = "Committee review";
            String toStatus = "In progress";

            application.setCurrentStage(toStage);
            application.setStatus(toStatus);
            application.setOwnerName("Standing Scrutiny / Hearing Committee");
            application.setOwnerId("USR-SAC-01");
            applicationRepository.save(application);

            // Record workflow transition with authoritative authenticated identity
            WorkflowTransition transition = new WorkflowTransition(
                    application,
                    "SUBMIT_EVC_REPORT",
                    fromStage,
                    toStage,
                    fromStatus,
                    toStatus,
                    actor.id(),
                    actor.displayName(),
                    actor.role(),
                    "EVC Inspection report submitted with outcome: " + saved.getReportOutcome(),
                    saved.getReportNotes(),
                    null
            );
            workflowTransitionRepository.save(transition);

            // Record workflow audit event
            saveAuditEvent("WORKFLOW_ACTION_EXECUTED", actor.id(), "APPLICATION", application.getApplicationReference(), Map.of(
                    "action", "SUBMIT_EVC_REPORT",
                    "fromStage", fromStage,
                    "toStage", toStage,
                    "reportOutcome", saved.getReportOutcome(),
                    "actorName", actor.displayName(),
                    "actorRole", actor.role()
            ));
        }

        // Record EVC audit event
        saveAuditEvent("EVC_REPORT_SUBMITTED", actor.id(), "EVC_VISIT", saved.getEvcReference(), Map.of(
                "reportOutcome", saved.getReportOutcome(),
                "actorName", actor.displayName(),
                "actorRole", actor.role()
        ));

        return EvcResponse.from(saved);
    }

    @Transactional
    public EvcResponse rescheduleVisit(String idOrReference, RescheduleEvcRequest request, String actorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isInstitutionRepresentative(auth)) {
            throw new AccessDeniedException("Institution representatives are not authorized to reschedule EVC visits.");
        }
        if (isEvcMember(auth)) {
            throw new AccessDeniedException("EVC members are not authorized to reschedule EVC visits.");
        }

        EvcVisit visit = findVisit(idOrReference);

        visit.setStatus("Scheduled");
        visit.setReportStatus("Not Applicable");
        if (request.newVisitDate() != null) {
            visit.setVisitDate(request.newVisitDate());
        }

        EvcVisit saved = evcVisitRepository.save(visit);

        AuthenticatedActor actor = resolveAuthenticatedActor(actorId);

        saveAuditEvent("EVC_VISIT_RESCHEDULED", actor.id(), "EVC_VISIT", saved.getEvcReference(), Map.of(
                "status", "Scheduled",
                "newVisitDate", saved.getVisitDate() != null ? saved.getVisitDate().toString() : "Unchanged",
                "actorName", actor.displayName(),
                "actorRole", actor.role()
        ));

        return EvcResponse.from(saved);
    }

    @Transactional
    public EvcVisit ensureEvcVisitForApplication(Application application) {
        if (application == null || application.getId() == null) {
            return null;
        }

        return evcVisitRepository.findByApplicationId(application.getId())
                .orElseGet(() -> {
                    String ref = "EVC-" + application.getId();
                    EvcVisit visit = new EvcVisit(
                            application,
                            ref,
                            application.getOwnerName() != null ? application.getOwnerName() : "C. Verma (EVC Operations)",
                            application.getOwnerId() != null ? application.getOwnerId() : "USR-EVC-01"
                    );
                    if (application.getDueDate() != null) {
                        visit.setDeadline(application.getDueDate());
                    } else {
                        visit.setDeadline(LocalDate.now().plusDays(14));
                    }
                    EvcVisit created = evcVisitRepository.save(visit);
                    log.info("Created new EVC visit record {} for application {}", ref, application.getApplicationReference());
                    return created;
                });
    }

    private boolean isInstitutionRepresentative(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INSTITUTION_REPRESENTATIVE".equalsIgnoreCase(a.getAuthority()));
    }

    public boolean isEvcMember(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_EVC_MEMBER".equalsIgnoreCase(a.getAuthority()));
    }

    @Transactional(readOnly = true)
    public List<EvcVisit> getVisitsAssignedToMember(String userIdOrMemberId) {
        if (userIdOrMemberId == null || userIdOrMemberId.isBlank()) {
            return Collections.emptyList();
        }
        Set<Long> visitIds = new LinkedHashSet<>();

        // 1. Check assignments via database repository
        if (evcMemberAssignmentRepository != null) {
            List<EvcMemberAssignment> assignments = evcMemberAssignmentRepository.findByMemberUserUserId(userIdOrMemberId);
            if (assignments.isEmpty()) {
                assignments = evcMemberAssignmentRepository.findByMemberMemberId(userIdOrMemberId);
            }
            for (EvcMemberAssignment a : assignments) {
                if (a.getEvcVisit() != null) {
                    visitIds.add(a.getEvcVisit().getId());
                }
            }
        }

        // 2. Resolve member name for legacy JSON matching
        String memberName = null;
        if (evcMemberProfileRepository != null) {
            Optional<EvcMemberProfile> prof = evcMemberProfileRepository.findByUserUserId(userIdOrMemberId)
                    .or(() -> evcMemberProfileRepository.findByMemberId(userIdOrMemberId));
            if (prof.isPresent()) {
                memberName = prof.get().getFullName();
            }
        }
        if (memberName == null && userRepository != null) {
            Optional<User> u = userRepository.findByUserId(userIdOrMemberId)
                    .or(() -> userRepository.findByEmailIgnoreCase(userIdOrMemberId));
            if (u.isPresent()) {
                memberName = u.get().getFullName();
            }
        }

        // 3. Fallback matching against expertsJson
        List<EvcVisit> allVisits = evcVisitRepository.findAll();
        for (EvcVisit v : allVisits) {
            if (visitIds.contains(v.getId())) continue;
            String json = v.getExpertsJson();
            if (json != null && !json.isBlank()) {
                String lower = json.toLowerCase();
                if (lower.contains(userIdOrMemberId.toLowerCase()) || (memberName != null && lower.contains(memberName.toLowerCase()))) {
                    visitIds.add(v.getId());
                }
            }
        }

        return allVisits.stream().filter(v -> visitIds.contains(v.getId())).toList();
    }

    @Transactional(readOnly = true)
    public boolean isMemberAssignedToVisit(String userIdOrMemberId, EvcVisit visit) {
        if (visit == null || userIdOrMemberId == null) return false;
        List<EvcVisit> assigned = getVisitsAssignedToMember(userIdOrMemberId);
        return assigned.stream().anyMatch(v -> Objects.equals(v.getId(), visit.getId()));
    }

    public EvcVisit findVisitByIdOrReference(String idOrReference) {
        return findVisit(idOrReference);
    }

    private Institution resolveAuthenticatedInstitution(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof DevUserDto user) {
            if (user.institutionId() != null && !user.institutionId().isBlank()) {
                Optional<Institution> instOpt = institutionRepository.findByInstitutionalIdentifier(user.institutionId());
                if (instOpt.isPresent()) {
                    return instOpt.get();
                }
                try {
                    Long numericId = Long.parseLong(user.institutionId());
                    instOpt = institutionRepository.findById(numericId);
                    if (instOpt.isPresent()) {
                        return instOpt.get();
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (user.id() != null) {
                Optional<User> uOpt = userRepository.findByUserId(user.id());
                if (uOpt.isPresent() && uOpt.get().getInstitutionId() != null) {
                    return resolveInstitutionByIdentifierOrId(uOpt.get().getInstitutionId());
                }
            }
        }

        if (auth != null && auth.getName() != null) {
            Optional<User> uOpt = userRepository.findByUserId(auth.getName())
                    .or(() -> userRepository.findByEmailIgnoreCase(auth.getName()));
            if (uOpt.isPresent() && uOpt.get().getInstitutionId() != null) {
                return resolveInstitutionByIdentifierOrId(uOpt.get().getInstitutionId());
            }
        }

        throw new AccessDeniedException("Authenticated institution identity could not be established.");
    }

    private Institution resolveInstitutionByIdentifierOrId(String identifierOrId) {
        if (identifierOrId == null || identifierOrId.isBlank()) {
            throw new AccessDeniedException("Institution identifier is required.");
        }
        return institutionRepository.findByInstitutionalIdentifier(identifierOrId)
                .or(() -> {
                    try {
                        Long num = Long.parseLong(identifierOrId);
                        return institutionRepository.findById(num);
                    } catch (NumberFormatException ignored) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new AccessDeniedException("Institution not found for identifier: " + identifierOrId));
    }

    private record AuthenticatedActor(String id, String displayName, String role) {}

    private AuthenticatedActor resolveAuthenticatedActor(String fallbackActorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getDetails() instanceof DevUserDto user) {
                String id = (user.id() != null && !user.id().isBlank()) ? user.id() : auth.getName();
                String name = (user.name() != null && !user.name().isBlank()) ? user.name() : id;
                String role = (user.role() != null && !user.role().isBlank()) ? user.role() : "EVC Coordinator";
                return new AuthenticatedActor(id, name, role);
            }
            if (auth.getName() != null && !auth.getName().isBlank()) {
                String principalName = auth.getName();
                Optional<User> uOpt = userRepository.findByUserId(principalName)
                        .or(() -> userRepository.findByEmailIgnoreCase(principalName));
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    return new AuthenticatedActor(u.getUserId(), u.getFullName(), u.getRole());
                }
                return new AuthenticatedActor(principalName, principalName, "EVC Coordinator");
            }
        }
        String safeId = (fallbackActorId != null && !fallbackActorId.isBlank()) ? fallbackActorId : "SYSTEM";
        return new AuthenticatedActor(safeId, safeId, "SYSTEM");
    }

    private EvcVisit findVisit(String idOrReference) {
        if (idOrReference == null || idOrReference.isBlank()) {
            throw new ResourceNotFoundException("EVC visit identifier cannot be empty.");
        }

        // Try numeric ID
        try {
            Long numericId = Long.parseLong(idOrReference.trim());
            return evcVisitRepository.findById(numericId)
                    .orElseGet(() -> evcVisitRepository.findByApplicationId(numericId)
                            .orElseThrow(() -> new ResourceNotFoundException("EVC visit not found for ID: " + idOrReference)));
        } catch (NumberFormatException ignored) {
        }

        // Try EVC reference (e.g. EVC-1)
        return evcVisitRepository.findByEvcReference(idOrReference.trim())
                .or(() -> evcVisitRepository.findByApplicationApplicationReference(idOrReference.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("EVC visit not found for reference: " + idOrReference));
    }

    private void saveAuditEvent(String eventType, String actorId, String targetType, String targetId, Map<String, Object> metadata) {
        String metaStr;
        try {
            metaStr = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            metaStr = "{}";
        }
        String effectiveActor = (actorId != null && !actorId.isBlank()) ? actorId : "SYSTEM";
        AuditEvent event = new AuditEvent(eventType, effectiveActor, targetType, targetId, metaStr);
        auditEventRepository.save(event);
    }
}
