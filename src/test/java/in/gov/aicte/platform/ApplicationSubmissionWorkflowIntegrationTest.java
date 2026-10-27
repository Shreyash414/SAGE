package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.application.dto.CreateApplicationRequest;
import in.gov.aicte.platform.application.dto.SaveDraftRequest;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
class ApplicationSubmissionWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @MockitoBean
    private MinioStorageService minioStorageService;

    private Institution institution;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        institution = new Institution("INST-" + uid, "Delhi Technological Institute " + uid, "Northern Region");
        institution = institutionRepository.save(institution);

        when(minioStorageService.uploadDocument(anyLong(), anyString(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(new MinioStorageService.StoredObjectResult(
                        "applications/1/DOC_LAND/v1_test.pdf",
                        "sage-application-documents",
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        1024L,
                        "application/pdf"
                ));

        when(minioStorageService.downloadDocument(anyString()))
                .thenReturn(new ByteArrayInputStream("mock pdf content".getBytes()));

        doNothing().when(minioStorageService).deleteDocument(anyString());
    }

    @Test
    @WithMockUser(username = "rep_user", roles = {"INSTITUTION_REPRESENTATIVE"})
    @DisplayName("Catalogue lists all 12 verified APH application categories")
    void shouldReturnAll12ApplicationTypes() throws Exception {
        mockMvc.perform(get("/api/v1/application-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[?(@.code == 'NEW_INSTITUTION')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'EXTENSION_OF_APPROVAL')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'NEW_PROGRAMME')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'ADD_COURSE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'CHANGE_INTAKE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'CLOSURE_INSTITUTION')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'INSTITUTION_CHANGE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'MERGER_INSTITUTIONS')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'RESTORATION_INTAKE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'SUPERNUMERARY_SEATS')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'COLLABORATIVE_ODL_ONLINE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'REGULATORY_CHANGE')]").exists());
    }

    @Test
    @WithMockUser(username = "rep_user", roles = {"INSTITUTION_REPRESENTATIVE"})
    @DisplayName("Fetch detailed requirements and sections for specific APH category")
    void shouldReturnApplicationTypeDetail() throws Exception {
        mockMvc.perform(get("/api/v1/application-types/NEW_INSTITUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("NEW_INSTITUTION")))
                .andExpect(jsonPath("$.aphClause", is("APH 2024-27 Chapter I")))
                .andExpect(jsonPath("$.sections", hasSize(3)))
                .andExpect(jsonPath("$.documentRequirements", hasSize(8)));
    }

    @Test
    @WithMockUser(username = "rep_user", roles = {"INSTITUTION_REPRESENTATIVE"})
    @DisplayName("Draft application persistence, document upload, and readiness check")
    void shouldManageDraftAndDocuments() throws Exception {
        // 1. Create initial draft application
        String appRef = "SAGE-NEW-" + UUID.randomUUID().toString().substring(0, 6);
        CreateApplicationRequest createReq = new CreateApplicationRequest(
                institution.getId(),
                appRef,
                "NEW_INSTITUTION",
                "2025-26"
        );

        String createJson = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationReference", is(appRef)))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(createJson).get("id").asLong();

        // 2. Save partial draft form data
        Map<String, Object> formFields = new HashMap<>();
        formFields.put("institutionName", institution.getName());
        formFields.put("landAreaAcres", 7.5);
        formFields.put("antiRaggingDeclared", true);

        SaveDraftRequest draftReq = new SaveDraftRequest(formFields, "Draft update testing");

        mockMvc.perform(put("/api/v1/applications/" + appId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formData.institutionName", is(institution.getName())))
                .andExpect(jsonPath("$.formData.landAreaAcres", is(7.5)));

        // 3. Upload a required document
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "land_deed.pdf",
                "application/pdf",
                "PDF file bytes sample".getBytes()
        );

        String uploadJson = mockMvc.perform(multipart("/api/v1/applications/" + appId + "/documents")
                        .file(file)
                        .param("requirementCode", "DOC_LAND_DOCUMENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementCode", is("DOC_LAND_DOCUMENTS")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.status", is("UPLOADED")))
                .andReturn().getResponse().getContentAsString();

        Long docId = objectMapper.readTree(uploadJson).get("id").asLong();

        // 4. Download document
        mockMvc.perform(get("/api/v1/applications/" + appId + "/documents/" + docId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"land_deed.pdf\""));

        // 5. Evaluate checklist (should still be incomplete because remaining docs and fields are missing)
        mockMvc.perform(get("/api/v1/applications/" + appId + "/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittable", is(false)))
                .andExpect(jsonPath("$.blockingDeficiencies").isArray());

        // 6. Delete document
        mockMvc.perform(delete("/api/v1/applications/" + appId + "/documents/" + docId))
                .andExpect(status().isNoContent());

        assertThat(documentRepository.findById(docId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "rep_user", roles = {"INSTITUTION_REPRESENTATIVE"})
    @DisplayName("Authoritative validation gate blocks submission when mandatory requirements are missing")
    void shouldBlockIncompleteSubmission() throws Exception {
        // Create draft application
        String appRef = "SAGE-EOA-" + UUID.randomUUID().toString().substring(0, 6);
        CreateApplicationRequest createReq = new CreateApplicationRequest(
                institution.getId(),
                appRef,
                "EXTENSION_OF_APPROVAL",
                "2025-26"
        );

        String createJson = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(createJson).get("id").asLong();

        // Attempt submission without completing form and documents
        mockMvc.perform(post("/api/v1/applications/" + appId + "/submit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());

        // Verify application status remained DRAFT
        Application app = applicationRepository.findById(appId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo("DRAFT");
        assertThat(app.getSubmittedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "rep_user", roles = {"INSTITUTION_REPRESENTATIVE"})
    @DisplayName("Complete submission generates snapshot, locks application, and hands off to Scrutiny queue")
    void shouldSuccessfullySubmitWhenAllRequirementsSatisfied() throws Exception {
        // 1. Create draft application
        String appRef = "SAGE-EOA-" + UUID.randomUUID().toString().substring(0, 6);
        CreateApplicationRequest createReq = new CreateApplicationRequest(
                institution.getId(),
                appRef,
                "EXTENSION_OF_APPROVAL",
                "2025-26"
        );

        String createJson = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(createJson).get("id").asLong();

        // 2. Populate all required form fields
        Map<String, Object> formFields = new HashMap<>();
        formFields.put("institutionName", institution.getName());
        formFields.put("trustName", "National Education Foundation");
        formFields.put("trustRegistrationNumber", "REG/2010/8899");
        formFields.put("panNumber", "AAATN9988C");
        formFields.put("headOfInstitutionName", "Prof. S. K. Gupta");
        formFields.put("contactEmail", "director@dti.edu.in");
        formFields.put("contactPhone", "9812345678");
        formFields.put("campusCategory", "Urban");
        formFields.put("landAreaAcres", 10.0);
        formFields.put("totalBuiltUpAreaSqM", 15000);
        formFields.put("instructionalAreaSqM", 9000);
        formFields.put("administrativeAreaSqM", 2500);
        formFields.put("antiRaggingDeclared", true);
        formFields.put("grievanceRedressalDeclared", true);
        formFields.put("internalComplaintsDeclared", true);
        formFields.put("barrierFreeDeclared", true);
        formFields.put("fireSafetyDeclared", true);

        mockMvc.perform(put("/api/v1/applications/" + appId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveDraftRequest(formFields, null))))
                .andExpect(status().isOk());

        // 3. Upload all mandatory documents for EXTENSION_OF_APPROVAL
        String[] requiredDocs = {
                "DOC_PREVIOUS_EOA",
                "DOC_FACULTY_ROLL",
                "DOC_FIRE_SAFETY",
                "DOC_OCCUPANCY_CERT",
                "DOC_AUDITED_ACCOUNTS"
        };

        for (String code : requiredDocs) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    code + ".pdf",
                    "application/pdf",
                    ("Bytes for " + code).getBytes()
            );

            mockMvc.perform(multipart("/api/v1/applications/" + appId + "/documents")
                            .file(file)
                            .param("requirementCode", code))
                    .andExpect(status().isOk());
        }

        // 4. Checklist should now indicate submittable = true
        mockMvc.perform(get("/api/v1/applications/" + appId + "/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittable", is(true)))
                .andExpect(jsonPath("$.completionPercentage", is(100)));

        // 5. Submit application
        mockMvc.perform(post("/api/v1/applications/" + appId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.applicationReference", is(appRef)))
                .andExpect(jsonPath("$.status", is("In progress")))
                .andExpect(jsonPath("$.currentStage", is("Scrutiny")))
                .andExpect(jsonPath("$.ownerName", is("B. Singh (Scrutiny Officer)")))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        // 6. Verify state and locked snapshot in database
        Application submittedApp = applicationRepository.findById(appId).orElseThrow();
        assertThat(submittedApp.getStatus()).isEqualTo("In progress");
        assertThat(submittedApp.getCurrentStage()).isEqualTo("Scrutiny");
        assertThat(submittedApp.getOwnerName()).isEqualTo("B. Singh (Scrutiny Officer)");
        assertThat(submittedApp.getSubmittedAt()).isNotNull();
        assertThat(submittedApp.getSubmittedSnapshotJson()).contains(appRef);
        assertThat(submittedApp.getSubmittedSnapshotJson()).contains("DOC_PREVIOUS_EOA");

        // 7. Modification after submission must be rejected
        mockMvc.perform(put("/api/v1/applications/" + appId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveDraftRequest(formFields, "tamper attempt"))))
                .andExpect(status().isForbidden());

        // 8. Subsequent submission must be rejected
        mockMvc.perform(post("/api/v1/applications/" + appId + "/submit"))
                .andExpect(status().isForbidden());
    }
}
