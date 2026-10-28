package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.institution.InstitutionRepository;
import in.gov.aicte.platform.scrutiny.dto.IssueDeficienciesRequest;
import in.gov.aicte.platform.scrutiny.dto.ReviewFindingRequest;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyAssessmentRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDeficiencyRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDocumentExtractionRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyFindingRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyRescrutinyComparisonRepository;
import in.gov.aicte.platform.scrutiny.service.ReScrutinyService;
import in.gov.aicte.platform.scrutiny.service.ScrutinyService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutomatedScrutinyWorkflowIntegrationTest {

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

    @Autowired
    private ScrutinyAssessmentRepository assessmentRepository;

    @Autowired
    private ScrutinyFindingRepository findingRepository;

    @Autowired
    private ScrutinyDocumentExtractionRepository extractionRepository;

    @Autowired
    private ScrutinyDeficiencyRepository deficiencyRepository;

    @Autowired
    private ScrutinyRescrutinyComparisonRepository comparisonRepository;

    @Autowired
    private ScrutinyService scrutinyService;

    @Autowired
    private ReScrutinyService reScrutinyService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    private Institution testInstitution;
    private Application testApplication;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        testInstitution = new Institution("INST-" + uid, "Apex Institute of Science and Technology " + uid, "North");
        testInstitution = institutionRepository.save(testInstitution);

        testApplication = new Application(
                testInstitution,
                "APP-2026-" + uid,
                "EXTENSION_OF_APPROVAL",
                "2026-2027",
                "Submitted"
        );
        testApplication.setCurrentStage("Scrutiny");
        testApplication = applicationRepository.save(testApplication);
    }

    private byte[] createPdf(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return "hash_" + data.length;
        }
    }

    @Test
    @DisplayName("Test A — Correct Document: Valid Fire NOC PDF extracts successfully and evaluates validity")
    void testCorrectDocumentExtractsAndEvaluates() throws Exception {
        String certText = "Government Fire and Rescue Services Department No Objection Certificate for Fire Safety. Certificate No: NOC-2024-9988. Issued to Apex Institute of Science and Technology. Valid Up To: 31/12/2026. Complies with Fire Prevention and Safety Act.";
        byte[] pdfBytes = createPdf(certText);
        String fileHash = calculateSha256(pdfBytes);

        ApplicationDocument fireDoc = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_cert.pdf", "fire_noc_valid.pdf", "s3://minio/fire_valid.pdf",
                (long) pdfBytes.length, "application/pdf", fileHash, 1, "UPLOADED", "SYSTEM"
        );
        fireDoc = documentRepository.save(fireDoc);

        when(minioStorageService.downloadDocument("s3://minio/fire_valid.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));

        // Trigger scrutiny
        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        // Verify extraction record was persisted in database
        List<ScrutinyDocumentExtraction> extractions = extractionRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        assertThat(extractions).isNotEmpty();
        ScrutinyDocumentExtraction ext = extractions.stream()
                .filter(e -> "DOC_FIRE_SAFETY".equals(e.getRequirementCode()))
                .findFirst()
                .orElse(null);

        assertThat(ext).isNotNull();
        assertThat(ext.getExtractionMethod()).isEqualTo("PDFBOX_SEARCHABLE_TEXT");
        assertThat(ext.getOcrStatus()).isEqualTo("SUCCESS");
        assertThat(ext.getExtractedTextSnippet()).contains("Fire and Rescue Services");

        // Verify findings: format valid passes, no content mismatch
        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        boolean hasContentMismatch = findings.stream()
                .anyMatch(f -> "RULE_DOC_CONTENT_MISMATCH_DOC_FIRE_SAFETY".equals(f.getRuleCode()));
        assertThat(hasContentMismatch).isFalse();
    }

    @Test
    @DisplayName("Test B — Wrong Document: DAA Lab PDF uploaded as Fire Safety Certificate must produce POTENTIAL_DEFICIENCY and NO content PASS")
    void testWrongDocumentRejectsUnrelatedLabFile() throws Exception {
        String labText = "Design and Analysis of Algorithms Lab Manual. Department of Computer Science. Experiment 1: Quick Sort and Merge Sort. Time complexity: O(n log n). Submitted by Student Roll 123.";
        byte[] labPdfBytes = createPdf(labText);
        String labHash = calculateSha256(labPdfBytes);

        ApplicationDocument fireDoc = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "daa_lab.pdf", "DAA_Lab_File_final.pdf", "s3://minio/daa_lab.pdf",
                (long) labPdfBytes.length, "application/pdf", labHash, 1, "UPLOADED", "SYSTEM"
        );
        fireDoc = documentRepository.save(fireDoc);

        when(minioStorageService.downloadDocument("s3://minio/daa_lab.pdf"))
                .thenReturn(new ByteArrayInputStream(labPdfBytes));

        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        // Verify extraction recorded
        List<ScrutinyDocumentExtraction> extractions = extractionRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        assertThat(extractions).isNotEmpty();
        assertThat(extractions.get(0).getExtractedTextSnippet()).contains("Design and Analysis of Algorithms");

        // Verify findings: must have POTENTIAL_DEFICIENCY for content mismatch
        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        ScrutinyFinding mismatchFinding = findings.stream()
                .filter(f -> "RULE_DOC_CONTENT_MISMATCH_DOC_FIRE_SAFETY".equals(f.getRuleCode()))
                .findFirst()
                .orElse(null);

        assertThat(mismatchFinding).isNotNull();
        assertThat(mismatchFinding.getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(mismatchFinding.getSeverity()).isEqualTo("BLOCKING");
        assertThat(mismatchFinding.getExplanation()).contains("academic lab manual");

        // Substantive statutory check must NOT pass
        boolean passedStatutory = findings.stream()
                .filter(f -> "CERTIFICATE_VALIDITY".equals(f.getFindingType()))
                .anyMatch(f -> "PASS".equals(f.getOutcome()));
        assertThat(passedStatutory).isFalse();
    }

    @Test
    @DisplayName("Test C — Duplicate File Upload: Same file uploaded across multiple distinct categories is flagged")
    void testDuplicateFileUploadFlagged() throws Exception {
        String text = "Generic Document Content Page 1";
        byte[] pdfBytes = createPdf(text);
        String identicalHash = calculateSha256(pdfBytes);

        ApplicationDocument doc1 = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "doc1.pdf", "DAA_Lab_File_final.pdf", "s3://minio/dup1.pdf",
                (long) pdfBytes.length, "application/pdf", identicalHash, 1, "UPLOADED", "SYSTEM"
        );
        ApplicationDocument doc2 = new ApplicationDocument(
                testApplication, "DOC_BUILDING_PLAN", "Building Plan Approval",
                "doc2.pdf", "DAA_Lab_File_final.pdf", "s3://minio/dup2.pdf",
                (long) pdfBytes.length, "application/pdf", identicalHash, 1, "UPLOADED", "SYSTEM"
        );
        documentRepository.saveAll(List.of(doc1, doc2));

        when(minioStorageService.downloadDocument(anyString()))
                .thenReturn(new ByteArrayInputStream(pdfBytes));

        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        List<ScrutinyFinding> duplicateFindings = findings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_"))
                .toList();

        assertThat(duplicateFindings).hasSize(2);
        for (ScrutinyFinding df : duplicateFindings) {
            assertThat(df.getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
            assertThat(df.getSeverity()).isEqualTo("BLOCKING");
            assertThat(df.getExplanation()).contains("identical file was uploaded under multiple distinct document categories");
        }
    }

    @Test
    @DisplayName("Test D — Missing Document: Mandatory un-uploaded documents produce BLOCKING deficiency findings")
    void testMissingMandatoryDocumentProducesDeficiency() {
        // testApplication has 0 documents uploaded
        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        List<ScrutinyFinding> missingFindings = findings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DOC_MANDATORY_MISSING_"))
                .toList();

        assertThat(missingFindings).isNotEmpty();
        for (ScrutinyFinding mf : missingFindings) {
            assertThat(mf.getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
            assertThat(mf.getSeverity()).isEqualTo("BLOCKING");
        }
    }

    @Test
    @DisplayName("Test F — Corrupted or Empty Document: Handled safely with FAILED status and no false PASS")
    void testCorruptedDocumentHandledGracefully() {
        byte[] corruptedBytes = new byte[]{0x00, 0x01, 0x02, 0x03}; // Not a valid PDF
        ApplicationDocument corruptDoc = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "corrupt.pdf", "corrupt.pdf", "s3://minio/corrupt.pdf",
                4L, "application/pdf", "corrupt_hash", 1, "UPLOADED", "SYSTEM"
        );
        corruptDoc = documentRepository.save(corruptDoc);

        when(minioStorageService.downloadDocument("s3://minio/corrupt.pdf"))
                .thenReturn(new ByteArrayInputStream(corruptedBytes));

        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        List<ScrutinyDocumentExtraction> extractions = extractionRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        assertThat(extractions).isNotEmpty();
        assertThat(extractions.get(0).getOcrStatus()).isEqualTo("FAILED");

        // Must not pass
        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        boolean hasPass = findings.stream()
                .filter(f -> "CERTIFICATE_VALIDITY".equals(f.getFindingType()))
                .anyMatch(f -> "PASS".equals(f.getOutcome()));
        assertThat(hasPass).isFalse();
    }

    @Test
    @DisplayName("Test G — Formal Deficiency Notice Workflow: Officer reviews, confirms, and issues notice")
    @WithMockUser(username = "officer", roles = {"SCRUTINY_OFFICER"})
    void testDeficiencyNoticeWorkflow() throws Exception {
        // 1. Run scrutiny to create preliminary findings
        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        ScrutinyFinding targetFinding = findings.stream()
                .filter(f -> "POTENTIAL_DEFICIENCY".equals(f.getOutcome()))
                .findFirst()
                .orElseThrow();

        // 2. Officer reviews and confirms deficiency
        ReviewFindingRequest reviewRequest = new ReviewFindingRequest(
                "CONFIRMED_DEFICIENCY",
                "Verified missing statutory documentation per APH 2024-27",
                null,
                null,
                true
        );

        mockMvc.perform(post("/api/v1/applications/{appId}/scrutiny/findings/{findingId}/review",
                        testApplication.getId(), targetFinding.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerDecision", is("CONFIRMED_DEFICIENCY")));

        // 3. Officer issues formal deficiency notice
        IssueDeficienciesRequest issueRequest = new IssueDeficienciesRequest(
                List.of(targetFinding.getId()),
                "Notice of Statutory Deficiencies for AICTE Approval",
                "Please rectify all cited statutory non-compliances within 15 days.",
                LocalDate.now().plusDays(15),
                true,
                true
        );

        mockMvc.perform(post("/api/v1/applications/{appId}/deficiencies/issue", testApplication.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("ISSUED")));

        // Verify deficiency in DB
        List<ScrutinyDeficiency> deficiencies = deficiencyRepository.findByApplicationIdOrderByCreatedAtAsc(testApplication.getId());
        assertThat(deficiencies).hasSize(1);
        assertThat(deficiencies.get(0).getStatus()).isEqualTo("ISSUED");
        assertThat(deficiencies.get(0).getRequirementCode()).isEqualTo(targetFinding.getRequirementCode());
    }

    @Test
    @DisplayName("Test H — Re-Scrutiny & Diffs: Uploading corrected document generates diff comparisons")
    void testReScrutinyGeneratesDiffComparisons() throws Exception {
        // Setup v1 document with wrong content
        String wrongText = "Design and Analysis of Algorithms Lab Manual Experiment 1";
        byte[] v1Bytes = createPdf(wrongText);
        ApplicationDocument docV1 = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_v1.pdf", "fire_v1.pdf", "s3://minio/fire_v1.pdf",
                (long) v1Bytes.length, "application/pdf", calculateSha256(v1Bytes), 1, "UPLOADED", "SYSTEM"
        );
        docV1 = documentRepository.save(docV1);

        when(minioStorageService.downloadDocument("s3://minio/fire_v1.pdf"))
                .thenReturn(new ByteArrayInputStream(v1Bytes));

        // Initial scrutiny cycle 1
        scrutinyService.runAutomatedScrutiny(testApplication.getId(), "USR-SO-01");

        // Confirm finding and issue deficiency
        List<ScrutinyFinding> findings = findingRepository.findByApplicationIdOrderByCreatedAtDesc(testApplication.getId());
        ScrutinyFinding fireFinding = findings.stream()
                .filter(f -> "DOC_FIRE_SAFETY".equals(f.getRequirementCode()))
                .findFirst()
                .orElseThrow();

        List<in.gov.aicte.platform.scrutiny.dto.ScrutinyDeficiencyResponse> issued = scrutinyService.issueFormalDeficiencies(
                testApplication.getId(),
                new IssueDeficienciesRequest(
                        List.of(fireFinding.getId()),
                        "Formal Notice", "Fix Fire NOC", LocalDate.now().plusDays(10), true, true
                ),
                "USR-SO-01"
        );
        Long deficiencyId = issued.get(0).id();

        // Now setup v2 corrected document
        String correctedText = "Government Fire and Rescue Services Department No Objection Certificate for Fire Safety. Certificate No: NOC-2024-9988. Issued to Apex Institute of Science and Technology. Valid Up To: 31/12/2026.";
        byte[] v2Bytes = createPdf(correctedText);

        when(minioStorageService.downloadDocument("s3://minio/fire_v2.pdf"))
                .thenReturn(new ByteArrayInputStream(v2Bytes));

        when(minioStorageService.uploadDocument(anyLong(), anyString(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(new MinioStorageService.StoredObjectResult(
                        "s3://minio/fire_v2.pdf", "sage-application-documents",
                        calculateSha256(v2Bytes), (long) v2Bytes.length, "application/pdf"
                ));

        MockMultipartFile correctedFile = new MockMultipartFile(
                "file", "fire_noc_renewed.pdf", "application/pdf", v2Bytes
        );

        // Upload corrected document (creates v2)
        scrutinyService.uploadCorrectedDocument(
                testApplication.getId(), deficiencyId, correctedFile, "USR-INST-01"
        );

        // Mark deficiency as RESPONSE_SUBMITTED so re-scrutiny processes it
        ScrutinyDeficiency def = deficiencyRepository.findById(deficiencyId).orElseThrow();
        def.setStatus("RESPONSE_SUBMITTED");
        def.setInstitutionExplanation("Uploaded renewed Fire NOC certificate.");
        deficiencyRepository.save(def);

        // Run re-scrutiny (cycle 2)
        ScrutinyAssessment reAssessment = reScrutinyService.runAutomatedRescrutiny(testApplication, "USR-SO-01");

        // Verify comparison records were generated
        List<ScrutinyRescrutinyComparison> comparisons = comparisonRepository.findByAssessmentIdOrderByIdAsc(reAssessment.getId());
        assertThat(comparisons).isNotEmpty();

        ScrutinyRescrutinyComparison fireComparison = comparisons.stream()
                .filter(c -> "DOC_FIRE_SAFETY".equals(c.getDeficiency().getRequirementCode()))
                .findFirst()
                .orElse(null);

        assertThat(fireComparison).isNotNull();
        assertThat(fireComparison.getOriginalDocumentVersion()).isEqualTo(1);
        assertThat(fireComparison.getCorrectedDocumentVersion()).isEqualTo(2);
        assertThat(fireComparison.getComparisonStatus()).isEqualTo("RECTIFIED_PENDING_CONFIRMATION");
    }

    @Test
    @DisplayName("Test I — Real API Integration: GET extractions and report returns persisted records")
    @WithMockUser(username = "officer", roles = {"SCRUTINY_OFFICER"})
    void testScrutinyApiEndpointsReturnPersistedData() throws Exception {
        String certText = "Government Fire and Rescue Services Department NOC for Fire Safety. Valid Up To: 31/12/2026.";
        byte[] pdfBytes = createPdf(certText);

        ApplicationDocument fireDoc = new ApplicationDocument(
                testApplication, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire.pdf", "fire_noc.pdf", "s3://minio/fire.pdf",
                (long) pdfBytes.length, "application/pdf", calculateSha256(pdfBytes), 1, "UPLOADED", "SYSTEM"
        );
        documentRepository.save(fireDoc);

        when(minioStorageService.downloadDocument("s3://minio/fire.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));

        // Start scrutiny via real POST API
        mockMvc.perform(post("/api/v1/applications/{appId}/scrutiny/run", testApplication.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId", is(testApplication.getId().intValue())));

        // Test GET report includes extractions
        mockMvc.perform(get("/api/v1/applications/{appId}/scrutiny/report", testApplication.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId", is(testApplication.getId().intValue())))
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.extractions").isArray());

        // Test GET dedicated extractions endpoint
        mockMvc.perform(get("/api/v1/applications/{appId}/scrutiny/extractions", testApplication.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].documentName", is("Fire Safety Certificate")))
                .andExpect(jsonPath("$[0].extractionMethod", is("PDFBOX_SEARCHABLE_TEXT")))
                .andExpect(jsonPath("$[0].ocrStatus", is("SUCCESS")));
    }
}
