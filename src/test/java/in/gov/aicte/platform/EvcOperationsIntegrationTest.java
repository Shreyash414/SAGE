package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.auth.dto.TokenRequest;
import in.gov.aicte.platform.auth.dto.TokenResponse;
import in.gov.aicte.platform.evc.EvcVisit;
import in.gov.aicte.platform.evc.EvcVisitRepository;
import in.gov.aicte.platform.evc.dto.CompleteVisitRequest;
import in.gov.aicte.platform.evc.dto.RescheduleEvcRequest;
import in.gov.aicte.platform.evc.dto.ScheduleEvcRequest;
import in.gov.aicte.platform.evc.dto.SubmitEvcReportRequest;
import in.gov.aicte.platform.audit.AuditEvent;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import in.gov.aicte.platform.workflow.WorkflowService;
import in.gov.aicte.platform.workflow.WorkflowTransition;
import in.gov.aicte.platform.workflow.WorkflowTransitionRepository;
import in.gov.aicte.platform.workflow.dto.WorkflowActionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvcOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EvcVisitRepository evcVisitRepository;

    @Autowired
    private DevTokenService devTokenService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkflowTransitionRepository workflowTransitionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String evcToken;

    @BeforeEach
    void setUp() {
        TokenResponse tokenResp = devTokenService.issueToken("evc.coord@aicte-india.gov.in", "EVC Coordinator");
        this.evcToken = "Bearer " + tokenResp.access_token();
    }

    private Institution createSampleInstitution(String suffix) {
        Institution inst = new Institution(
                "INST-" + suffix,
                "Apex Institute of Technology " + suffix,
                "Northern Region"
        );
        return institutionRepository.save(inst);
    }

    private Application createSampleApplication(Institution inst, String stage, String status) {
        Application app = new Application(
                inst,
                "APP-2026-" + UUID.randomUUID().toString().substring(0, 8),
                "Extension of Approval (EoA)",
                "2026-2027",
                status,
                stage,
                "C. Verma (EVC Coordinator)",
                "USR-EVC-01",
                LocalDate.now().plusDays(10),
                "Scheduled for verification"
        );
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("GET /api/v1/evc returns list of EVC cases with valid response mapping")
    void testListEvcVisits() throws Exception {
        Institution inst = createSampleInstitution("TEST-LIST");
        Application app = createSampleApplication(inst, "EVC scheduling", "In progress");

        EvcVisit visit = new EvcVisit(app, "EVC-" + app.getId(), "C. Verma", "USR-EVC-01");
        evcVisitRepository.save(visit);

        mockMvc.perform(get("/api/v1/evc")
                        .header("Authorization", evcToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visit.getEvcReference() + "')]").exists());
    }

    @Test
    @DisplayName("GET /api/v1/evc/metrics returns all 6 KPIs correctly")
    void testGetEvcMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/evc/metrics")
                        .header("Authorization", evcToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").isNumber())
                .andExpect(jsonPath("$.pendingScheduling").isNumber())
                .andExpect(jsonPath("$.upcomingVisits").isNumber())
                .andExpect(jsonPath("$.completedVisits").isNumber())
                .andExpect(jsonPath("$.reportsPending").isNumber())
                .andExpect(jsonPath("$.overdueTasks").isNumber());
    }

    @Test
    @DisplayName("Full EVC Lifecycle: Schedule -> Complete Visit -> Submit Report -> Advances Application to Committee review")
    void testFullEvcLifecycle() throws Exception {
        Institution inst = createSampleInstitution("LIFECYCLE");
        Application app = createSampleApplication(inst, "EVC scheduling", "Due soon");

        EvcVisit visit = new EvcVisit(app, "EVC-" + app.getId(), "C. Verma", "USR-EVC-01");
        visit.setStatus("Awaiting scheduling");
        visit = evcVisitRepository.save(visit);

        String evcRef = visit.getEvcReference();

        // 1. Schedule Visit
        ScheduleEvcRequest scheduleReq = new ScheduleEvcRequest(
                LocalDate.now().plusDays(5),
                "C. Verma",
                "Physical On-Site Verification",
                List.of("Prof. R. Sharma (IIT Delhi)", "Dr. S. K. Gupta (NIT Kurukshetra)"),
                "C. Verma",
                "EVC Coordinator"
        );

        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/schedule")
                        .header("Authorization", evcToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Scheduled"))
                .andExpect(jsonPath("$.visitDate").value(scheduleReq.visitDate().toString()))
                .andExpect(jsonPath("$.experts.length()").value(2));

        // Verify in DB
        EvcVisit scheduledVisit = evcVisitRepository.findByEvcReference(evcRef).orElseThrow();
        assertThat(scheduledVisit.getStatus()).isEqualTo("Scheduled");
        assertThat(scheduledVisit.getVisitDate()).isEqualTo(scheduleReq.visitDate());

        // 2. Mark Visit Completed
        CompleteVisitRequest completeReq = new CompleteVisitRequest(
                "Inspection completed. Labs and faculty verified without critical deficiency.",
                "C. Verma",
                "EVC Coordinator"
        );

        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/complete")
                        .header("Authorization", evcToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.reportStatus").value("Report pending submission"))
                .andExpect(jsonPath("$.completionNotes").value(completeReq.completionNotes()));

        // 3. Submit Final EVC Report -> Must advance linked Application to 'Committee review'
        SubmitEvcReportRequest reportReq = new SubmitEvcReportRequest(
                "Recommended Approval",
                "All AICTE physical infrastructure norms satisfied as per APH 2026-27.",
                "C. Verma",
                "EVC Coordinator"
        );

        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/report")
                        .header("Authorization", evcToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Complete for stage"))
                .andExpect(jsonPath("$.reportStatus").value("Report Submitted & Verified"))
                .andExpect(jsonPath("$.reportOutcome").value("Recommended Approval"));

        // Verify linked application has advanced to Committee review
        Application updatedApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(updatedApp.getCurrentStage()).isEqualTo("Committee review");
        assertThat(updatedApp.getStatus()).isEqualTo("In progress");
        assertThat(updatedApp.getOwnerName()).contains("Committee");
    }

    @Test
    @DisplayName("Reschedule / Revert: Can revert completed visit back to scheduled")
    void testRescheduleRevert() throws Exception {
        Institution inst = createSampleInstitution("REVERT");
        Application app = createSampleApplication(inst, "EVC scheduling", "In progress");

        EvcVisit visit = new EvcVisit(app, "EVC-" + app.getId(), "C. Verma", "USR-EVC-01");
        visit.setStatus("Completed");
        visit = evcVisitRepository.save(visit);

        RescheduleEvcRequest reschedReq = new RescheduleEvcRequest(
                LocalDate.now().plusDays(7),
                "C. Verma",
                "EVC Coordinator"
        );

        mockMvc.perform(post("/api/v1/evc/" + visit.getEvcReference() + "/reschedule")
                        .header("Authorization", evcToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reschedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Scheduled"))
                .andExpect(jsonPath("$.visitDate").value(reschedReq.newVisitDate().toString()));
    }

    @Test
    @DisplayName("Workflow Integration: Clearing Scrutiny automatically creates an EVC work item")
    void testScrutinyClearanceCreatesEvcWorkItem() {
        Institution inst = createSampleInstitution("AUTO-EVC");
        Application app = createSampleApplication(inst, "Scrutiny", "In progress");

        WorkflowActionRequest clearReq = new WorkflowActionRequest(
                "COMPLETE_SCRUTINY",
                "B. Singh",
                "Scrutiny Officer",
                "Documents verified. Forwarding for physical EVC inspection.",
                "All mandatory statutory approvals present.",
                null,
                "EVC scheduling"
        );

        workflowService.executeAction(app.getApplicationReference(), clearReq, "USR-SO-01");

        // Verify application transitioned
        Application updated = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(updated.getCurrentStage()).isEqualTo("EVC scheduling");

        // Verify EVC visit record created
        EvcVisit visit = evcVisitRepository.findByApplicationId(app.getId()).orElse(null);
        assertThat(visit).isNotNull();
        assertThat(visit.getStatus()).isEqualTo("Awaiting scheduling");
        assertThat(visit.getEvcReference()).isEqualTo("EVC-" + app.getId());
    }

    @Test
    @DisplayName("Backend Authorization: Institution Representative receives 403 on all EVC mutation endpoints")
    void testInstitutionRepresentativeForbiddenFromAllMutationEndpoints() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Institution inst = createSampleInstitution("FORBIDDEN-" + uid);
        Application app = createSampleApplication(inst, "EVC scheduling", "In progress");

        EvcVisit visit = new EvcVisit(app, "EVC-" + app.getId(), "C. Verma", "USR-EVC-01");
        visit.setStatus("Awaiting scheduling");
        visit = evcVisitRepository.save(visit);
        String evcRef = visit.getEvcReference();

        // Create representative user for this institution
        User repUser = new User(
                "USR-REP-" + uid,
                "Dr. Representative " + uid,
                "rep." + uid + "@college.edu.in",
                "hashedPass123",
                "Institution Representative",
                "ACTIVE",
                "Northern Region",
                inst.getInstitutionalIdentifier(),
                inst.getName(),
                "Principal"
        );
        userRepository.save(repUser);
        String repToken = "Bearer " + devTokenService.issueToken(repUser.getEmail(), "Institution Representative").access_token();

        // 1. Attempt Schedule -> 403 Forbidden
        ScheduleEvcRequest schedReq = new ScheduleEvcRequest(
                LocalDate.now().plusDays(5),
                "Dr. Unauthorized",
                "Physical On-Site Verification",
                List.of("Expert One"),
                "Dr. Representative",
                "Institution Representative"
        );
        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/schedule")
                        .header("Authorization", repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(schedReq)))
                .andExpect(status().isForbidden());

        // Assert DB state unchanged
        EvcVisit dbVisitAfterSched = evcVisitRepository.findByEvcReference(evcRef).orElseThrow();
        assertThat(dbVisitAfterSched.getStatus()).isEqualTo("Awaiting scheduling");
        assertThat(dbVisitAfterSched.getVisitDate()).isNull();

        // 2. Attempt Complete -> 403 Forbidden
        CompleteVisitRequest compReq = new CompleteVisitRequest("Attempted completion", "Dr. Representative", "Institution Representative");
        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/complete")
                        .header("Authorization", repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compReq)))
                .andExpect(status().isForbidden());

        // Assert DB state unchanged
        EvcVisit dbVisitAfterComp = evcVisitRepository.findByEvcReference(evcRef).orElseThrow();
        assertThat(dbVisitAfterComp.getStatus()).isEqualTo("Awaiting scheduling");

        // 3. Attempt Report -> 403 Forbidden
        SubmitEvcReportRequest repReq = new SubmitEvcReportRequest("Recommended Approval", "Self-approved notes", "Dr. Representative", "Institution Representative");
        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/report")
                        .header("Authorization", repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(repReq)))
                .andExpect(status().isForbidden());

        // Assert Application state unchanged
        Application dbAppAfterReport = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(dbAppAfterReport.getCurrentStage()).isEqualTo("EVC scheduling");
        assertThat(dbAppAfterReport.getStatus()).isEqualTo("In progress");

        // 4. Attempt Reschedule -> 403 Forbidden
        RescheduleEvcRequest reschedReq = new RescheduleEvcRequest(LocalDate.now().plusDays(10), "Dr. Representative", "Institution Representative");
        mockMvc.perform(post("/api/v1/evc/" + evcRef + "/reschedule")
                        .header("Authorization", repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reschedReq)))
                .andExpect(status().isForbidden());

        EvcVisit dbVisitAfterResched = evcVisitRepository.findByEvcReference(evcRef).orElseThrow();
        assertThat(dbVisitAfterResched.getStatus()).isEqualTo("Awaiting scheduling");
    }

    @Test
    @DisplayName("Institution Scoping: Institution representative only accesses own visits; other institution visits return 403")
    void testInstitutionScopingOnVisitListingAndRetrieval() throws Exception {
        String uidA = UUID.randomUUID().toString().substring(0, 8);
        String uidB = UUID.randomUUID().toString().substring(0, 8);

        Institution instA = createSampleInstitution("SCOPED-A-" + uidA);
        Institution instB = createSampleInstitution("SCOPED-B-" + uidB);

        Application appA = createSampleApplication(instA, "EVC scheduling", "In progress");
        Application appB = createSampleApplication(instB, "EVC scheduling", "In progress");

        EvcVisit visitA = new EvcVisit(appA, "EVC-" + appA.getId(), "C. Verma", "USR-EVC-01");
        visitA = evcVisitRepository.save(visitA);

        EvcVisit visitB = new EvcVisit(appB, "EVC-" + appB.getId(), "C. Verma", "USR-EVC-01");
        visitB = evcVisitRepository.save(visitB);

        // Rep A for Institution A
        User repA = new User(
                "USR-REP-A-" + uidA,
                "Rep Alpha",
                "rep.a." + uidA + "@alpha.edu.in",
                "hashedPass123",
                "Institution Representative",
                "ACTIVE",
                "Northern Region",
                instA.getInstitutionalIdentifier(),
                instA.getName(),
                "Principal"
        );
        userRepository.save(repA);
        String tokenA = "Bearer " + devTokenService.issueToken(repA.getEmail(), "Institution Representative").access_token();

        // Rep B for Institution B
        User repB = new User(
                "USR-REP-B-" + uidB,
                "Rep Beta",
                "rep.b." + uidB + "@beta.edu.in",
                "hashedPass123",
                "Institution Representative",
                "ACTIVE",
                "Northern Region",
                instB.getInstitutionalIdentifier(),
                instB.getName(),
                "Dean"
        );
        userRepository.save(repB);
        String tokenB = "Bearer " + devTokenService.issueToken(repB.getEmail(), "Institution Representative").access_token();

        // 1. Rep A lists visits -> must only contain visitA, must NOT contain visitB
        mockMvc.perform(get("/api/v1/evc")
                        .header("Authorization", tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitA.getEvcReference() + "')]").exists())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitB.getEvcReference() + "')]").doesNotExist());

        // 2. Rep B lists visits -> must only contain visitB, must NOT contain visitA
        mockMvc.perform(get("/api/v1/evc")
                        .header("Authorization", tokenB)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitB.getEvcReference() + "')]").exists())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitA.getEvcReference() + "')]").doesNotExist());

        // 3. Rep A retrieves own visit by reference -> 200 OK
        mockMvc.perform(get("/api/v1/evc/" + visitA.getEvcReference())
                        .header("Authorization", tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evcReference").value(visitA.getEvcReference()));

        // 4. Rep A attempts to retrieve visit B by reference -> 403 Forbidden
        mockMvc.perform(get("/api/v1/evc/" + visitB.getEvcReference())
                        .header("Authorization", tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // 5. Rep A attempts to retrieve visit B by numeric ID -> 403 Forbidden
        mockMvc.perform(get("/api/v1/evc/" + visitB.getId())
                        .header("Authorization", tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // 6. Rep A attempts to retrieve visit B by application reference -> 403 Forbidden
        mockMvc.perform(get("/api/v1/evc/" + appB.getApplicationReference())
                        .header("Authorization", tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // 7. Authorized EVC role lists visits -> receives BOTH visits across institutions
        mockMvc.perform(get("/api/v1/evc")
                        .header("Authorization", evcToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitA.getEvcReference() + "')]").exists())
                .andExpect(jsonPath("$[?(@.evcReference == '" + visitB.getEvcReference() + "')]").exists());

        // 8. Authorized EVC role retrieves visit B -> 200 OK
        mockMvc.perform(get("/api/v1/evc/" + visitB.getEvcReference())
                        .header("Authorization", evcToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evcReference").value(visitB.getEvcReference()));
    }

    @Test
    @DisplayName("Audit Identity Integrity: Client-supplied actorName and actorRole are ignored in persisted audit records")
    void testAuditIdentityIntegrityCannotBeSpoofedByClient() throws Exception {
        Institution inst = createSampleInstitution("AUDIT-INTEGRITY");
        Application app = createSampleApplication(inst, "EVC scheduling", "In progress");

        EvcVisit visit = new EvcVisit(app, "EVC-" + app.getId(), "C. Verma", "USR-EVC-01");
        visit.setStatus("Completed");
        visit = evcVisitRepository.save(visit);

        // Submit report with forged actor credentials in client request body
        SubmitEvcReportRequest forgedReq = new SubmitEvcReportRequest(
                "Recommended Approval",
                "Satisfies all mandatory lab and infrastructural norms.",
                "Hon. Union Minister of Education",
                "Cabinet Minister"
        );

        mockMvc.perform(post("/api/v1/evc/" + visit.getEvcReference() + "/report")
                        .header("Authorization", evcToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Complete for stage"));

        // Verify persisted WorkflowTransition in database
        List<WorkflowTransition> transitions = workflowTransitionRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId());
        assertThat(transitions).isNotEmpty();
        WorkflowTransition reportTransition = transitions.stream()
                .filter(t -> "SUBMIT_EVC_REPORT".equals(t.getAction()))
                .findFirst()
                .orElseThrow();

        // The authoritative authenticated principal must be stored, NOT the forged client strings
        assertThat(reportTransition.getActorId()).isEqualTo("USR-EVC-01");
        assertThat(reportTransition.getActorName()).isEqualTo("C. Verma");
        assertThat(reportTransition.getActorRole()).isEqualTo("EVC Coordinator");
        assertThat(reportTransition.getActorName()).isNotEqualTo("Hon. Union Minister of Education");
        assertThat(reportTransition.getActorRole()).isNotEqualTo("Cabinet Minister");

        // Verify persisted AuditEvent
        List<AuditEvent> auditEvents = auditEventRepository.findByTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc("EVC_VISIT", visit.getEvcReference());
        assertThat(auditEvents).isNotEmpty();
        AuditEvent reportAudit = auditEvents.stream()
                .filter(a -> "EVC_REPORT_SUBMITTED".equals(a.getEventType()))
                .findFirst()
                .orElseThrow();

        assertThat(reportAudit.getActorId()).isEqualTo("USR-EVC-01");
        assertThat(reportAudit.getMetadataJson()).contains("\"actorName\":\"C. Verma\"");
        assertThat(reportAudit.getMetadataJson()).contains("\"actorRole\":\"EVC Coordinator\"");
        assertThat(reportAudit.getMetadataJson()).doesNotContain("Hon. Union Minister of Education");
    }
}
