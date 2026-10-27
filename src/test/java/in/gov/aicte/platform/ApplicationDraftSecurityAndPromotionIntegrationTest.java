package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.application.draft.ApplicationDraft;
import in.gov.aicte.platform.application.draft.ApplicationDraftRepository;
import in.gov.aicte.platform.application.draft.dto.CreateDraftRequest;
import in.gov.aicte.platform.application.draft.dto.DraftResponse;
import in.gov.aicte.platform.application.draft.dto.PromoteDraftResult;
import in.gov.aicte.platform.application.draft.dto.UpdateDraftRequest;
import in.gov.aicte.platform.auth.DevTokenService;
import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationDraftSecurityAndPromotionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private ApplicationDraftRepository draftRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DevTokenService devTokenService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    private Institution institutionA;
    private Institution institutionB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        String uidA = UUID.randomUUID().toString().substring(0, 8);
        institutionA = new Institution("INST-A-" + uidA, "Institution Alpha " + uidA, "Northern Region");
        institutionA = institutionRepository.save(institutionA);

        String uidB = UUID.randomUUID().toString().substring(0, 8);
        institutionB = new Institution("INST-B-" + uidB, "Institution Beta " + uidB, "Western Region");
        institutionB = institutionRepository.save(institutionB);

        User dbUserA = new User(
                "USR-REP-A-" + uidA,
                "Rep Alpha",
                "rep.a." + uidA + "@alpha.edu.in",
                "hashedPass123",
                "Institution Representative",
                "ACTIVE",
                "Northern Region",
                institutionA.getInstitutionalIdentifier(),
                institutionA.getName(),
                "Registrar"
        );
        userRepository.save(dbUserA);
        tokenA = devTokenService.issueToken(dbUserA.getEmail(), "Institution Representative").access_token();

        User dbUserB = new User(
                "USR-REP-B-" + uidB,
                "Rep Beta",
                "rep.b." + uidB + "@beta.edu.in",
                "hashedPass123",
                "Institution Representative",
                "ACTIVE",
                "Western Region",
                institutionB.getInstitutionalIdentifier(),
                institutionB.getName(),
                "Principal"
        );
        userRepository.save(dbUserB);
        tokenB = devTokenService.issueToken(dbUserB.getEmail(), "Institution Representative").access_token();

        when(minioStorageService.uploadDocument(anyLong(), anyString(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(new MinioStorageService.StoredObjectResult(
                        "drafts/1/DOC_LAND/v1_land.pdf",
                        "sage-application-documents",
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        2048L,
                        "application/pdf"
                ));

        when(minioStorageService.downloadDocument(anyString()))
                .thenReturn(new ByteArrayInputStream("mock document content".getBytes()));

        doNothing().when(minioStorageService).deleteDocument(anyString());
    }

    @Test
    @DisplayName("Draft Creation & Resumption: Institution A can create, save form data, and resume draft")
    void shouldCreateSaveAndResumeDraft() throws Exception {
        // 1. Create Draft
        CreateDraftRequest createReq = new CreateDraftRequest(
                "EXTENSION_OF_APPROVAL",
                "2026-2027",
                Map.of("institutionName", institutionA.getName())
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftReference", startsWith("DFT-2026-")))
                .andExpect(jsonPath("$.applicationType").value("EXTENSION_OF_APPROVAL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.institution.institutionalIdentifier").value(institutionA.getInstitutionalIdentifier()))
                .andReturn();

        DraftResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), DraftResponse.class);

        // 2. Update Draft Form Data
        Map<String, Object> fields = new HashMap<>();
        fields.put("totalEnrolment", "1200");
        fields.put("facultyCadreRatioCertified", "true");

        UpdateDraftRequest updateReq = new UpdateDraftRequest(fields, "2026-2027");
        mockMvc.perform(put("/api/v1/application-drafts/" + created.id())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formData.totalEnrolment").value("1200"))
                .andExpect(jsonPath("$.formData.facultyCadreRatioCertified").value("true"));

        // 3. Resume Draft after page refresh/new request
        mockMvc.perform(get("/api/v1/application-drafts/" + created.draftReference())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.draftReference").value(created.draftReference()))
                .andExpect(jsonPath("$.formData.totalEnrolment").value("1200"));

        // 4. Retrieve Draft Checklist
        mockMvc.perform(get("/api/v1/application-drafts/" + created.id() + "/checklist")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(created.id()))
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.documents").isArray());
    }

    @Test
    @DisplayName("Institution Isolation: Institution B cannot see Institution A's drafts in list")
    void shouldIsolateDraftListBetweenInstitutions() throws Exception {
        // Institution A creates a draft
        CreateDraftRequest createReq = new CreateDraftRequest("NEW_INSTITUTION", "2026-2027", null);
        MvcResult resA = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DraftResponse draftA = objectMapper.readValue(resA.getResponse().getContentAsString(), DraftResponse.class);

        // Institution A lists drafts -> Sees draftA
        mockMvc.perform(get("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].draftReference").value(draftA.draftReference()));

        // Institution B lists drafts -> Sees ZERO drafts of Institution A
        mockMvc.perform(get("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Access Control: Institution B cannot fetch, edit, delete, or submit Institution A's draft")
    void shouldRejectCrossInstitutionDraftAccess() throws Exception {
        // Institution A creates a draft
        CreateDraftRequest createReq = new CreateDraftRequest("EXTENSION_OF_APPROVAL", "2026-2027", null);
        MvcResult resA = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DraftResponse draftA = objectMapper.readValue(resA.getResponse().getContentAsString(), DraftResponse.class);

        // 1. Institution B attempts GET by ID -> 403 Forbidden
        mockMvc.perform(get("/api/v1/application-drafts/" + draftA.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 2. Institution B attempts GET by draft reference -> 403 Forbidden
        mockMvc.perform(get("/api/v1/application-drafts/" + draftA.draftReference())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 3. Institution B attempts PUT (modify) -> 403 Forbidden
        UpdateDraftRequest hackReq = new UpdateDraftRequest(Map.of("hacked", "true"), "2026-2027");
        mockMvc.perform(put("/api/v1/application-drafts/" + draftA.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hackReq)))
                .andExpect(status().isForbidden());

        // 4. Institution B attempts DELETE -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/application-drafts/" + draftA.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 5. Institution B attempts promote/submit -> 403 Forbidden
        mockMvc.perform(post("/api/v1/application-drafts/" + draftA.id() + "/promote")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document Security: Cross-institution upload and download are rejected with 403")
    void shouldRejectCrossInstitutionDocumentOperations() throws Exception {
        // Institution A creates draft
        CreateDraftRequest createReq = new CreateDraftRequest("EXTENSION_OF_APPROVAL", "2026-2027", null);
        MvcResult resA = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DraftResponse draftA = objectMapper.readValue(resA.getResponse().getContentAsString(), DraftResponse.class);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "land_deed.pdf",
                "application/pdf",
                "%PDF-1.4 test document bytes".getBytes()
        );

        // 1. Institution B attempts document upload to Draft A -> 403 Forbidden
        mockMvc.perform(multipart("/api/v1/application-drafts/" + draftA.id() + "/documents/DOC_PREVIOUS_EOA")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 2. Institution A uploads document legitimately -> 200 OK
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/application-drafts/" + draftA.id() + "/documents/DOC_PREVIOUS_EOA")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementCode").value("DOC_PREVIOUS_EOA"))
                .andExpect(jsonPath("$.documentId").exists())
                .andReturn();

        Long docId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("documentId").asLong();

        // 3. Institution B attempts download of Draft A document -> 403 Forbidden
        mockMvc.perform(get("/api/v1/application-drafts/" + draftA.id() + "/documents/" + docId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 4. Institution A downloads document -> 200 OK
        mockMvc.perform(get("/api/v1/application-drafts/" + draftA.id() + "/documents/" + docId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"land_deed.pdf\"")));
    }

    @Test
    @DisplayName("Completeness Gate: Incomplete draft submission blocked with 422 and structured errors")
    void shouldBlockIncompleteDraftSubmission() throws Exception {
        // Create empty draft
        CreateDraftRequest createReq = new CreateDraftRequest("EXTENSION_OF_APPROVAL", "2026-2027", null);
        MvcResult resA = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DraftResponse draftA = objectMapper.readValue(resA.getResponse().getContentAsString(), DraftResponse.class);

        // Attempt promotion when form and documents are empty
        mockMvc.perform(post("/api/v1/application-drafts/" + draftA.id() + "/promote")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.draftReference").value(draftA.draftReference()))
                .andExpect(jsonPath("$.validationErrors", notNullValue()));

        // Verify NO official application created in applications table
        List<Application> officialApps = applicationRepository.findByInstitutionId(institutionA.getId());
        assertThat(officialApps).isEmpty();

        // Verify draft remains in DRAFT status
        ApplicationDraft draft = draftRepository.findById(draftA.id()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Safe Promotion: 100% complete draft promotes into official application with immutable snapshot")
    void shouldSuccessfullyPromoteCompleteDraft() throws Exception {
        // Create draft
        CreateDraftRequest createReq = new CreateDraftRequest("EXTENSION_OF_APPROVAL", "2026-2027", null);
        MvcResult resA = mockMvc.perform(post("/api/v1/application-drafts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DraftResponse draftA = objectMapper.readValue(resA.getResponse().getContentAsString(), DraftResponse.class);

        // Fill all required fields for EXTENSION_OF_APPROVAL baseSections
        Map<String, Object> completeForm = new HashMap<>();
        completeForm.put("institutionName", "National Institute of Technology");
        completeForm.put("trustName", "Apex Education Trust");
        completeForm.put("trustRegistrationNumber", "REG/2012/1048");
        completeForm.put("panNumber", "AAATA1234F");
        completeForm.put("headOfInstitutionName", "Dr. Rajesh Sharma");
        completeForm.put("contactEmail", "director@institute.edu.in");
        completeForm.put("contactPhone", "9876543210");
        completeForm.put("campusCategory", "Urban");
        completeForm.put("landAreaAcres", "5.5");
        completeForm.put("totalBuiltUpAreaSqM", "12500");
        completeForm.put("instructionalAreaSqM", "7500");
        completeForm.put("administrativeAreaSqM", "2000");
        completeForm.put("antiRaggingDeclared", "true");
        completeForm.put("grievanceRedressalDeclared", "true");
        completeForm.put("internalComplaintsDeclared", "true");
        completeForm.put("barrierFreeDeclared", "true");
        completeForm.put("fireSafetyDeclared", "true");

        mockMvc.perform(put("/api/v1/application-drafts/" + draftA.id())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDraftRequest(completeForm, "2026-2027"))))
                .andExpect(status().isOk());

        // Upload all mandatory documents for EXTENSION_OF_APPROVAL
        String[] mandatoryDocs = {
                "DOC_PREVIOUS_EOA",
                "DOC_FACULTY_ROLL",
                "DOC_FIRE_SAFETY",
                "DOC_OCCUPANCY_CERT",
                "DOC_AUDITED_ACCOUNTS"
        };
        for (String reqCode : mandatoryDocs) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    reqCode.toLowerCase() + ".pdf",
                    "application/pdf",
                    ("mock bytes for " + reqCode).getBytes()
            );
            mockMvc.perform(multipart("/api/v1/application-drafts/" + draftA.id() + "/documents/" + reqCode)
                            .file(file)
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk());
        }

        // Verify checklist reports submittable = true
        mockMvc.perform(get("/api/v1/application-drafts/" + draftA.id() + "/checklist")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittable").value(true))
                .andExpect(jsonPath("$.completionPercentage").value(100));

        // Execute Promotion
        MvcResult promoteRes = mockMvc.perform(post("/api/v1/application-drafts/" + draftA.id() + "/promote")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.draftReference").value(draftA.draftReference()))
                .andExpect(jsonPath("$.officialApplicationReference", startsWith("APP-2026-")))
                .andExpect(jsonPath("$.status").value("In progress"))
                .andExpect(jsonPath("$.currentStage").value("Scrutiny"))
                .andReturn();

        PromoteDraftResult result = objectMapper.readValue(promoteRes.getResponse().getContentAsString(), PromoteDraftResult.class);

        // Verify official application entity created
        Application officialApp = applicationRepository.findById(result.officialApplicationId()).orElseThrow();
        assertThat(officialApp.getApplicationReference()).isEqualTo(result.officialApplicationReference());
        assertThat(officialApp.getInstitution().getId()).isEqualTo(institutionA.getId());
        assertThat(officialApp.getSubmittedAt()).isNotNull();
        assertThat(officialApp.getSubmittedSnapshotJson()).contains("documents");

        // Verify documents are now associated with official application
        List<ApplicationDocument> linkedDocs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(officialApp.getId());
        assertThat(linkedDocs).hasSize(5);

        // Verify draft status is PROMOTED
        ApplicationDraft updatedDraft = draftRepository.findById(draftA.id()).orElseThrow();
        assertThat(updatedDraft.getStatus()).isEqualTo("PROMOTED");
        assertThat(updatedDraft.getPromotedApplication().getId()).isEqualTo(officialApp.getId());

        // Verify duplicate submission on promoted draft is blocked
        mockMvc.perform(post("/api/v1/application-drafts/" + draftA.id() + "/promote")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        // Verify editing promoted draft is blocked
        mockMvc.perform(put("/api/v1/application-drafts/" + draftA.id())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDraftRequest(Map.of(), "2026-2027"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Root Cause Resolution: Non-existent reference returns standard 404 ProblemDetail without crash")
    void shouldReturn404ForNonExistentReference() throws Exception {
        // Investigated defect reference: SAGE-NEW-INSTITUTION-2026-5900
        mockMvc.perform(get("/api/v1/applications/SAGE-NEW-INSTITUTION-2026-5900")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value("Application not found with reference: SAGE-NEW-INSTITUTION-2026-5900"));
    }
}
