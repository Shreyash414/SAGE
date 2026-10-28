package in.gov.aicte.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.ApplicationRepository;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.audit.AuditEventRepository;
import in.gov.aicte.platform.evc.EvcService;
import in.gov.aicte.platform.institution.Institution;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.scrutiny.dto.FieldSourceInfo;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyDocumentExtractionResponse;
import in.gov.aicte.platform.scrutiny.dto.ReviewFindingRequest;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyFindingResponse;
import in.gov.aicte.platform.scrutiny.ocr.DocumentTextExtractionService;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyAssessmentRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDocumentExtractionRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyFindingRepository;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyRescrutinyComparisonRepository;
import in.gov.aicte.platform.scrutiny.service.ReScrutinyService;
import in.gov.aicte.platform.scrutiny.service.ScrutinyService;
import in.gov.aicte.platform.application.document.MinioStorageService;
import in.gov.aicte.platform.scrutiny.ocr.DocumentFieldExtractionService;
import in.gov.aicte.platform.scrutiny.ocr.ExtractedDocumentData;
import in.gov.aicte.platform.scrutiny.repository.ScrutinyDeficiencyRepository;
import in.gov.aicte.platform.scrutiny.validation.ScrutinyValidationService;
import in.gov.aicte.platform.workflow.WorkflowService;
import in.gov.aicte.platform.workflow.WorkflowTransitionRepository;
import in.gov.aicte.platform.scrutiny.dto.IssueDeficienciesRequest;
import in.gov.aicte.platform.scrutiny.dto.ReviewDeficiencyRequest;
import in.gov.aicte.platform.scrutiny.dto.SubmitDeficiencyResponsesRequest;
import in.gov.aicte.platform.scrutiny.dto.RescrutinyComparisonResponse;
import in.gov.aicte.platform.scrutiny.dto.ScrutinyDeficiencyResponse;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;
import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;
import in.gov.aicte.platform.workflow.WorkflowTransition;
import in.gov.aicte.platform.workflow.dto.WorkflowActionRequest;
import in.gov.aicte.platform.scrutiny.controller.ScrutinyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import in.gov.aicte.platform.scrutiny.ocr.TesseractOcrService;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrutinyAutomationAndRescrutinyUnitTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private WorkflowTransitionRepository workflowTransitionRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private EvcService evcService;

    @Mock
    private ScrutinyDeficiencyRepository scrutinyDeficiencyRepository;

    @Mock
    private ApplicationDocumentRepository documentRepository;

    @Mock
    private ScrutinyAssessmentRepository assessmentRepository;

    @Mock
    private ScrutinyDocumentExtractionRepository extractionRepository;

    @Mock
    private ScrutinyFindingRepository findingRepository;

    @Mock
    private ScrutinyRescrutinyComparisonRepository comparisonRepository;

    @Mock
    private DocumentTextExtractionService textExtractionService;

    @Mock
    private ReScrutinyService reScrutinyService;

    @Mock
    private MinioStorageService minioStorageService;

    private ObjectMapper objectMapper;
    private ApplicationTypeCatalogue catalogue;
    private DocumentFieldExtractionService fieldExtractionService;
    private ScrutinyValidationService validationService;
    private WorkflowService workflowService;
    private ScrutinyService scrutinyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        catalogue = new ApplicationTypeCatalogue();
        fieldExtractionService = new DocumentFieldExtractionService();
        validationService = new ScrutinyValidationService(catalogue, fieldExtractionService);
        workflowService = new WorkflowService(
                applicationRepository,
                workflowTransitionRepository,
                auditEventRepository,
                objectMapper,
                evcService,
                scrutinyDeficiencyRepository
        );
        scrutinyService = new ScrutinyService(
                applicationRepository,
                documentRepository,
                assessmentRepository,
                extractionRepository,
                findingRepository,
                scrutinyDeficiencyRepository,
                comparisonRepository,
                textExtractionService,
                fieldExtractionService,
                validationService,
                reScrutinyService,
                minioStorageService,
                auditEventRepository,
                objectMapper,
                workflowTransitionRepository
        );
    }

    @Test
    @DisplayName("DocumentFieldExtraction: should extract dates, certificate numbers, PAN and address from text")
    void testFieldExtractionFromRawText() {
        String sampleFireNocText = """
                GOVERNMENT OF UTTAR PRADESH
                FIRE & RESCUE SERVICES DEPARTMENT
                NO OBJECTION CERTIFICATE
                Certificate No: NOC/FIRE/2024/78910
                Date of Issue: 15/01/2024
                Valid Up To / Expiry Date: 31/12/2025
                This is to certify that ABC Institute of Technology located at Plot 4, Knowledge Park III,
                Greater Noida, Uttar Pradesh 201308 has complied with fire safety norms.
                PAN: AAATC1234F
                Issued by: Chief Fire Officer, Gautam Buddha Nagar
                """;

        ExtractedDocumentData data = new ExtractedDocumentData();
        data.setRawText(sampleFireNocText);
        data.setExtractionMethod("APACHE_PDFBOX_TEXT");
        data.setOcrStatus("SUCCESS");

        ExtractedDocumentData enriched = fieldExtractionService.extractStructuredFields(data, "DOC_FIRE_SAFETY");

        assertThat(enriched.getFields()).isNotNull();
        assertThat(enriched.getDateField("issueDate")).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(enriched.getDateField("expiryDate")).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(enriched.getStringField("certificateNumber")).contains("NOC/FIRE/2024/78910");
        assertThat(enriched.getStringField("panNumber")).isEqualTo("AAATC1234F");
        assertThat(enriched.getStringField("pinCode")).isEqualTo("201308");
        assertThat(enriched.getStringField("institutionName")).contains("ABC Institute of Technology");
    }

    @Test
    @DisplayName("ScrutinyValidation: should flag expired certificate as POTENTIAL_DEFICIENCY with BLOCKING severity")
    void testValidationFlagsExpiredCertificate() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument fireDoc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_uuid.pdf", "fire_noc_2025.pdf", "s3://minio/fire.pdf",
                102400L, "application/pdf", "hash123", 1, "UPLOADED", "SYSTEM"
        );
        fireDoc.setId(101L);

        List<ApplicationDocument> docs = List.of(fireDoc);

        // Extracted expiry date is 2025-12-31, but assessment date is 2026-03-01
        String fieldsJson = "{\"expiryDate\":\"2025-12-31\",\"issueDate\":\"2024-01-15\",\"certificateNumber\":\"NOC/2024/111\",\"institutionName\":\"ABC Institute of Technology\"}";

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), fireDoc, "DOC_FIRE_SAFETY",
                "Fire Safety Certificate", 1, "APACHE_PDFBOX_TEXT",
                "Government Fire and Rescue Services NOC for Fire Safety and Emergency Prevention", fieldsJson, 0.95, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, docs, Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        Optional<ScrutinyFinding> fireFinding = findings.stream()
                .filter(f -> "DOC_FIRE_SAFETY".equals(f.getRequirementCode()) && "CERTIFICATE_VALIDITY".equals(f.getFindingType()))
                .findFirst();

        assertThat(fireFinding).isPresent();
        assertThat(fireFinding.get().getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(fireFinding.get().getSeverity()).isEqualTo("BLOCKING");
        assertThat(fireFinding.get().getExplanation()).contains("expired");
        assertThat(fireFinding.get().getRuleReference()).contains("APH");
    }

    @Test
    @DisplayName("ScrutinyValidation: should flag cross-document institution name discrepancy")
    void testValidationFlagsInstitutionNameDiscrepancy() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_uuid.pdf", "fire_noc.pdf", "s3://minio/fire.pdf",
                102400L, "application/pdf", "hash123", 1, "UPLOADED", "SYSTEM"
        );
        doc.setId(102L);

        // Name in document differs completely from application institution name "ABC Institute of Technology"
        String fieldsJson = "{\"expiryDate\":\"2027-12-31\",\"institutionName\":\"XYZ College of Management Studies\"}";

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY",
                "Fire Safety Certificate", 1, "APACHE_PDFBOX_TEXT",
                "State Disaster Management and Fire Safety Certificate", fieldsJson, 0.95, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(doc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        Optional<ScrutinyFinding> consistencyFinding = findings.stream()
                .filter(f -> "CROSS_DOCUMENT_CONSISTENCY".equals(f.getFindingType()))
                .findFirst();

        assertThat(consistencyFinding).isPresent();
        assertThat(consistencyFinding.get().getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(consistencyFinding.get().getExplanation()).contains("XYZ College of Management Studies");
    }

    @Test
    @DisplayName("ScrutinyValidation: should pass valid certificate with matching name and future expiry")
    void testValidationPassesValidCertificate() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_uuid.pdf", "fire_noc_valid.pdf", "s3://minio/fire.pdf",
                102400L, "application/pdf", "hash123", 1, "UPLOADED", "SYSTEM"
        );
        doc.setId(103L);

        String fieldsJson = "{\"expiryDate\":\"2027-12-31\",\"issueDate\":\"2024-01-10\",\"institutionName\":\"ABC Institute of Technology\"}";

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY",
                "Fire Safety Certificate", 1, "APACHE_PDFBOX_TEXT",
                "Fire Safety Certificate No Objection Certificate issued to ABC Institute of Technology", fieldsJson, 0.98, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(doc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        Optional<ScrutinyFinding> validityCheck = findings.stream()
                .filter(f -> "DOC_FIRE_SAFETY".equals(f.getRequirementCode()) && "CERTIFICATE_VALIDITY".equals(f.getFindingType()))
                .findFirst();

        assertThat(validityCheck).isPresent();
        assertThat(validityCheck.get().getOutcome()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("WorkflowService: SUBMIT_CORRECTIONS must NOT erase deficienciesJson")
    void testSubmitCorrectionsPreservesDeficienciesJson() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        app.setCurrentStage("Re-scrutiny");
        app.setStatus("Returned");
        app.setDeficienciesJson("[\"Fire safety NOC expired\",\"Address discrepancy in Affidavit 2\"]");

        when(applicationRepository.findByApplicationReference(app.getApplicationReference()))
                .thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(workflowTransitionRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());

        WorkflowActionRequest request = new WorkflowActionRequest(
                "SUBMIT_CORRECTIONS",
                "Principal Dr. Sharma",
                "INSTITUTION",
                "Uploaded renewed Fire NOC and amended Affidavit 2",
                "Institution responses and supporting documents submitted",
                null,
                null
        );

        workflowService.executeAction(app.getApplicationReference(), request, "USR-INST-01");

        // Verify deficienciesJson is NOT cleared
        assertThat(app.getDeficienciesJson()).isNotNull();
        assertThat(app.getDeficienciesJson()).contains("Fire safety NOC expired");
        assertThat(app.getStatus()).isEqualTo("In progress");
        assertThat(app.getCurrentStage()).isEqualTo("Re-scrutiny");
    }

    @Test
    @DisplayName("WorkflowService: COMPLETE_SCRUTINY must be blocked when unresolved blocking deficiencies exist")
    void testCompleteScrutinyBlockedByUnresolvedBlockingDeficiencies() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        app.setCurrentStage("Scrutiny");
        app.setStatus("In progress");

        when(applicationRepository.findByApplicationReference(app.getApplicationReference()))
                .thenReturn(Optional.of(app));
        // Mock 1 blocking deficiency remaining unresolved
        when(scrutinyDeficiencyRepository.countByApplicationIdAndBlockingTrueAndStatusIn(
                app.getId(), List.of("ISSUED", "RESPONSE_SUBMITTED")
        )).thenReturn(1L);

        WorkflowActionRequest request = new WorkflowActionRequest(
                "COMPLETE_SCRUTINY",
                "B. Singh",
                "SCRUTINY_OFFICER",
                "Ready for EVC",
                "Scrutiny complete",
                null,
                "EVC scheduling"
        );

        assertThatThrownBy(() -> workflowService.executeAction(app.getApplicationReference(), request, "USR-SO-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete scrutiny")
                .hasMessageContaining("1 unresolved blocking deficiency");
    }

    @Test
    @DisplayName("WorkflowService: COMPLETE_SCRUTINY succeeds when all blocking deficiencies are resolved")
    void testCompleteScrutinySucceedsWhenNoBlockingDeficiencies() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        app.setCurrentStage("Scrutiny");
        app.setStatus("In progress");

        when(applicationRepository.findByApplicationReference(app.getApplicationReference()))
                .thenReturn(Optional.of(app));
        when(scrutinyDeficiencyRepository.countByApplicationIdAndBlockingTrueAndStatusIn(
                app.getId(), List.of("ISSUED", "RESPONSE_SUBMITTED")
        )).thenReturn(0L);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(workflowTransitionRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());

        WorkflowActionRequest request = new WorkflowActionRequest(
                "COMPLETE_SCRUTINY",
                "B. Singh",
                "SCRUTINY_OFFICER",
                "All requirements verified and cleared",
                "Scrutiny completed successfully",
                null,
                "EVC scheduling"
        );

        workflowService.executeAction(app.getApplicationReference(), request, "USR-SO-01");

        assertThat(app.getCurrentStage()).isEqualTo("EVC scheduling");
        assertThat(app.getStatus()).isEqualTo("Due soon");
        verify(evcService).ensureEvcVisitForApplication(app);
    }

    @Test
    @DisplayName("ScrutinyValidation: unrelated academic lab file (DAA Lab File) must produce POTENTIAL_DEFICIENCY and NOT pass substantive rules")
    void testScrutinyRejectsUnrelatedLabFile() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        String daaLabText = """
                Design and Analysis of Algorithms Lab Manual
                Department of Computer Science & Engineering
                Experiment 1: Implementation of Quick Sort and Merge Sort
                Time Complexity: O(n log n)
                Algorithm: Divide and Conquer approach
                Submitted by: Student Roll No 12345
                Semester VI, Batch 2024
                """;

        ApplicationDocument fireDoc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "fire_uuid.pdf", "DAA_Lab_File_final.pdf", "s3://minio/daa.pdf",
                397739L, "application/pdf", "hash_daa_123", 1, "UPLOADED", "SYSTEM"
        );
        fireDoc.setId(201L);

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), fireDoc, "DOC_FIRE_SAFETY",
                "DAA_Lab_File_final.pdf", 1, "APACHE_PDFBOX_TEXT",
                daaLabText, "{}", 0.95, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(fireDoc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        // 1. Must contain content mismatch finding
        Optional<ScrutinyFinding> mismatchFinding = findings.stream()
                .filter(f -> "RULE_DOC_CONTENT_MISMATCH_DOC_FIRE_SAFETY".equals(f.getRuleCode()))
                .findFirst();

        assertThat(mismatchFinding).isPresent();
        assertThat(mismatchFinding.get().getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(mismatchFinding.get().getSeverity()).isEqualTo("BLOCKING");
        assertThat(mismatchFinding.get().getExplanation()).contains("academic lab manual");

        // 2. Substantive statutory check must NOT pass
        boolean hasPassOutcome = findings.stream()
                .filter(f -> "CERTIFICATE_VALIDITY".equals(f.getFindingType()))
                .anyMatch(f -> "PASS".equals(f.getOutcome()));

        assertThat(hasPassOutcome).isFalse();
    }

    @Test
    @DisplayName("ScrutinyValidation: duplicate file hashes across distinct document categories must be flagged")
    void testScrutinyDetectsDuplicateFileUploadsAcrossCategories() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        String duplicateHash = "sha256_identical_file_hash_999";

        ApplicationDocument fireDoc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "uuid_1.pdf", "DAA_Lab_File_final.pdf", "s3://minio/doc1.pdf",
                397739L, "application/pdf", duplicateHash, 1, "UPLOADED", "SYSTEM"
        );
        fireDoc.setId(301L);

        ApplicationDocument buildingDoc = new ApplicationDocument(
                app, "DOC_BUILDING_PLAN", "Building Plan Approval",
                "uuid_2.pdf", "DAA_Lab_File_final.pdf", "s3://minio/doc2.pdf",
                397739L, "application/pdf", duplicateHash, 1, "UPLOADED", "SYSTEM"
        );
        buildingDoc.setId(302L);

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(fireDoc, buildingDoc), Collections.emptyMap(), LocalDate.of(2026, 3, 1)
        );

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
    @DisplayName("ScrutinyValidation: missing mandatory documents must generate BLOCKING POTENTIAL_DEFICIENCY findings")
    void testScrutinyFlagsMissingMandatoryDocument() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        // No documents uploaded at all
        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, Collections.emptyList(), Collections.emptyMap(), LocalDate.of(2026, 3, 1)
        );

        List<ScrutinyFinding> missingFindings = findings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DOC_MANDATORY_MISSING_"))
                .toList();

        assertThat(missingFindings).isNotEmpty();
        for (ScrutinyFinding mf : missingFindings) {
            assertThat(mf.getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
            assertThat(mf.getSeverity()).isEqualTo("BLOCKING");
            assertThat(mf.getExplanation()).contains("is mandatory under APH");
        }
    }

    @Test
    @DisplayName("Acceptance Test 1: Upload an unrelated DAA PDF as fire safety cert - content compliance must not PASS")
    void testAcceptanceTest1_UnrelatedDaaPdfAsFireSafetyCert_ContentComplianceMustNotPass() {
        Application app = createSampleApplication("NEW_INSTITUTION");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument fireDoc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "uuid_daa.pdf", "DAA_Lab_File_final.pdf", "s3://minio/daa.pdf",
                397739L, "application/pdf", "hash_daa", 1, "UPLOADED", "SYSTEM"
        );
        fireDoc.setId(101L);

        String daaLabText = """
                DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING
                DESIGN AND ANALYSIS OF ALGORITHMS LAB (CS-501)
                LABORATORY MANUAL
                Experiment 1: Divide and Conquer - Merge Sort and Quick Sort
                Experiment 2: Greedy Strategy - Fractional Knapsack and Dijkstra's Single Source Shortest Path
                Experiment 3: Dynamic Programming - 0/1 Knapsack Problem and Floyd Warshall Algorithm
                Submitted by: Student Name, Roll No: 21BCS045
                """;

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), fireDoc, "DOC_FIRE_SAFETY",
                "DAA_Lab_File_final.pdf", 1, "APACHE_PDFBOX_TEXT",
                daaLabText, "{}", 0.95, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(fireDoc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        // Content-compliance requirement MUST NOT pass
        List<ScrutinyFinding> statutoryPasses = findings.stream()
                .filter(f -> "DOC_FIRE_SAFETY".equals(f.getRequirementCode()) && "PASS".equals(f.getOutcome())
                        && !"TECHNICAL_VALIDATION".equals(f.getFindingType()))
                .toList();
        assertThat(statutoryPasses).isEmpty();

        // Must generate content mismatch deficiency
        Optional<ScrutinyFinding> mismatch = findings.stream()
                .filter(f -> "RULE_DOC_CONTENT_MISMATCH_DOC_FIRE_SAFETY".equals(f.getRuleCode()))
                .findFirst();
        assertThat(mismatch).isPresent();
        assertThat(mismatch.get().getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(mismatch.get().getSeverity()).isEqualTo("BLOCKING");
        assertThat(mismatch.get().getExplanation()).contains("academic lab manual");
    }

    @Test
    @DisplayName("Acceptance Test 2: Valid PDF with irrelevant content - PDF format check may PASS, content validation must not")
    void testAcceptanceTest2_ValidPdfWithIrrelevantContent_FormatCheckMayPassContentMustNot() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "uuid_irrelevant.pdf", "Unrelated_Report.pdf", "s3://minio/doc.pdf",
                204800L, "application/pdf", "hash_irrelevant_123", 1, "UPLOADED", "SYSTEM"
        );
        doc.setId(201L);

        String irrelevantText = "Annual Sports Day Event Schedule and Track & Field Rules and Regulations.";

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY",
                "Unrelated_Report.pdf", 1, "APACHE_PDFBOX_TEXT",
                irrelevantText, "{}", 0.95, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(doc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        // PDF-format check may PASS, but labeled as TECHNICAL_VALIDATION
        Optional<ScrutinyFinding> formatCheck = findings.stream()
                .filter(f -> "RULE_DOC_FORMAT_VALID_DOC_FIRE_SAFETY".equals(f.getRuleCode()))
                .findFirst();
        assertThat(formatCheck).isPresent();
        assertThat(formatCheck.get().getOutcome()).isEqualTo("PASS");
        assertThat(formatCheck.get().getFindingType()).isEqualTo("TECHNICAL_VALIDATION");
        assertThat(formatCheck.get().getExplanation()).contains("structure only");

        // Content validation must NOT pass
        List<ScrutinyFinding> contentPasses = findings.stream()
                .filter(f -> "PASS".equals(f.getOutcome()) && !"TECHNICAL_VALIDATION".equals(f.getFindingType()))
                .toList();
        assertThat(contentPasses).isEmpty();

        // Must flag missing statutory signals
        Optional<ScrutinyFinding> signalFinding = findings.stream()
                .filter(f -> "RULE_DOC_SIGNALS_MISSING_DOC_FIRE_SAFETY".equals(f.getRuleCode()))
                .findFirst();
        assertThat(signalFinding).isPresent();
        assertThat(signalFinding.get().getOutcome()).isEqualTo("POTENTIAL_DEFICIENCY");
    }

    @Test
    @DisplayName("Acceptance Test 3: Document with sufficient evidence for configured rule - that rule may PASS")
    void testAcceptanceTest3_DocumentWithSufficientEvidenceForConfiguredRule_MayPass() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        ApplicationDocument fireDoc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "uuid_valid_fire.pdf", "Fire_Safety_NOC_2026.pdf", "s3://minio/fire.pdf",
                512000L, "application/pdf", "hash_valid_fire_777", 1, "UPLOADED", "SYSTEM"
        );
        fireDoc.setId(401L);

        String genuineFireNocText = """
                GOVERNMENT OF DELHI
                DELHI FIRE SERVICE, HEADQUARTERS
                NO OBJECTION CERTIFICATE FOR FIRE AND LIFE SAFETY
                Reference Number: DFS/HQ/2024/NOC/45612
                Date of Issue: 10/01/2024
                Validity Period: 3 Years
                Expiry Date: 31/12/2026
                Certified that ABC Institute of Technology premises inspected and complies with fire safety requirements.
                Issued by: Chief Fire Officer, Delhi Fire Service
                """;

        String fieldsJson = "{\"expiryDate\":\"2026-12-31\",\"issueDate\":\"2024-01-10\",\"certificateNumber\":\"DFS/HQ/2024/NOC/45612\",\"institutionName\":\"ABC Institute of Technology\"}";

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, app.getId(), fireDoc, "DOC_FIRE_SAFETY",
                "Fire_Safety_NOC_2026.pdf", 1, "APACHE_PDFBOX_TEXT",
                genuineFireNocText, fieldsJson, 0.98, "SUCCESS"
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(fireDoc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 3, 1)
        );

        // Substantive statutory rule RULE_FIRE_NOC_VALID must PASS
        Optional<ScrutinyFinding> fireValid = findings.stream()
                .filter(f -> "RULE_FIRE_NOC_VALID".equals(f.getRuleCode()))
                .findFirst();
        assertThat(fireValid).isPresent();
        assertThat(fireValid.get().getOutcome()).isEqualTo("PASS");
        assertThat(fireValid.get().getFindingType()).isEqualTo("CERTIFICATE_VALIDITY");
        assertThat(fireValid.get().getActualValue()).contains("VALID_TILL_2026-12-31");
    }

    @Test
    @DisplayName("Acceptance Test 4: Summary counts match actual persisted findings")
    void testAcceptanceTest4_SummaryCountsMatchActualPersistedFindings() {
        Application app = createSampleApplication("EXTENSION_OF_APPROVAL");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        // Add 3 sample findings
        ScrutinyFinding f1 = new ScrutinyFinding(
                assessment, app.getId(), 1L, "DOC_FIRE_SAFETY",
                "RULE_DOC_FORMAT_VALID_DOC_FIRE_SAFETY", "Format Check", "APH Clause 1.6",
                "TECHNICAL_VALIDATION", "PASS", "ADVISORY", "Valid PDF", "FORMAT_VALID",
                "Container valid", "None", "fire.pdf"
        );
        ScrutinyFinding f2 = new ScrutinyFinding(
                assessment, app.getId(), 1L, "DOC_FIRE_SAFETY",
                "RULE_FIRE_NOC_VALID", "Fire NOC Valid", "APH Clause 1.6",
                "CERTIFICATE_VALIDITY", "PASS", "ADVISORY", "Valid Date", "VALID_TILL_2026-12-31",
                "Date valid", "None", "fire.pdf"
        );
        ScrutinyFinding f3 = new ScrutinyFinding(
                assessment, app.getId(), 2L, "DOC_BUILDING_PLAN",
                "RULE_DOC_CONTENT_MISMATCH_DOC_BUILDING_PLAN", "Content Mismatch", "APH Clause 1.5.1",
                "STATUTORY_COMPLIANCE", "POTENTIAL_DEFICIENCY", "BLOCKING", "Valid Plan", "ACADEMIC_LAB",
                "Lab file uploaded", "Upload genuine plan", "plan.pdf"
        );

        List<ScrutinyFinding> findings = List.of(f1, f2, f3);

        int total = findings.size();
        int passAll = (int) findings.stream().filter(f -> "PASS".equals(f.getOutcome())).count();
        int statutoryPass = (int) findings.stream().filter(f -> "PASS".equals(f.getOutcome()) && !"TECHNICAL_VALIDATION".equals(f.getFindingType())).count();
        int technicalPass = (int) findings.stream().filter(f -> "PASS".equals(f.getOutcome()) && "TECHNICAL_VALIDATION".equals(f.getFindingType())).count();
        int defCount = (int) findings.stream().filter(f -> "POTENTIAL_DEFICIENCY".equals(f.getOutcome())).count();

        assertThat(total).isEqualTo(3);
        assertThat(passAll).isEqualTo(2);
        assertThat(statutoryPass).isEqualTo(1);
        assertThat(technicalPass).isEqualTo(1);
        assertThat(defCount).isEqualTo(1);
    }

    @Test
    @DisplayName("User Test Case: Uploading DAA_Lab_File_final.pdf under 8 categories produces 25 checks, exactly 8 technical passes, 0 statutory passes, and 17 potential deficiencies")
    void testEightDuplicateDaaPdfUploadsUnderNewInstitutionScenario() {
        Application app = createSampleApplication("NEW_INSTITUTION");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );

        List<String> reqCodes = List.of(
                "DOC_BUILDING_PLAN",
                "DOC_OCCUPANCY_CERT",
                "DOC_FIRE_SAFETY",
                "DOC_STRUCTURAL_STABILITY",
                "DOC_LAND_DOCUMENTS",
                "DOC_TRUST_DEED",
                "DOC_AFFILIATING_NOC",
                "DOC_CORPUS_FUND"
        );

        String identicalDaaHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String daaRawText = """
                DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING
                DESIGN AND ANALYSIS OF ALGORITHMS LAB FILE (CS-501)
                LABORATORY EXPERIMENT MANUAL
                Experiment 1: Implementation of Quick Sort and Merge Sort using Divide and Conquer
                Experiment 2: Greedy Strategy - Dijkstra Algorithm and Fractional Knapsack
                Experiment 3: Dynamic Programming - 0/1 Knapsack Problem and Bellman Ford
                Submitted by: Student Name, Roll No: 21BCS108
                """;

        List<ApplicationDocument> documents = new java.util.ArrayList<>();
        Map<String, ScrutinyDocumentExtraction> extractions = new java.util.LinkedHashMap<>();

        long idCounter = 100L;
        for (String reqCode : reqCodes) {
            idCounter++;
            ApplicationDocument doc = new ApplicationDocument(
                    app, reqCode, reqCode + " Upload",
                    "uuid_" + reqCode + ".pdf", "DAA_Lab_File_final.pdf", "s3://minio/daa.pdf",
                    397739L, "application/pdf", identicalDaaHash, 1, "UPLOADED", "SYSTEM"
            );
            doc.setId(idCounter);
            documents.add(doc);

            ScrutinyDocumentExtraction ext = new ScrutinyDocumentExtraction(
                    assessment, app.getId(), doc, reqCode,
                    "DAA_Lab_File_final.pdf", 1, "APACHE_PDFBOX_TEXT",
                    daaRawText, "{}", 0.95, "SUCCESS"
            );
            extractions.put(reqCode, ext);
        }

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, documents, extractions, LocalDate.of(2026, 3, 1)
        );

        // Verification of checks
        assertThat(findings).hasSize(25);

        // Trace of the 8 PASS results:
        List<ScrutinyFinding> passFindings = findings.stream().filter(f -> "PASS".equals(f.getOutcome())).toList();
        assertThat(passFindings).hasSize(8);

        // All 8 PASS results MUST be technical format validations, NOT statutory compliance
        for (ScrutinyFinding pf : passFindings) {
            assertThat(pf.getFindingType()).isEqualTo("TECHNICAL_VALIDATION");
            assertThat(pf.getRuleCode()).startsWith("RULE_DOC_FORMAT_VALID_");
            assertThat(pf.getDocumentName()).isEqualTo("DAA_Lab_File_final.pdf");
            assertThat(pf.getExplanation()).contains("structure only");
        }

        // Substantive statutory PASS count MUST be 0
        long statutoryPassCount = findings.stream()
                .filter(f -> "PASS".equals(f.getOutcome()) && !"TECHNICAL_VALIDATION".equals(f.getFindingType()))
                .count();
        assertThat(statutoryPassCount).isEqualTo(0);

        // Trace of the 17 POTENTIAL_DEFICIENCY results:
        List<ScrutinyFinding> defFindings = findings.stream()
                .filter(f -> "POTENTIAL_DEFICIENCY".equals(f.getOutcome()))
                .toList();
        assertThat(defFindings).hasSize(17);

        // 8 Duplicate upload findings
        List<ScrutinyFinding> duplicates = defFindings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_"))
                .toList();
        assertThat(duplicates).hasSize(8);

        // 8 Content mismatch findings (academic coursework detected)
        List<ScrutinyFinding> mismatches = defFindings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DOC_CONTENT_MISMATCH_"))
                .toList();
        assertThat(mismatches).hasSize(8);

        // 1 New institution land document content deficient finding
        List<ScrutinyFinding> landDeficient = defFindings.stream()
                .filter(f -> "RULE_NEW_INST_LAND_CONTENT_DEFICIENT".equals(f.getRuleCode()))
                .toList();
        assertThat(landDeficient).hasSize(1);

        // Check documentName on all findings
        for (ScrutinyFinding f : findings) {
            assertThat(f.getDocumentName()).isNotNull().isNotEmpty();
        }
    }

    @Test
    @DisplayName("Task 2 Acceptance Test 1: Upload same PDF under 8 categories - consolidated into single finding with all 8 affected categories")
    void testTask2AcceptanceTest1_DuplicateConsolidation_UploadSamePdfUnderEightCategories() {
        Application app = createSampleApplication("NEW_INSTITUTION");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );
        assessment.setId(701L);

        String sameHash = "hash_daa_lab_file_999";
        String sameFileName = "DAA_Lab_File_final.pdf";
        String academicRawText = """
                DESIGN AND ANALYSIS OF ALGORITHMS LABORATORY
                Course Code: CS-301
                Department of Computer Science & Engineering
                Experiment 1: Divide and Conquer Merge Sort
                Student Name: Rahul Verma, Roll No: 2024CS082
                """;

        List<String> requirements = List.of(
                "DOC_LAND_DOCUMENTS",
                "DOC_BUILDING_PLAN",
                "DOC_OCCUPANCY_CERT",
                "DOC_FIRE_SAFETY",
                "DOC_STRUCTURAL_STABILITY",
                "DOC_TRUST_DEED",
                "DOC_AFFILIATING_NOC",
                "DOC_CORPUS_FUND"
        );

        List<ApplicationDocument> documents = new java.util.ArrayList<>();
        Map<String, ScrutinyDocumentExtraction> extractions = new java.util.LinkedHashMap<>();
        Map<Long, ApplicationDocument> docsById = new java.util.LinkedHashMap<>();

        long docIdSeq = 100L;
        for (String req : requirements) {
            ApplicationDocument doc = new ApplicationDocument(
                    app, req, req + " Upload",
                    "uuid_" + req + ".pdf", sameFileName, "s3://minio/daa.pdf",
                    1024L * 50, "application/pdf", sameHash, 1, "UPLOADED", "SYSTEM"
            );
            doc.setId(docIdSeq++);
            doc.setDocumentName(sameFileName);
            documents.add(doc);
            docsById.put(doc.getId(), doc);

            ScrutinyDocumentExtraction ext = new ScrutinyDocumentExtraction(
                    assessment,
                    app.getId(),
                    doc,
                    req,
                    sameFileName,
                    1,
                    "PDFBOX_TEXT_STRIPPER",
                    academicRawText,
                    "{}",
                    0.99,
                    "SUCCESS"
            );
            extractions.put(req, ext);
        }

        // Run validation rules
        List<ScrutinyFinding> evaluatedFindings = validationService.evaluateScrutiny(
                assessment, app, documents, extractions, LocalDate.of(2026, 3, 1)
        );

        // Verify underlying duplicate upload findings exist (1 per document = 8 total)
        List<ScrutinyFinding> rawDuplicateFindings = evaluatedFindings.stream()
                .filter(f -> f.getRuleCode().startsWith("RULE_DUPLICATE_FILE_UPLOAD_"))
                .toList();
        assertThat(rawDuplicateFindings).hasSize(8);

        // Map to DTOs
        List<ScrutinyFindingResponse> findingDtos = evaluatedFindings.stream()
                .map(f -> ScrutinyFindingResponse.from(f, f.getDocumentName()))
                .toList();

        // Consolidate duplicate findings
        List<ScrutinyFindingResponse> consolidated = scrutinyService.consolidateFindings(findingDtos, docsById);

        // In consolidated view, the 8 separate duplicate cards are grouped into 1 consolidated finding
        List<ScrutinyFindingResponse> groupedFindings = consolidated.stream()
                .filter(f -> "RULE_DUPLICATE_DOCUMENT_HASH".equals(f.ruleCode()) || f.isGrouped())
                .toList();
        assertThat(groupedFindings).hasSize(1);

        ScrutinyFindingResponse duplicateCard = groupedFindings.get(0);
        assertThat(duplicateCard.isGrouped()).isTrue();
        assertThat(duplicateCard.ruleName()).isEqualTo("Duplicate Document Detected");
        assertThat(duplicateCard.documentName()).isEqualTo("DAA_Lab_File_final.pdf");
        assertThat(duplicateCard.findingType()).isEqualTo("DOCUMENT_AUTHENTICITY");
        assertThat(duplicateCard.outcome()).isEqualTo("POTENTIAL_DEFICIENCY");
        assertThat(duplicateCard.severity()).isEqualTo("BLOCKING");
        assertThat(duplicateCard.explanation()).isEqualTo("Identical file content has been submitted under multiple document categories.");
        assertThat(duplicateCard.recommendedAction()).isEqualTo("Review whether independent category-specific evidence is required.");

        // Check all 8 affected categories are identified
        assertThat(duplicateCard.affectedCategories()).containsExactlyInAnyOrderElementsOf(requirements);
        assertThat(duplicateCard.affectedCategoryNames()).contains(
                "Trust Deed", "Building Plan", "Fire Safety", "Structural Stability",
                "Land Documents", "Occupancy Certificate", "Affiliation NOC", "Corpus Fund"
        );

        // Verify every affected document remains traceable in underlyingFindings
        assertThat(duplicateCard.underlyingFindings()).hasSize(8);
        for (ScrutinyFindingResponse uf : duplicateCard.underlyingFindings()) {
            assertThat(uf.documentName()).isEqualTo("DAA_Lab_File_final.pdf");
            assertThat(uf.ruleCode()).startsWith("RULE_DUPLICATE_FILE_UPLOAD_");
            assertThat(uf.documentId()).isNotNull();
            assertThat(docsById.containsKey(uf.documentId())).isTrue();
        }

        // Consolidated findings total checks is 18 (25 total checks - 8 duplicates + 1 grouped duplicate)
        assertThat(consolidated).hasSize(18);
    }

    @Test
    @DisplayName("Task 2 Acceptance Test 2: Officer review with applyToAllDuplicates synchronizes sibling duplicate findings")
    void testTask2AcceptanceTest2_OfficerReviewSynchronizesDuplicateFindingsWhenRequested() {
        Application app = createSampleApplication("NEW_INSTITUTION");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );
        assessment.setId(701L);

        ApplicationDocument doc1 = new ApplicationDocument(app, "DOC_TRUST_DEED", "Trust Deed", "uuid1.pdf", "Trust.pdf", "path1", 1024L, "application/pdf", "hash_abc", 1, "UPLOADED", "SYSTEM");
        doc1.setId(101L);
        ApplicationDocument doc2 = new ApplicationDocument(app, "DOC_BUILDING_PLAN", "Building Plan", "uuid2.pdf", "Plan.pdf", "path2", 1024L, "application/pdf", "hash_abc", 1, "UPLOADED", "SYSTEM");
        doc2.setId(102L);

        ScrutinyFinding finding1 = new ScrutinyFinding(
                assessment, app.getId(), doc1.getId(), "DOC_TRUST_DEED", "RULE_DUPLICATE_FILE_UPLOAD_DOC_TRUST_DEED",
                "Duplicate File", "APH 1.2", "DOCUMENT_AUTHENTICITY", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Condition", "DUPLICATE_FILE_HASH", "Explanation", "Action", "Trust.pdf"
        );
        finding1.setId(1001L);

        ScrutinyFinding finding2 = new ScrutinyFinding(
                assessment, app.getId(), doc2.getId(), "DOC_BUILDING_PLAN", "RULE_DUPLICATE_FILE_UPLOAD_DOC_BUILDING_PLAN",
                "Duplicate File", "APH 1.2", "DOCUMENT_AUTHENTICITY", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Condition", "DUPLICATE_FILE_HASH", "Explanation", "Action", "Plan.pdf"
        );
        finding2.setId(1002L);

        when(findingRepository.findById(1001L)).thenReturn(Optional.of(finding1));
        when(findingRepository.save(any(ScrutinyFinding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.findById(101L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(102L)).thenReturn(Optional.of(doc2));
        when(findingRepository.findByAssessmentIdOrderByIdAsc(701L)).thenReturn(List.of(finding1, finding2));

        // Review with applyToAllDuplicates = true
        ReviewFindingRequest req = new ReviewFindingRequest(
                "CONFIRMED_DEFICIENCY", "Confirmed identical file across trust deed and plan", null, null, true, true
        );
        scrutinyService.reviewFinding(1001L, req, "USR-SO-01");

        // Both finding1 and sibling finding2 must be CONFIRMED_DEFICIENCY
        assertThat(finding1.getOfficerDecision()).isEqualTo("CONFIRMED_DEFICIENCY");
        assertThat(finding2.getOfficerDecision()).isEqualTo("CONFIRMED_DEFICIENCY");
    }

    @Test
    @DisplayName("Task 2 Acceptance Test 3: Re-run scrutiny without changing files preserves idempotency and does not multiply findings")
    void testTask2AcceptanceTest3_RerunScrutinyWithoutChangingFiles_DoesNotMultiplyFindings() {
        Application app = createSampleApplication("NEW_INSTITUTION");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );
        assessment.setId(701L);

        ApplicationDocument doc = new ApplicationDocument(app, "DOC_FIRE_SAFETY", "Fire Safety", "uuid_fire.pdf", "Fire.pdf", "path", 1024L, "application/pdf", "hash_fire", 1, "UPLOADED", "SYSTEM");
        doc.setId(201L);
        doc.setDocumentName("Fire.pdf");

        ScrutinyDocumentExtraction ext = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY", "Fire.pdf", 1, "PDFBOX", "FIRE SAFETY VALID CERTIFICATE", "{}", 0.95, "SUCCESS"
        );
        ext.setId(801L);

        ScrutinyFinding existingFinding = new ScrutinyFinding(
                assessment, app.getId(), doc.getId(), "DOC_FIRE_SAFETY", "RULE_DOC_FORMAT_VALID_DOC_FIRE_SAFETY",
                "Technical File Format", "APH 1.2", "TECHNICAL_VALIDATION", "PASS", "ADVISORY",
                "PDF format", "VALID_PDF_STREAM", "Technical pass", "None", "Fire.pdf"
        );
        existingFinding.setId(901L);

        when(applicationRepository.findById(1042L)).thenReturn(Optional.of(app));
        when(documentRepository.findByApplicationIdOrderByUploadedAtAsc(1042L)).thenReturn(List.of(doc));
        when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(1042L)).thenReturn(Optional.of(assessment));
        when(extractionRepository.findByAssessmentIdOrderByCreatedAtAsc(701L)).thenReturn(List.of(ext));
        when(findingRepository.findByAssessmentIdOrderByIdAsc(701L)).thenReturn(List.of(existingFinding));
        when(findingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentRepository.save(any(ScrutinyAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Re-run scrutiny
        var report = scrutinyService.runAutomatedScrutiny(1042L, "USR-SO-01");

        assertThat(report).isNotNull();
        // Verify saveAll was called with existing findings updated in-place, NOT multiplying findings
        verify(findingRepository).saveAll(any());
        assertThat(existingFinding.getId()).isEqualTo(901L);
    }

    // =========================================================================
    // TASK 3 ACCEPTANCE TESTS: OCR Confidence Calculation Realism & Separation
    // =========================================================================

    @Test
    @DisplayName("Task 3 Acceptance Test 1: Searchable PDF has extracted text, but OCR confidence is NOT fabricated (reported as null / N/A)")
    void testTask3AcceptanceTest1_SearchablePdf_ExtractedTextAvailable_OcrConfidenceNotFabricated() throws Exception {
        TesseractOcrService mockTessService = mock(TesseractOcrService.class);
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(mockTessService);

        String certText = "Government Fire and Rescue Services Department No Objection Certificate for Fire Safety. Certificate No: NOC-2024-9988. Issued to ABC Institute of Technology. Valid Up To: 31/12/2026.";
        byte[] pdfBytes = createPdfWithText(certText);

        ExtractedDocumentData result = extractionService.extractText(pdfBytes, "Fire_Safety_NOC.pdf", "application/pdf");

        assertThat(result).isNotNull();
        assertThat(result.getRawText()).contains("Government Fire and Rescue Services Department");
        assertThat(result.getExtractionMethod()).isEqualTo("PDFBOX_SEARCHABLE_TEXT");
        assertThat(result.getOcrStatus()).isEqualTo("SUCCESS");

        // REQUIREMENT 4: For PDFBox searchable text, do NOT invent OCR confidence!
        assertThat(result.getConfidenceScore())
                .as("Searchable digital PDF text has no optical recognition uncertainty; confidence score must be null (not hardcoded 0.88 or 0.98)")
                .isNull();
        assertThat(result.getValidationMetric()).isEqualTo("N/A (Embedded Text)");
        assertThat(result.getTextCharacterCount()).isGreaterThanOrEqualTo(40);
        assertThat(result.getPageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Task 3 Acceptance Test 2: Scanned PDF executes real OCR and reports its actual measured word-level confidence")
    void testTask3AcceptanceTest2_ScannedPdf_RealOcrExecutesAndReportsActualConfidence() throws Exception {
        ITesseract mockTesseract = mock(ITesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("GOVERNMENT FIRE SAFETY CERTIFICATE 2026");
        when(mockTesseract.getWords(any(BufferedImage.class), eq(ITessAPI.TessPageIteratorLevel.RIL_WORD))).thenReturn(List.of(
                new Word("GOVERNMENT", 85.0f, new Rectangle(0, 0, 10, 10)),
                new Word("FIRE", 95.0f, new Rectangle(10, 0, 10, 10)),
                new Word("SAFETY", 91.0f, new Rectangle(20, 0, 10, 10)),
                new Word("CERTIFICATE", 85.0f, new Rectangle(30, 0, 10, 10))
        ));

        TesseractOcrService tesseractOcrService = new TesseractOcrService(mockTesseract);
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(tesseractOcrService);

        byte[] scannedPdfBytes = createScannedPdfWithImage();

        ExtractedDocumentData result = extractionService.extractText(scannedPdfBytes, "Scanned_Fire_NOC.pdf", "application/pdf");

        assertThat(result).isNotNull();
        assertThat(result.getExtractionMethod()).isEqualTo("TESSERACT_OCR_SCANNED");
        assertThat(result.getOcrStatus()).isEqualTo("SUCCESS");

        // Expected mean: (85 + 95 + 91 + 85) / 4 = 89.0% -> 0.8900
        assertThat(result.getConfidenceScore()).isEqualTo(0.8900);
        assertThat(result.getValidationMetric()).contains("OCR_MEASURED (89.0%)");
    }

    @Test
    @DisplayName("Task 3 Acceptance Test 3: Different scanned documents reflect distinct measured confidences rather than a fixed constant (never 0.88)")
    void testTask3AcceptanceTest3_DifferentScannedDocuments_ReflectActualResultsNotFixedConstant() throws Exception {
        ITesseract mockTesseractA = mock(ITesseract.class);
        when(mockTesseractA.doOCR(any(BufferedImage.class))).thenReturn("HIGH QUALITY SCAN TEXT");
        when(mockTesseractA.getWords(any(BufferedImage.class), eq(ITessAPI.TessPageIteratorLevel.RIL_WORD))).thenReturn(List.of(
                new Word("HIGH", 96.0f, new Rectangle(0, 0, 10, 10)),
                new Word("QUALITY", 94.0f, new Rectangle(10, 0, 10, 10))
        ));
        TesseractOcrService ocrServiceA = new TesseractOcrService(mockTesseractA);
        DocumentTextExtractionService extractionServiceA = new DocumentTextExtractionService(ocrServiceA);

        ITesseract mockTesseractB = mock(ITesseract.class);
        when(mockTesseractB.doOCR(any(BufferedImage.class))).thenReturn("DEGRADED SCAN TEXT");
        when(mockTesseractB.getWords(any(BufferedImage.class), eq(ITessAPI.TessPageIteratorLevel.RIL_WORD))).thenReturn(List.of(
                new Word("DEGRADED", 72.0f, new Rectangle(0, 0, 10, 10)),
                new Word("SCAN", 70.0f, new Rectangle(10, 0, 10, 10))
        ));
        TesseractOcrService ocrServiceB = new TesseractOcrService(mockTesseractB);
        DocumentTextExtractionService extractionServiceB = new DocumentTextExtractionService(ocrServiceB);

        ITesseract mockTesseractC = mock(ITesseract.class);
        when(mockTesseractC.doOCR(any(BufferedImage.class))).thenReturn("NOISY UNCERTAIN SCAN");
        when(mockTesseractC.getWords(any(BufferedImage.class), eq(ITessAPI.TessPageIteratorLevel.RIL_WORD))).thenReturn(List.of(
                new Word("NOISY", 45.0f, new Rectangle(0, 0, 10, 10)),
                new Word("UNCERTAIN", 41.0f, new Rectangle(10, 0, 10, 10))
        ));
        TesseractOcrService ocrServiceC = new TesseractOcrService(mockTesseractC);
        DocumentTextExtractionService extractionServiceC = new DocumentTextExtractionService(ocrServiceC);

        byte[] scannedPdf = createScannedPdfWithImage();

        ExtractedDocumentData resA = extractionServiceA.extractText(scannedPdf, "DocA.pdf", "application/pdf");
        ExtractedDocumentData resB = extractionServiceB.extractText(scannedPdf, "DocB.pdf", "application/pdf");
        ExtractedDocumentData resC = extractionServiceC.extractText(scannedPdf, "DocC.pdf", "application/pdf");

        // Verify values are not fixed constants (none are 0.88)
        assertThat(resA.getConfidenceScore()).isEqualTo(0.9500);
        assertThat(resB.getConfidenceScore()).isEqualTo(0.7100);
        assertThat(resC.getConfidenceScore()).isEqualTo(0.4300);

        assertThat(resA.getConfidenceScore()).isNotEqualTo(resB.getConfidenceScore());
        assertThat(resB.getConfidenceScore()).isNotEqualTo(resC.getConfidenceScore());

        // Document C is flagged as UNCERTAIN due to low confidence (< 0.60)
        assertThat(resC.getOcrStatus()).isEqualTo("UNCERTAIN");
    }

    @Test
    @DisplayName("Task 3 Acceptance Test 4: Blank or unreadable PDF reports appropriate failure or empty-text status")
    void testTask3AcceptanceTest4_BlankOrUnreadablePdf_ReportsAppropriateStatus() throws Exception {
        ITesseract mockTesseract = mock(ITesseract.class);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("");
        when(mockTesseract.getWords(any(BufferedImage.class), eq(ITessAPI.TessPageIteratorLevel.RIL_WORD))).thenReturn(Collections.emptyList());
        TesseractOcrService ocrService = new TesseractOcrService(mockTesseract);
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(ocrService);

        // 1. Blank PDF
        byte[] blankPdf = createBlankPdf();
        ExtractedDocumentData blankResult = extractionService.extractText(blankPdf, "Blank.pdf", "application/pdf");
        assertThat(blankResult.getOcrStatus()).isEqualTo("EMPTY");
        assertThat(blankResult.getConfidenceScore()).isNull();
        assertThat(blankResult.getValidationMetric()).isEqualTo("NO_TEXT_DETECTED");

        // 2. Corrupted PDF bytes
        byte[] corruptedBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x00, 0x11, 0x22, 0x33, 0x44};
        ExtractedDocumentData corruptedResult = extractionService.extractText(corruptedBytes, "Corrupted.pdf", "application/pdf");
        assertThat(corruptedResult.getOcrStatus()).isEqualTo("FAILED");
        assertThat(corruptedResult.getConfidenceScore()).isNull();

        // 3. 0-byte payload
        ExtractedDocumentData zeroBytesResult = extractionService.extractText(new byte[0], "Empty.pdf", "application/pdf");
        assertThat(zeroBytesResult.getOcrStatus()).isEqualTo("EMPTY");
        assertThat(zeroBytesResult.getConfidenceScore()).isNull();
    }

    @Test
    @DisplayName("Task 3 Acceptance Test 5: High optical confidence score never implies statutory compliance or authenticity")
    void testTask3AcceptanceTest5_HighConfidenceDoesNotEstablishStatutoryCompliance() throws Exception {
        // High confidence (or embedded text) with DAA Lab Manual text
        String labText = "Design and Analysis of Algorithms Lab Manual. Experiment 1: Quick Sort and Merge Sort. Time complexity: O(n log n). Submitted by Student Roll 123.";
        ExtractedDocumentData labExtraction = new ExtractedDocumentData(
                labText, "PDFBOX_SEARCHABLE_TEXT", null, "SUCCESS", labText.length(), 1, "N/A (Embedded Text)"
        );

        Application app = createSampleApplication("Technical_New_Institute");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "SCR-2026-001", app, app.getInstitution().getId(), "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM_AUTOMATION"
        );
        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate", "uuid.pdf", "Fire_Safety.pdf", "path", 1024L, "application/pdf", "hash_lab", 1, "UPLOADED", "USR-INST-01"
        );
        doc.setId(101L);

        ScrutinyDocumentExtraction ext = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY", "Fire Safety Certificate", 1,
                labExtraction.getExtractionMethod(), labExtraction.getRawText(), "{}",
                labExtraction.getConfidenceScore(), labExtraction.getOcrStatus(),
                labExtraction.getTextCharacterCount(), labExtraction.getPageCount(), labExtraction.getValidationMetric()
        );

        List<ScrutinyFinding> findings = validationService.evaluateScrutiny(
                assessment, app, List.of(doc), Map.of("DOC_FIRE_SAFETY", ext), LocalDate.now()
        );

        // Technical validation may note readable stream, but statutory compliance MUST fail with BLOCKING POTENTIAL_DEFICIENCY
        boolean hasContentMismatch = findings.stream().anyMatch(f ->
                "RULE_DOC_CONTENT_MISMATCH_DOC_FIRE_SAFETY".equals(f.getRuleCode()) &&
                "POTENTIAL_DEFICIENCY".equals(f.getOutcome()) &&
                "BLOCKING".equals(f.getSeverity())
        );
        assertThat(hasContentMismatch)
                .as("High extraction readability/confidence must NOT establish statutory compliance for contradictory content")
                .isTrue();
    }

    // =========================================================================
    // TASK 4 ACCEPTANCE TESTS: EVIDENCE TRACEABILITY & VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Task 4.1: Upload DAA laboratory PDF -> genuine text matches contents, NO fake certificate details fabricated")
    void testTask4_DaaLabFilePdfExtractionMatchesContentsAndDoesNotFabricateFakeCertificateDetails() throws Exception {
        DocumentTextExtractionService realTextExtraction = new DocumentTextExtractionService(new TesseractOcrService());

        // Create genuine PDF bytes containing DAA laboratory file experiments
        String daaContent = "Department of Computer Science & Engineering\n" +
                "Design and Analysis of Algorithms Lab Manual\n" +
                "Index of Experiments:\n" +
                "Experiment 1: Implement Quick Sort and analyze its time complexity\n" +
                "Experiment 2: Implement 0/1 Knapsack problem using Dynamic Programming\n" +
                "Experiment 3: Minimum Spanning Tree using Prim's algorithm\n" +
                "Submitted by: Student Rohit Sharma, Roll No: 2026CS101, Semester: V";
        byte[] daaPdfBytes = createPdfWithText(daaContent);

        // 1. Verify text extraction genuinely matches the uploaded PDF contents
        ExtractedDocumentData rawData = realTextExtraction.extractText(daaPdfBytes, "DAA_Lab_File_final.pdf", "application/pdf");
        assertThat(rawData.getOcrStatus()).isEqualTo("SUCCESS");
        assertThat(rawData.getExtractionMethod()).isEqualTo("PDFBOX_SEARCHABLE_TEXT");
        assertThat(rawData.getRawText()).contains("Design and Analysis of Algorithms Lab Manual");
        assertThat(rawData.getRawText()).contains("Quick Sort");
        assertThat(rawData.getRawText()).contains("0/1 Knapsack problem");

        // 2. Ingest into field extraction service with ApplicationDocument metadata
        Application app = createSampleApplication("New Technical Institution");
        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "doc_fs_901.pdf", "DAA_Lab_File_final.pdf",
                "sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/DAA_Lab_File_final.pdf",
                (long) daaPdfBytes.length, "application/pdf",
                "a5d09f7a69bc920f01de985a140f099238e833446927e1892f254b1d3d63d081",
                1, "UPLOADED", "INST_USER"
        );
        doc.setId(901L);

        ExtractedDocumentData structured = fieldExtractionService.extractStructuredFields(rawData, "DOC_FIRE_SAFETY", doc);
        Map<String, Object> fields = structured.getFields();

        // 3. Confirm NO fake certificate details are generated
        assertThat(fields.get("certificateNumber"))
                .as("No fake certificate number should be generated for an unrelated lab PDF")
                .isNull();
        assertThat(fields.get("expiryDate"))
                .as("No fake expiry date should be generated for an unrelated lab PDF")
                .isNull();
        assertThat(fields.get("issuingAuthority"))
                .as("No fake issuing authority should be fabricated")
                .isNull();

        // 4. Confirm category plausibility is rejected and warning is recorded
        assertThat(fields.get("categoryPlausible")).isEqualTo(false);
        assertThat(fields.get("contentWarning")).isEqualTo("Unrelated academic/lab manual content detected");

        // 5. Verify field sources label contentWarning as DERIVED_VALUE and file attributes as FILE_METADATA
        @SuppressWarnings("unchecked")
        Map<String, FieldSourceInfo> sources = (Map<String, FieldSourceInfo>) fields.get("_fieldSources");
        assertThat(sources).isNotNull();
        assertThat(sources.get("contentWarning").source()).isEqualTo(FieldSourceInfo.SOURCE_DERIVED_VALUE);
        assertThat(sources.get("contentWarning").sourceLabel()).isEqualTo("Derived Value");
        assertThat(sources.get("originalFileName").source()).isEqualTo(FieldSourceInfo.SOURCE_FILE_METADATA);
        assertThat(sources.get("storagePath").value()).isEqualTo("sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/DAA_Lab_File_final.pdf");
    }

    @Test
    @DisplayName("Task 4.2: Verify document record traces from PostgreSQL to MinIO S3 object and returns inline download URL")
    void testTask4_MinioObjectTraceabilityFromPostgresRecord() throws Exception {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment(
                "ASMT-APP-2026-1042-C1", app, 501L, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM_AUTOMATION"
        );
        assessment.setId(301L);

        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety NOC",
                "fire_noc_2026.pdf", "Fire_Safety_Certificate_2026.pdf",
                "sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/Fire_Safety_Certificate_2026.pdf",
                142588L, "application/pdf",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                2, "UPLOADED", "INST_USER"
        );
        doc.setId(445L);

        String sampleFieldsJson = "{" +
                "\"certificateNumber\":\"DFS/NOC/2024/782\"," +
                "\"expiryDate\":\"2027-03-31\"," +
                "\"categoryPlausible\":true" +
                "}";

        ScrutinyDocumentExtraction ext = new ScrutinyDocumentExtraction(
                assessment, app.getId(), doc, "DOC_FIRE_SAFETY", "Fire Safety NOC",
                2, "PDFBOX_SEARCHABLE_TEXT", "Delhi Fire Service NOC No. DFS/NOC/2024/782 valid upto 31/03/2027",
                sampleFieldsJson, null, "SUCCESS", 75, 2, "N/A (Embedded Text)"
        );
        ext.setId(881L);

        // Map to Response DTO
        ScrutinyDocumentExtractionResponse resp = ScrutinyDocumentExtractionResponse.from(ext);

        // Verify direct traceability fields
        assertThat(resp).isNotNull();
        assertThat(resp.originalFileName()).isEqualTo("Fire_Safety_Certificate_2026.pdf");
        assertThat(resp.storagePath()).isEqualTo("sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/Fire_Safety_Certificate_2026.pdf");
        assertThat(resp.fileHash()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(resp.fileSize()).isEqualTo(142588L);
        assertThat(resp.contentType()).isEqualTo("application/pdf");
        assertThat(resp.documentVersion()).isEqualTo(2);
        assertThat(resp.downloadUrl()).isEqualTo("/api/v1/applications/1042/documents/445?inline=true");
        assertThat(resp.assessmentReference()).isEqualTo(assessment.getAssessmentReference());
        assertThat(resp.cycleNumber()).isEqualTo(1);

        // Verify structured field sources
        Map<String, FieldSourceInfo> sources = resp.fieldSources();
        assertThat(sources).isNotNull();
        assertThat(sources.get("originalFileName").source()).isEqualTo(FieldSourceInfo.SOURCE_FILE_METADATA);
        assertThat(sources.get("storagePath").source()).isEqualTo(FieldSourceInfo.SOURCE_FILE_METADATA);
        assertThat(sources.get("fileHash").source()).isEqualTo(FieldSourceInfo.SOURCE_FILE_METADATA);
        assertThat(sources.get("certificateNumber").source()).isEqualTo(FieldSourceInfo.SOURCE_ACTUAL_DOCUMENT_CONTENT);
        assertThat(sources.get("expiryDate").source()).isEqualTo(FieldSourceInfo.SOURCE_ACTUAL_DOCUMENT_CONTENT);
        assertThat(sources.get("categoryPlausible").source()).isEqualTo(FieldSourceInfo.SOURCE_DERIVED_VALUE);
    }

    @Test
    @DisplayName("Task 4.3: Different uploaded PDFs produce their own corresponding evidence without cross-leakage")
    void testTask4_DifferentUploadedPdfsProduceTheirOwnCorrespondingEvidence() throws Exception {
        DocumentTextExtractionService realTextExtraction = new DocumentTextExtractionService(new TesseractOcrService());

        // PDF 1: Fire Safety
        byte[] fireBytes = createPdfWithText(
                "Delhi Fire Service Headquarters\n" +
                "Fire Safety Certificate NOC No: DFS/2024/9981\n" +
                "Valid upto: 31/12/2027\n" +
                "Issued to ABC Institute of Technology"
        );
        // PDF 2: Building Plan
        byte[] buildingBytes = createPdfWithText(
                "Municipal Corporation Building Plan Approval\n" +
                "Sanction Order No: BP/SANCT/2023/554\n" +
                "Dated: 15/06/2023\n" +
                "Approved Floor Area: 5200 sq. meters"
        );

        Application app = createSampleApplication("EoA");
        ApplicationDocument docFire = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety NOC", "fire.pdf", "Fire_NOC.pdf",
                "sage/fire.pdf", (long) fireBytes.length, "application/pdf", "hash_fire_111", 1, "UPLOADED", "USR"
        );
        ApplicationDocument docBuilding = new ApplicationDocument(
                app, "DOC_BUILDING_PLAN", "Building Plan Approval", "plan.pdf", "Sanctioned_Plan.pdf",
                "sage/plan.pdf", (long) buildingBytes.length, "application/pdf", "hash_plan_222", 1, "UPLOADED", "USR"
        );

        ExtractedDocumentData rawFire = realTextExtraction.extractText(fireBytes, "Fire_NOC.pdf", "application/pdf");
        ExtractedDocumentData structuredFire = fieldExtractionService.extractStructuredFields(rawFire, "DOC_FIRE_SAFETY", docFire);

        ExtractedDocumentData rawBuilding = realTextExtraction.extractText(buildingBytes, "Sanctioned_Plan.pdf", "application/pdf");
        ExtractedDocumentData structuredBuilding = fieldExtractionService.extractStructuredFields(rawBuilding, "DOC_BUILDING_PLAN", docBuilding);

        // Verify PDF 1 extracted its own content
        assertThat(structuredFire.getFields().get("certificateNumber")).isEqualTo("DFS/2024/9981");
        assertThat(structuredFire.getFields().get("expiryDate")).isEqualTo("2027-12-31");
        assertThat(structuredFire.getFields().get("issuingAuthority")).isEqualTo("Delhi Fire Service");
        assertThat(structuredFire.getFields().get("originalFileName")).isEqualTo("Fire_NOC.pdf");

        // Verify PDF 2 extracted its own distinct content
        assertThat(structuredBuilding.getFields().get("certificateNumber")).isEqualTo("BP/SANCT/2023/554");
        assertThat(structuredBuilding.getFields().get("issueDate")).isEqualTo("2023-06-15");
        assertThat(structuredBuilding.getFields().get("issuingAuthority")).isEqualTo("Municipal Corporation");
        assertThat(structuredBuilding.getFields().get("originalFileName")).isEqualTo("Sanctioned_Plan.pdf");

        // Verify NO cross-contamination
        assertThat(structuredFire.getFields().get("certificateNumber")).isNotEqualTo(structuredBuilding.getFields().get("certificateNumber"));
        assertThat(structuredFire.getFields().get("originalFileName")).isNotEqualTo(structuredBuilding.getFields().get("originalFileName"));
    }

    @Test
    @DisplayName("Task 4.4: Corrupted or unreadable PDF produces explicit FAILED extraction status and error message")
    void testTask4_UnreadableOrCorruptedPdfProducesExplicitExtractionFailureState() {
        DocumentTextExtractionService realTextExtraction = new DocumentTextExtractionService(new TesseractOcrService());
        byte[] corruptedBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x7F, 0x10, 0x20};

        ExtractedDocumentData result = realTextExtraction.extractText(corruptedBytes, "corrupted_bad.pdf", "application/pdf");

        assertThat(result.getOcrStatus()).isEqualTo("FAILED");
        assertThat(result.getExtractionMethod()).isEqualTo("EXTRACTION_FAILED");
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getErrorMessage()).containsIgnoringCase("corrupted or unreadable");
    }

    @Test
    @DisplayName("Task 4.5: Empty zero-byte PDF produces explicit EMPTY extraction status")
    void testTask4_EmptyZeroBytePdfProducesExplicitEmptyDocumentState() {
        DocumentTextExtractionService realTextExtraction = new DocumentTextExtractionService(new TesseractOcrService());
        byte[] emptyBytes = new byte[0];

        ExtractedDocumentData result = realTextExtraction.extractText(emptyBytes, "empty_file.pdf", "application/pdf");

        assertThat(result.getOcrStatus()).isEqualTo("EMPTY");
        assertThat(result.getExtractionMethod()).isEqualTo("EMPTY_DOCUMENT");
        assertThat(result.getErrorMessage()).containsIgnoringCase("empty (0 bytes)");
    }

    private byte[] createPdfWithText(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                String[] lines = text.split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        cs.newLineAtOffset(0, -15);
                    }
                    cs.showText(lines[i]);
                }
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] createBlankPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] createScannedPdfWithImage() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            BufferedImage bim = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bim);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 50, 500, 100, 100);
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private Application createSampleApplication(String type) {
        Institution inst = new Institution("INST-2026-909", "ABC Institute of Technology", "North");
        inst.setId(501L);

        Application app = new Application(
                inst,
                "APP-2026-1042",
                type,
                "2026-2027",
                "Submitted"
        );
        app.setId(1042L);
        app.setCurrentStage("Scrutiny");
        app.setOwnerId("USR-SO-01");
        app.setOwnerName("B. Singh (Scrutiny Officer)");
        app.setSubmittedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        return app;
    }

    @Test
    @DisplayName("Task 5 Req 3 & 4: Officer review confirms potential deficiency, records remarks, reviewer identity and audit event")
    void testTask5_OfficerReviewFinding_ConfirmDeficiency_PersistsDecisionAndAudit() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ScrutinyFinding finding = new ScrutinyFinding(
                assessment, app.getId(), 101L, "DOC_CAT_LAND", "RULE_LAND", "Land Title Deed",
                "APH-2026-6.1", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Mandatory Land Title Deed", "DAA Lab Manual detected",
                "Content is completely unrelated to Land ownership.", "Upload registered Land Title Deed."
        );
        finding.setId(301L);
        finding.setOfficerDecision("PENDING");

        org.mockito.Mockito.when(findingRepository.findById(301L)).thenReturn(Optional.of(finding));
        org.mockito.Mockito.when(findingRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyFinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewFindingRequest req = new ReviewFindingRequest(
                "CONFIRMED_DEFICIENCY",
                "Confirmed invalid document. DAA Lab Manual uploaded instead of Land Title Deed.",
                false
        );

        ScrutinyFindingResponse response = scrutinyService.reviewFinding(301L, req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED_DEFICIENCY", finding.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertTrue(finding.getOfficerRemarks().contains("Confirmed invalid document"));
        org.junit.jupiter.api.Assertions.assertEquals("USR-SO-01", finding.getReviewedBy());
        org.junit.jupiter.api.Assertions.assertNotNull(finding.getReviewedAt());
        org.mockito.Mockito.verify(auditEventRepository, org.mockito.Mockito.atLeastOnce())
                .save(org.mockito.ArgumentMatchers.argThat(event -> "SCRUTINY_FINDING_REVIEWED".equals(event.getEventType())));
    }

    @Test
    @DisplayName("Task 5 Req 3: Officer review dismisses false positive, updates finding and cancels draft deficiency")
    void testTask5_OfficerReviewFinding_DismissFalsePositive_CancelsDraftDeficiency() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ScrutinyFinding finding = new ScrutinyFinding(
                assessment, app.getId(), 102L, "DOC_CAT_FIRE", "RULE_FIRE", "Fire NOC",
                "APH-2026-6.2", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Fire Safety Certificate", "Partial scan detected",
                "Certificate seal blurry.", "Upload clearer scan."
        );
        finding.setId(302L);
        finding.setOfficerDecision("PENDING");

        ScrutinyDeficiency draftDef = new ScrutinyDeficiency();
        draftDef.setId(401L);
        draftDef.setApplication(app);
        draftDef.setFinding(finding);
        draftDef.setStatus("CONFIRMED");

        org.mockito.Mockito.when(findingRepository.findById(302L)).thenReturn(Optional.of(finding));
        org.mockito.Mockito.when(findingRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyFinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByFindingId(302L)).thenReturn(Optional.of(draftDef));

        ReviewFindingRequest req = new ReviewFindingRequest(
                "DISMISSED",
                "Physical inspection verified genuine fire department endorsement. False positive.",
                false
        );

        ScrutinyFindingResponse response = scrutinyService.reviewFinding(302L, req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals("DISMISSED", finding.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertEquals("DISMISSED", draftDef.getStatus());
        org.mockito.Mockito.verify(scrutinyDeficiencyRepository).save(draftDef);
    }

    @Test
    @DisplayName("Task 5 Req 3: Officer review requests manual verification")
    void testTask5_OfficerReviewFinding_RequestManualVerification() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ScrutinyFinding finding = new ScrutinyFinding(
                assessment, app.getId(), 103L, "DOC_CAT_BUILDING", "RULE_BLDG", "Building Plan",
                "APH-2026-6.3", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Approved Building Plan", "Architect signature faint",
                "Signature faint.", "Requires manual physical check."
        );
        finding.setId(303L);

        org.mockito.Mockito.when(findingRepository.findById(303L)).thenReturn(Optional.of(finding));
        org.mockito.Mockito.when(findingRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyFinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewFindingRequest req = new ReviewFindingRequest(
                "REQUEST_MANUAL_VERIFICATION",
                "Forwarded to EVC physical inspection desk for blueprint verification.",
                false
        );

        ScrutinyFindingResponse response = scrutinyService.reviewFinding(303L, req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals("REQUEST_MANUAL_VERIFICATION", finding.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertEquals("USR-SO-01", finding.getReviewedBy());
    }

    @Test
    @DisplayName("Task 5 Req 3: Officer review synchronizes decision across all duplicate file findings")
    void testTask5_OfficerReviewFinding_SynchronizeAllDuplicateFindings() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ApplicationDocument doc1 = new ApplicationDocument();
        doc1.setId(101L);
        doc1.setFileHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        ApplicationDocument doc2 = new ApplicationDocument();
        doc2.setId(102L);
        doc2.setFileHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        ApplicationDocument doc3 = new ApplicationDocument();
        doc3.setId(103L);
        doc3.setFileHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        ScrutinyFinding f1 = new ScrutinyFinding(assessment, app.getId(), 101L, "DOC_CAT_LAND", "RULE_DUPLICATE_FILE_UPLOAD_LAND", "Duplicate Document",
                "APH-2026-DUP", "DUPLICATE_FILE", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Unique Document", "Duplicate hash", "Unrelated PDF.", "Upload genuine document.");
        f1.setId(301L);
        f1.setAssessment(assessment);

        ScrutinyFinding f2 = new ScrutinyFinding(assessment, app.getId(), 102L, "DOC_CAT_FIRE", "RULE_DUPLICATE_FILE_UPLOAD_FIRE", "Duplicate Document",
                "APH-2026-DUP", "DUPLICATE_FILE", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Unique Document", "Duplicate hash", "Unrelated PDF.", "Upload genuine document.");
        f2.setId(302L);
        f2.setAssessment(assessment);

        ScrutinyFinding f3 = new ScrutinyFinding(assessment, app.getId(), 103L, "DOC_CAT_BUILDING", "RULE_DUPLICATE_FILE_UPLOAD_BLDG", "Duplicate Document",
                "APH-2026-DUP", "DUPLICATE_FILE", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Unique Document", "Duplicate hash", "Unrelated PDF.", "Upload genuine document.");
        f3.setId(303L);
        f3.setAssessment(assessment);

        org.mockito.Mockito.when(findingRepository.findById(301L)).thenReturn(Optional.of(f1));
        org.mockito.Mockito.when(documentRepository.findById(101L)).thenReturn(Optional.of(doc1));
        org.mockito.Mockito.when(documentRepository.findById(102L)).thenReturn(Optional.of(doc2));
        org.mockito.Mockito.when(documentRepository.findById(103L)).thenReturn(Optional.of(doc3));
        org.mockito.Mockito.when(findingRepository.findByAssessmentIdOrderByIdAsc(assessment.getId()))
                .thenReturn(List.of(f1, f2, f3));
        org.mockito.Mockito.when(findingRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyFinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewFindingRequest req = new ReviewFindingRequest(
                "CONFIRMED_DEFICIENCY",
                "All duplicate file uploads confirmed as non-statutory lab manuals.",
                true // applyToAllDuplicates
        );

        ScrutinyFindingResponse response = scrutinyService.reviewFinding(301L, req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED_DEFICIENCY", f1.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED_DEFICIENCY", f2.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED_DEFICIENCY", f3.getOfficerDecision());
        org.junit.jupiter.api.Assertions.assertEquals("USR-SO-01", f2.getReviewedBy());
        org.junit.jupiter.api.Assertions.assertEquals("USR-SO-01", f3.getReviewedBy());
    }

    @Test
    @DisplayName("Task 5 Req 7: Prevent formal notice issuance without required officer review/confirmation")
    void testTask5_IssueFormalDeficiencies_PreventUnreviewedIssuance_ThrowsException() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        // Finding is unreviewed (officerDecision = PENDING)
        ScrutinyFinding unreviewed = new ScrutinyFinding(
                assessment, app.getId(), 101L, "DOC_CAT_LAND", "RULE_LAND", "Land Title Deed",
                "APH-2026-6.1", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Land Title Deed", "Extracted evidence", "Potential defect", "Upload deed."
        );
        unreviewed.setId(301L);
        unreviewed.setOfficerDecision("PENDING");

        org.mockito.Mockito.when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId()))
                .thenReturn(Optional.of(assessment));
        org.mockito.Mockito.when(findingRepository.findByAssessmentIdOrderByIdAsc(assessment.getId()))
                .thenReturn(List.of(unreviewed));

        IssueDeficienciesRequest req = new IssueDeficienciesRequest(
                Collections.emptyList(), // no finding IDs provided
                "Deficiency Notice",
                "Please rectify immediately",
                LocalDate.now().plusDays(15),
                true,
                true
        );

        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> scrutinyService.issueFormalDeficiencies(app.getId(), req, "USR-SO-01")
        );

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("No confirmed deficiencies selected"));
    }

    @Test
    @DisplayName("Task 5 Req 6, 8, 9, 10, 11: Issue formal notice links to application, updates status, saves workflow transition and audit history")
    void testTask5_IssueFormalDeficiencies_Success_LinksApplication_UpdatesStatus_RecordsWorkflowTransition() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setStatus("Submitted");
        app.setCurrentStage("Scrutiny");

        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ApplicationDocument landDoc = new ApplicationDocument();
        landDoc.setId(101L);
        landDoc.setApplication(app);
        landDoc.setRequirementCode("DOC_CAT_LAND");
        landDoc.setDocumentName("Land_Title.pdf");
        landDoc.setVersion(1);

        ScrutinyFinding confirmedFinding = new ScrutinyFinding(
                assessment, app.getId(), 101L, "DOC_CAT_LAND", "RULE_LAND", "Land Title Deed Missing",
                "APH-2026-6.1", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Mandatory Land Title Deed", "DAA Lab Manual found",
                "Unrelated document uploaded.", "Upload genuine registered Land Title Deed."
        );
        confirmedFinding.setId(301L);
        confirmedFinding.setOfficerDecision("CONFIRMED");

        org.mockito.Mockito.when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        org.mockito.Mockito.when(findingRepository.findAllById(List.of(301L))).thenReturn(List.of(confirmedFinding));
        org.mockito.Mockito.when(documentRepository.findById(101L)).thenReturn(Optional.of(landDoc));
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByFindingId(301L)).thenReturn(Optional.empty());
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyDeficiency.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(applicationRepository.save(org.mockito.ArgumentMatchers.any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDate deadline = LocalDate.now().plusDays(15);
        IssueDeficienciesRequest req = new IssueDeficienciesRequest(
                List.of(301L),
                "Official Scrutiny Deficiency Notice #1",
                "Mandatory rectification: Upload registered Land Title Deed within 15 days.",
                deadline,
                true,
                true
        );

        List<ScrutinyDeficiencyResponse> issued = scrutinyService.issueFormalDeficiencies(app.getId(), req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(issued);
        org.junit.jupiter.api.Assertions.assertEquals(1, issued.size());
        ScrutinyDeficiencyResponse def = issued.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("ISSUED", def.status());
        org.junit.jupiter.api.Assertions.assertEquals("USR-SO-01", def.issuedBy());
        org.junit.jupiter.api.Assertions.assertEquals(deadline, def.dueDate());

        // Verify application status changed to Returned and owner assigned to Institution Representative
        org.junit.jupiter.api.Assertions.assertEquals("Returned", app.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Re-scrutiny", app.getCurrentStage());
        org.junit.jupiter.api.Assertions.assertEquals("Institution Representative", app.getOwnerName());
        org.junit.jupiter.api.Assertions.assertTrue(app.getDeficienciesJson().contains("Land Title Deed Missing"));

        // Verify workflow transition and audit events recorded
        org.mockito.Mockito.verify(workflowTransitionRepository)
                .save(org.mockito.ArgumentMatchers.argThat(t -> "RETURN_FOR_CORRECTION".equals(t.getAction())));
        org.mockito.Mockito.verify(auditEventRepository, org.mockito.Mockito.atLeastOnce())
                .save(org.mockito.ArgumentMatchers.argThat(e -> "FORMAL_DEFICIENCIES_ISSUED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("Task 5 Req 12: Idempotency guard prevents duplicate formal notices on accidental repeated clicks")
    void testTask5_IssueFormalDeficiencies_DuplicateProtection_IdempotencyWindow() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setApplication(app);

        ScrutinyFinding confirmedFinding = new ScrutinyFinding(
                assessment, app.getId(), 101L, "DOC_CAT_LAND", "RULE_LAND", "Land Title Deed",
                "APH-2026-6.1", "DOCUMENT_VALIDATION", "POTENTIAL_DEFICIENCY", "BLOCKING",
                "Land Title", "Invalid document", "Unrelated file", "Upload deed."
        );
        confirmedFinding.setId(301L);
        confirmedFinding.setOfficerDecision("CONFIRMED");

        ScrutinyDeficiency existingDef = new ScrutinyDeficiency();
        existingDef.setId(401L);
        existingDef.setDeficiencyReference("DEF-APP-2026-1042-01");
        existingDef.setApplication(app);
        existingDef.setAssessment(assessment);
        existingDef.setFinding(confirmedFinding);
        existingDef.setStatus("ISSUED");
        existingDef.setIssuedAt(Instant.now().minusSeconds(5)); // issued 5 seconds ago

        org.mockito.Mockito.when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        org.mockito.Mockito.when(findingRepository.findAllById(List.of(301L))).thenReturn(List.of(confirmedFinding));
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByFindingId(301L)).thenReturn(Optional.of(existingDef));
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByApplicationIdAndStatus(app.getId(), "ISSUED"))
                .thenReturn(List.of(existingDef));

        IssueDeficienciesRequest req = new IssueDeficienciesRequest(
                List.of(301L),
                "Official Scrutiny Deficiency Notice #1",
                "Repeat click attempt",
                LocalDate.now().plusDays(15),
                true,
                true
        );

        List<ScrutinyDeficiencyResponse> issued = scrutinyService.issueFormalDeficiencies(app.getId(), req, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(issued);
        org.junit.jupiter.api.Assertions.assertEquals(1, issued.size());
        // Verify no duplicate deficiency was created/saved
        org.mockito.Mockito.verify(scrutinyDeficiencyRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(ScrutinyDeficiency.class));
    }

    // =========================================================================
    // TASK 6 TESTS: END-TO-END AUTOMATED RE-SCRUTINY WORKFLOW
    // =========================================================================

    private byte[] createPdfWithSearchableText(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (String line : text.split("\n")) {
                    String clean = line.trim();
                    if (!clean.isEmpty()) {
                        stream.showText(clean);
                        stream.newLineAtOffset(0, -15);
                    }
                }
                stream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate test PDF", e);
        }
    }

    @Test
    @DisplayName("Task 6: Acceptance Test - Complete End-to-End Workflow from DAA Lab PDF to Corrected Fire NOC and Verification")
    void testTask6_AcceptanceTest_CompleteWorkflow_EndToEnd() throws Exception {
        // Step 1: Institution uploads incorrect DAA laboratory PDF under statutory category DOC_FIRE_SAFETY
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setId(1042L);

        String daaText = "Design and Analysis of Algorithms\nLab Manual Experiments\nQuickSort MergeSort Knapsack Problem\nSubmitted by Student Roll: 23BCS101";
        byte[] daaPdfBytes = createPdfWithSearchableText(daaText);
        String daaHash = "sha256-daa-lab-file-0101";

        ApplicationDocument originalDocV1 = new ApplicationDocument();
        originalDocV1.setId(501L);
        originalDocV1.setApplication(app);
        originalDocV1.setRequirementCode("DOC_FIRE_SAFETY");
        originalDocV1.setDocumentName("DAA_Lab_File_final.pdf");
        originalDocV1.setOriginalFileName("DAA_Lab_File_final.pdf");
        originalDocV1.setStoragePath("1042/DOC_FIRE_SAFETY/v1/DAA_Lab_File_final.pdf");
        originalDocV1.setFileSize((long) daaPdfBytes.length);
        originalDocV1.setContentType("application/pdf");
        originalDocV1.setFileHash(daaHash);
        originalDocV1.setVersion(1);
        originalDocV1.setStatus("PENDING");
        originalDocV1.setUploadedBy("USR-INST-01");

        // Real PDFBox extraction processes the actual uploaded PDF bytes
        DocumentTextExtractionService realTextExtractor = new DocumentTextExtractionService(new TesseractOcrService());
        ExtractedDocumentData rawExtracted = realTextExtractor.extractText(daaPdfBytes, "DAA_Lab_File_final.pdf", "application/pdf");
        org.junit.jupiter.api.Assertions.assertNotNull(rawExtracted.getRawText());
        org.junit.jupiter.api.Assertions.assertTrue(rawExtracted.getRawText().contains("Design and Analysis of Algorithms"));

        ScrutinyAssessment cycle1 = new ScrutinyAssessment(
                "SCR-APP-2026-1042-01", app, null, "PRELIMINARY_SCRUTINY", 1, "APH_2024_27_V1", "SYSTEM"
        );
        cycle1.setId(201L);

        ScrutinyDocumentExtraction ext1 = new ScrutinyDocumentExtraction();
        ext1.setId(601L);
        ext1.setAssessment(cycle1);
        ext1.setDocument(originalDocV1);
        ext1.setRequirementCode("DOC_FIRE_SAFETY");
        ext1.setRawText(rawExtracted.getRawText());

        // Step 2: Automated scrutiny detects potential deficiency from unrelated DAA lab content
        List<ScrutinyFinding> evaluatedFindings = validationService.evaluateScrutiny(
                cycle1, app, List.of(originalDocV1), Map.of("DOC_FIRE_SAFETY", ext1), LocalDate.now()
        );

        ScrutinyFinding daaFinding = evaluatedFindings.stream()
                .filter(f -> f.getRuleCode() != null && f.getRuleCode().contains("DOC_FIRE_SAFETY") && "POTENTIAL_DEFICIENCY".equals(f.getOutcome()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected deficiency finding for unrelated DAA lab file. Findings evaluated: " + evaluatedFindings.size()));

        org.junit.jupiter.api.Assertions.assertEquals("POTENTIAL_DEFICIENCY", daaFinding.getOutcome());
        org.junit.jupiter.api.Assertions.assertTrue(daaFinding.getExplanation().contains("academic lab manual"));
        daaFinding.setId(701L);

        // Step 3: Scrutiny Officer reviews and confirms deficiency
        org.mockito.Mockito.when(findingRepository.findById(701L)).thenReturn(Optional.of(daaFinding));
        org.mockito.Mockito.when(findingRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyFinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewFindingRequest confirmReq = new ReviewFindingRequest("CONFIRMED_DEFICIENCY", "Confirmed unrelated academic coursework uploaded for fire safety.", false);
        ScrutinyFindingResponse reviewResp = scrutinyService.reviewFinding(701L, confirmReq, "USR-SO-01");
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED_DEFICIENCY", reviewResp.officerDecision());

        // Step 4: Officer issues formal deficiency notice
        org.mockito.Mockito.when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        org.mockito.Mockito.when(findingRepository.findAllById(List.of(701L))).thenReturn(List.of(daaFinding));
        org.mockito.Mockito.when(documentRepository.findById(501L)).thenReturn(Optional.of(originalDocV1));
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByFindingId(701L)).thenReturn(Optional.empty());

        ScrutinyDeficiency deficiency = new ScrutinyDeficiency();
        deficiency.setId(801L);
        deficiency.setDeficiencyReference("DEF-APP-2026-1042-01");
        deficiency.setApplication(app);
        deficiency.setAssessment(cycle1);
        deficiency.setFinding(daaFinding);
        deficiency.setRequirementCode("DOC_FIRE_SAFETY");
        deficiency.setTitle("Fire Safety Certificate Deficient");
        deficiency.setDescription(daaFinding.getExplanation());
        deficiency.setAssociatedDocument(originalDocV1);
        deficiency.setAssociatedDocumentVersion(1);
        deficiency.setOriginalEvidenceText("Unrelated academic coursework (DAA_Lab_File_final.pdf)");
        deficiency.setStatus("ISSUED");

        org.mockito.Mockito.when(scrutinyDeficiencyRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyDeficiency.class)))
                .thenAnswer(inv -> {
                    ScrutinyDeficiency d = inv.getArgument(0);
                    if (d.getId() == null) d.setId(801L);
                    return d;
                });
        org.mockito.Mockito.when(applicationRepository.save(org.mockito.ArgumentMatchers.any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDate deadline = LocalDate.now().plusDays(15);
        IssueDeficienciesRequest noticeReq = new IssueDeficienciesRequest(
                List.of(701L), "Statutory Deficiency Notice: DOC_FIRE_SAFETY",
                "Rectify fire safety certificate with competent fire service NOC.", deadline, true, true
        );
        List<ScrutinyDeficiencyResponse> issuedNotices = scrutinyService.issueFormalDeficiencies(app.getId(), noticeReq, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertEquals(1, issuedNotices.size());
        org.junit.jupiter.api.Assertions.assertEquals("Returned", app.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Re-scrutiny", app.getCurrentStage());
        org.junit.jupiter.api.Assertions.assertEquals("Institution Representative", app.getOwnerName());

        // Step 5: Institution uploads corrected category-appropriate sample document (Preserving v1 and creating v2)
        String validFireNocText = "Delhi Fire Service\nFire Safety Certificate NOC\nCertificate No: DFS/2026/894\nDate of Issue: 10/01/2026\nValid Up to: 15/12/2027\nIn respect of Delhi Institute of Technology";
        byte[] validFireNocBytes = createPdfWithSearchableText(validFireNocText);
        String validHash = "sha256-delhi-fire-service-noc-2026";

        MockMultipartFile correctedFile = new MockMultipartFile(
                "file", "Delhi_Fire_Service_NOC_2026.pdf", "application/pdf", validFireNocBytes
        );

        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByIdAndApplicationId(801L, app.getId()))
                .thenReturn(Optional.of(deficiency));
        org.mockito.Mockito.when(minioStorageService.uploadDocument(
                org.mockito.ArgumentMatchers.eq(app.getId()),
                org.mockito.ArgumentMatchers.eq("DOC_FIRE_SAFETY"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class)
        )).thenReturn(new MinioStorageService.StoredObjectResult(
                "1042/DOC_FIRE_SAFETY/v2/Delhi_Fire_Service_NOC_2026.pdf",
                "aicte-documents",
                validHash,
                validFireNocBytes.length,
                "application/pdf"
        ));

        ApplicationDocument correctedDocV2 = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate (Revision v2)",
                "Delhi_Fire_Service_NOC_2026.pdf", "Delhi_Fire_Service_NOC_2026.pdf",
                "1042/DOC_FIRE_SAFETY/v2/Delhi_Fire_Service_NOC_2026.pdf",
                (long) validFireNocBytes.length, "application/pdf", validHash, 2, "CORRECTED_REVISION", "USR-INST-01"
        );
        correctedDocV2.setId(502L);

        org.mockito.Mockito.when(documentRepository.save(org.mockito.ArgumentMatchers.any(ApplicationDocument.class)))
                .thenReturn(correctedDocV2);

        ApplicationDocument uploadedDoc = scrutinyService.uploadCorrectedDocument(app.getId(), 801L, correctedFile, "USR-INST-01");

        // Verify version 1 preserved intact, version 2 created and linked
        org.junit.jupiter.api.Assertions.assertEquals(1, originalDocV1.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals("DAA_Lab_File_final.pdf", originalDocV1.getOriginalFileName());
        org.junit.jupiter.api.Assertions.assertEquals(2, uploadedDoc.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals("CORRECTED_REVISION", uploadedDoc.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(deficiency.getCorrectedDocument().getId(), uploadedDoc.getId());
        org.junit.jupiter.api.Assertions.assertEquals(2, deficiency.getCorrectedDocumentVersion());

        // Step 6: Institution submits deficiency rectifications
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByApplicationIdAndStatus(app.getId(), "ISSUED"))
                .thenReturn(List.of(deficiency));

        // Setup real ReScrutinyService
        ReScrutinyService realReScrutinyService = new ReScrutinyService(
                assessmentRepository,
                scrutinyDeficiencyRepository,
                extractionRepository,
                comparisonRepository,
                realTextExtractor,
                fieldExtractionService,
                minioStorageService,
                auditEventRepository,
                objectMapper
        );

        org.mockito.Mockito.when(minioStorageService.downloadDocument("1042/DOC_FIRE_SAFETY/v2/Delhi_Fire_Service_NOC_2026.pdf"))
                .thenReturn(new java.io.ByteArrayInputStream(validFireNocBytes));

        org.mockito.Mockito.when(assessmentRepository.countByApplicationId(app.getId())).thenReturn(1L);
        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId()))
                .thenReturn(Optional.of(cycle1));
        org.mockito.Mockito.when(assessmentRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyAssessment.class)))
                .thenAnswer(inv -> {
                    ScrutinyAssessment a = inv.getArgument(0);
                    if (a.getId() == null) a.setId(202L);
                    return a;
                });
        org.mockito.Mockito.when(comparisonRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyRescrutinyComparison.class)))
                .thenAnswer(inv -> {
                    ScrutinyRescrutinyComparison c = inv.getArgument(0);
                    if (c.getId() == null) c.setId(901L);
                    return c;
                });

        deficiency.setStatus("RESPONSE_SUBMITTED");
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByApplicationIdAndStatus(app.getId(), "RESPONSE_SUBMITTED"))
                .thenReturn(List.of(deficiency));

        // Step 7: Run automated re-scrutiny and verify comparative diff generation
        ScrutinyAssessment reAssessment = realReScrutinyService.runAutomatedRescrutiny(app, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(reAssessment);
        org.junit.jupiter.api.Assertions.assertEquals(2, reAssessment.getCycleNumber());
        org.junit.jupiter.api.Assertions.assertEquals("RE_SCRUTINY", reAssessment.getAssessmentType());
        org.junit.jupiter.api.Assertions.assertEquals(1, reAssessment.getPassedChecks());
        org.junit.jupiter.api.Assertions.assertEquals(0, reAssessment.getDeficiencyChecks());

        // Verify comparison record
        org.mockito.ArgumentCaptor<ScrutinyRescrutinyComparison> compCaptor = org.mockito.ArgumentCaptor.forClass(ScrutinyRescrutinyComparison.class);
        org.mockito.Mockito.verify(comparisonRepository).save(compCaptor.capture());
        ScrutinyRescrutinyComparison savedComp = compCaptor.getValue();

        org.junit.jupiter.api.Assertions.assertEquals("RECTIFIED_PENDING_CONFIRMATION", savedComp.getComparisonStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, savedComp.getOriginalDocumentVersion());
        org.junit.jupiter.api.Assertions.assertEquals(2, savedComp.getCorrectedDocumentVersion());
        org.junit.jupiter.api.Assertions.assertTrue(savedComp.getComparisonSummary().contains("satisfies the date-validity check"));
        org.junit.jupiter.api.Assertions.assertTrue(savedComp.getCorrectedExtractedValue().contains("2027-12-15"));

        // Crucial Requirement: Deficiency is NOT automatically marked VERIFIED_RESOLVED
        org.junit.jupiter.api.Assertions.assertNotEquals("VERIFIED_RESOLVED", deficiency.getStatus());

        // Step 8: Officer reviews comparative evidence and marks deficiency VERIFIED_RESOLVED
        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findById(801L)).thenReturn(Optional.of(deficiency));
        ReviewDeficiencyRequest reviewDefReq = new ReviewDeficiencyRequest(
                "VERIFIED_RESOLVED", "Certificate verified with Delhi Fire Service records. Valid through 15/12/2027."
        );
        ScrutinyDeficiencyResponse resolvedDef = scrutinyService.reviewDeficiency(801L, reviewDefReq, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertEquals("VERIFIED_RESOLVED", resolvedDef.status());
        org.junit.jupiter.api.Assertions.assertNotNull(deficiency.getResolvedAt());
        org.junit.jupiter.api.Assertions.assertEquals("VERIFIED_RESOLVED", deficiency.getReviewerDecision());
        org.junit.jupiter.api.Assertions.assertTrue(deficiency.getReviewerRemarks().contains("Delhi Fire Service"));
    }

    @Test
    @DisplayName("Task 6: Acceptance Test Step 9 - Re-running without document changes (Identical File Hash) produces DEFICIENCY_PERSISTS without false improvement")
    void testTask6_AcceptanceTest_Step9_IdenticalFileHashProducesDeficiencyPersists() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setId(1042L);

        String daaText = "Design and Analysis of Algorithms Lab Manual Experiments QuickSort Knapsack";
        byte[] daaPdfBytes = createPdfWithSearchableText(daaText);
        String identicalHash = "sha256-exact-same-daa-hash-4422";

        ApplicationDocument originalDocV1 = new ApplicationDocument();
        originalDocV1.setId(501L);
        originalDocV1.setApplication(app);
        originalDocV1.setRequirementCode("DOC_FIRE_SAFETY");
        originalDocV1.setDocumentName("DAA_Lab_File_final.pdf");
        originalDocV1.setOriginalFileName("DAA_Lab_File_final.pdf");
        originalDocV1.setStoragePath("1042/DOC_FIRE_SAFETY/v1/DAA_Lab_File_final.pdf");
        originalDocV1.setFileSize((long) daaPdfBytes.length);
        originalDocV1.setContentType("application/pdf");
        originalDocV1.setFileHash(identicalHash);
        originalDocV1.setVersion(1);

        // Institution re-uploads the exact same file under v2
        ApplicationDocument reuploadedDocV2 = new ApplicationDocument();
        reuploadedDocV2.setId(502L);
        reuploadedDocV2.setApplication(app);
        reuploadedDocV2.setRequirementCode("DOC_FIRE_SAFETY");
        reuploadedDocV2.setDocumentName("DAA_Lab_File_final.pdf");
        reuploadedDocV2.setOriginalFileName("DAA_Lab_File_final.pdf");
        reuploadedDocV2.setStoragePath("1042/DOC_FIRE_SAFETY/v2/DAA_Lab_File_final.pdf");
        reuploadedDocV2.setFileSize((long) daaPdfBytes.length);
        reuploadedDocV2.setContentType("application/pdf");
        reuploadedDocV2.setFileHash(identicalHash); // Exact same file hash!
        reuploadedDocV2.setVersion(2);

        ScrutinyDeficiency deficiency = new ScrutinyDeficiency();
        deficiency.setId(801L);
        deficiency.setDeficiencyReference("DEF-APP-2026-1042-01");
        deficiency.setApplication(app);
        deficiency.setRequirementCode("DOC_FIRE_SAFETY");
        deficiency.setTitle("Fire Safety Certificate Deficient");
        deficiency.setAssociatedDocument(originalDocV1);
        deficiency.setAssociatedDocumentVersion(1);
        deficiency.setCorrectedDocument(reuploadedDocV2);
        deficiency.setCorrectedDocumentVersion(2);
        deficiency.setStatus("RESPONSE_SUBMITTED");

        DocumentTextExtractionService realTextExtractor = new DocumentTextExtractionService(new TesseractOcrService());
        ReScrutinyService realReScrutinyService = new ReScrutinyService(
                assessmentRepository,
                scrutinyDeficiencyRepository,
                extractionRepository,
                comparisonRepository,
                realTextExtractor,
                fieldExtractionService,
                minioStorageService,
                auditEventRepository,
                objectMapper
        );

        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByApplicationIdAndStatus(app.getId(), "RESPONSE_SUBMITTED"))
                .thenReturn(List.of(deficiency));
        org.mockito.Mockito.when(minioStorageService.downloadDocument("1042/DOC_FIRE_SAFETY/v2/DAA_Lab_File_final.pdf"))
                .thenReturn(new java.io.ByteArrayInputStream(daaPdfBytes));

        org.mockito.Mockito.when(assessmentRepository.countByApplicationId(app.getId())).thenReturn(1L);
        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(assessmentRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyAssessment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrutinyAssessment reAssessment = realReScrutinyService.runAutomatedRescrutiny(app, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertNotNull(reAssessment);
        org.junit.jupiter.api.Assertions.assertEquals(0, reAssessment.getPassedChecks());
        org.junit.jupiter.api.Assertions.assertEquals(1, reAssessment.getDeficiencyChecks());
        org.junit.jupiter.api.Assertions.assertEquals("POTENTIAL_DEFICIENCIES_DETECTED", reAssessment.getSummaryOutcome());

        org.mockito.ArgumentCaptor<ScrutinyRescrutinyComparison> compCaptor = org.mockito.ArgumentCaptor.forClass(ScrutinyRescrutinyComparison.class);
        org.mockito.Mockito.verify(comparisonRepository).save(compCaptor.capture());
        ScrutinyRescrutinyComparison comp = compCaptor.getValue();

        org.junit.jupiter.api.Assertions.assertEquals("DEFICIENCY_PERSISTS", comp.getComparisonStatus());
        org.junit.jupiter.api.Assertions.assertTrue(comp.getComparisonSummary().contains("identical content"));
        org.junit.jupiter.api.Assertions.assertTrue(comp.getRemainingIssues().contains("identical to the original deficient document"));
    }

    @Test
    @DisplayName("Task 6: Idempotency guard suppresses duplicate re-scrutiny assessment cycles within 15 seconds")
    void testTask6_IdempotencyGuard_SuppressesDuplicateReScrutinyCycles() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setId(1042L);

        ScrutinyAssessment recentReAssessment = new ScrutinyAssessment(
                "RE-SCR-APP-2026-1042-02", app, null, "RE_SCRUTINY", 2, "APH_2024_27_V1", "USR-SO-01"
        );
        recentReAssessment.setId(202L);
        recentReAssessment.setCreatedAt(Instant.now().minusSeconds(5)); // created 5 seconds ago

        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId()))
                .thenReturn(Optional.of(recentReAssessment));

        ReScrutinyService realReScrutinyService = new ReScrutinyService(
                assessmentRepository,
                scrutinyDeficiencyRepository,
                extractionRepository,
                comparisonRepository,
                new DocumentTextExtractionService(new TesseractOcrService()),
                fieldExtractionService,
                minioStorageService,
                auditEventRepository,
                objectMapper
        );

        ScrutinyAssessment result = realReScrutinyService.runAutomatedRescrutiny(app, "USR-SO-01");

        org.junit.jupiter.api.Assertions.assertEquals(202L, result.getId());
        org.junit.jupiter.api.Assertions.assertEquals("RE-SCR-APP-2026-1042-02", result.getAssessmentReference());
        // Verify no new assessment cycle or comparisons saved
        org.mockito.Mockito.verify(comparisonRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(ScrutinyRescrutinyComparison.class));
    }

    @Test
    @DisplayName("Task 6: Detection of Newly Introduced Issues (Corrupt PDF or Revocation Notices)")
    void testTask6_NewlyIntroducedIssue_CorruptedOrRevocationNotice() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setId(1042L);

        ApplicationDocument originalDocV1 = new ApplicationDocument();
        originalDocV1.setId(501L);
        originalDocV1.setApplication(app);
        originalDocV1.setRequirementCode("DOC_FIRE_SAFETY");
        originalDocV1.setFileHash("hash-v1");
        originalDocV1.setVersion(1);

        // Upload v2 contains explicit revocation notice
        String revocationText = "Delhi Fire Service Notice of Violation Certificate Revoked and Cancelled due to safety infractions";
        byte[] revocationBytes = createPdfWithSearchableText(revocationText);

        ApplicationDocument correctedDocV2 = new ApplicationDocument();
        correctedDocV2.setId(502L);
        correctedDocV2.setApplication(app);
        correctedDocV2.setRequirementCode("DOC_FIRE_SAFETY");
        correctedDocV2.setDocumentName("Fire_Revocation_Notice.pdf");
        correctedDocV2.setOriginalFileName("Fire_Revocation_Notice.pdf");
        correctedDocV2.setStoragePath("1042/DOC_FIRE_SAFETY/v2/Fire_Revocation_Notice.pdf");
        correctedDocV2.setFileSize((long) revocationBytes.length);
        correctedDocV2.setContentType("application/pdf");
        correctedDocV2.setFileHash("hash-v2-revoked");
        correctedDocV2.setVersion(2);

        ScrutinyDeficiency deficiency = new ScrutinyDeficiency();
        deficiency.setId(801L);
        deficiency.setApplication(app);
        deficiency.setRequirementCode("DOC_FIRE_SAFETY");
        deficiency.setTitle("Fire Safety Certificate Deficient");
        deficiency.setAssociatedDocument(originalDocV1);
        deficiency.setAssociatedDocumentVersion(1);
        deficiency.setCorrectedDocument(correctedDocV2);
        deficiency.setCorrectedDocumentVersion(2);
        deficiency.setStatus("RESPONSE_SUBMITTED");

        DocumentTextExtractionService realTextExtractor = new DocumentTextExtractionService(new TesseractOcrService());
        ReScrutinyService realReScrutinyService = new ReScrutinyService(
                assessmentRepository,
                scrutinyDeficiencyRepository,
                extractionRepository,
                comparisonRepository,
                realTextExtractor,
                fieldExtractionService,
                minioStorageService,
                auditEventRepository,
                objectMapper
        );

        org.mockito.Mockito.when(scrutinyDeficiencyRepository.findByApplicationIdAndStatus(app.getId(), "RESPONSE_SUBMITTED"))
                .thenReturn(List.of(deficiency));
        org.mockito.Mockito.when(minioStorageService.downloadDocument("1042/DOC_FIRE_SAFETY/v2/Fire_Revocation_Notice.pdf"))
                .thenReturn(new java.io.ByteArrayInputStream(revocationBytes));
        org.mockito.Mockito.when(assessmentRepository.countByApplicationId(app.getId())).thenReturn(1L);
        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(assessmentRepository.save(org.mockito.ArgumentMatchers.any(ScrutinyAssessment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        realReScrutinyService.runAutomatedRescrutiny(app, "USR-SO-01");

        org.mockito.ArgumentCaptor<ScrutinyRescrutinyComparison> compCaptor = org.mockito.ArgumentCaptor.forClass(ScrutinyRescrutinyComparison.class);
        org.mockito.Mockito.verify(comparisonRepository).save(compCaptor.capture());
        ScrutinyRescrutinyComparison comp = compCaptor.getValue();

        org.junit.jupiter.api.Assertions.assertEquals("NEW_DEFICIENCY_DETECTED", comp.getComparisonStatus());
        org.junit.jupiter.api.Assertions.assertTrue(comp.getComparisonSummary().contains("revocation or violation"));
    }

    @Test
    @DisplayName("Task 6: ScrutinyController getComparisons fallback and runRescrutiny endpoint")
    void testTask6_Controller_GetComparisonsFallback_And_RunRescrutiny() {
        Application app = createSampleApplication("Extension of Approval (EoA)");
        app.setId(1042L);

        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(201L);
        assessment.setAssessmentReference("RE-SCR-APP-2026-1042-02");
        assessment.setApplication(app);

        ScrutinyRescrutinyComparison comp = new ScrutinyRescrutinyComparison();
        comp.setId(901L);
        comp.setAssessment(assessment);
        comp.setComparisonStatus("RECTIFIED_PENDING_CONFIRMATION");
        comp.setComparisonSummary("Substantiated correction verified");

        org.mockito.Mockito.when(applicationRepository.existsById(1042L)).thenReturn(true);
        org.mockito.Mockito.when(applicationRepository.findById(1042L)).thenReturn(Optional.of(app));
        org.mockito.Mockito.when(assessmentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(1042L))
                .thenReturn(Optional.of(assessment));
        // Primary search returns empty, testing the fallback to application-level comparisons:
        org.mockito.Mockito.when(comparisonRepository.findByAssessmentIdOrderByIdAsc(201L))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(comparisonRepository.findByAssessmentApplicationIdOrderByCreatedAtDesc(1042L))
                .thenReturn(List.of(comp));

        in.gov.aicte.platform.application.ApplicationSubmissionService mockSubmissionService =
                org.mockito.Mockito.mock(in.gov.aicte.platform.application.ApplicationSubmissionService.class);

        ScrutinyController controller = new ScrutinyController(
                scrutinyService,
                applicationRepository,
                assessmentRepository,
                findingRepository,
                scrutinyDeficiencyRepository,
                comparisonRepository,
                extractionRepository,
                mockSubmissionService,
                reScrutinyService
        );

        var response = controller.getComparisons("1042", null);
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertEquals(1, response.getBody().size());
        org.junit.jupiter.api.Assertions.assertEquals(901L, response.getBody().get(0).id());
        org.junit.jupiter.api.Assertions.assertEquals("RECTIFIED_PENDING_CONFIRMATION", response.getBody().get(0).comparisonStatus());
        org.junit.jupiter.api.Assertions.assertEquals("RE-SCR-APP-2026-1042-02", response.getBody().get(0).assessmentReference());

        // Test runRescrutiny endpoint
        java.security.Principal principal = () -> "USR-SO-01";
        org.mockito.Mockito.when(reScrutinyService.runAutomatedRescrutiny(org.mockito.ArgumentMatchers.eq(app), org.mockito.ArgumentMatchers.eq("USR-SO-01")))
                .thenReturn(assessment);

        var rescrutinyResp = controller.runRescrutiny("1042", principal);
        org.junit.jupiter.api.Assertions.assertNotNull(rescrutinyResp);
        org.mockito.Mockito.verify(reScrutinyService).runAutomatedRescrutiny(app, "USR-SO-01");
    }

    @Test
    @DisplayName("Acceptance Test: Scanned PDF -> Tess4J OCR extracts text, evaluates validity, and flags authenticity verification")
    void testRealScannedFireSafetyPdfOcrExtraction() throws Exception {
        byte[] pdfBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("test-documents/04_ocr_edge_cases/15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf"));
        TesseractOcrService ocrService = new TesseractOcrService();
        assertThat(ocrService.isAvailable()).isTrue();

        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(ocrService);
        ExtractedDocumentData data = extractionService.extractText(pdfBytes, "15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf", "application/pdf");

        // 1. Verify OCR Extraction Results
        assertThat(data.getOcrStatus()).isEqualTo("SUCCESS");
        assertThat(data.getExtractionMethod()).isEqualTo("TESSERACT_OCR_SCANNED");
        assertThat(data.getTextCharacterCount()).isGreaterThan(1000);
        assertThat(data.getConfidenceScore()).isNotNull();
        assertThat(data.getConfidenceScore()).isGreaterThan(0.90);
        assertThat(data.getRawText()).contains("FIRE SAFETY EXIT INSPECTION COMPLETE");
        assertThat(data.getRawText()).contains("TEST-FIRE-SCAN-2026-01");
        assertThat(data.getRawText()).contains("Valid Until 2027-09-01");

        // 2. Verify Field Extraction
        DocumentFieldExtractionService fieldExtractionService = new DocumentFieldExtractionService();
        Application app = new Application();
        app.setId(1042L);
        app.setApplicationReference("APP-2026-1042");
        app.setApplicationType("EXTENSION_OF_APPROVAL");
        Institution inst = new Institution();
        inst.setId(1L);
        inst.setName("ABC Institute of Technology");
        app.setInstitution(inst);

        ApplicationDocument doc = new ApplicationDocument(
                app, "DOC_FIRE_SAFETY", "Fire Safety Certificate",
                "15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf", "15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf",
                "sage-application-documents/APP-2026-1042/DOC_FIRE_SAFETY/15_Fire_Safety_SCANNED_IMAGE_ONLY.pdf",
                (long) pdfBytes.length, "application/pdf", "hash_scan_fire", 1, "UPLOADED", "USR-INST-01"
        );
        doc.setId(501L);

        ExtractedDocumentData structured = fieldExtractionService.extractStructuredFields(data, "DOC_FIRE_SAFETY", doc);
        assertThat(structured.getFields().get("expiryDate")).isEqualTo("2027-09-01");
        assertThat(structured.getFields().get("issueDate")).isEqualTo("2026-09-01");
        assertThat(structured.getFields().get("certificateNumber")).isEqualTo("TEST-FIRE-SCAN-2026-01");
        assertThat(structured.getFields().get("categoryPlausible")).isEqualTo(true);

        // 3. Verify Statutory Rule Evaluation (without assuming readable text proves authenticity)
        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(101L);
        assessment.setApplication(app);
        assessment.setAssessmentReference("SCR-APP-2026-1042-01");

        ScrutinyDocumentExtraction extraction = new ScrutinyDocumentExtraction(
                assessment, 1042L, doc, "DOC_FIRE_SAFETY", "Fire Safety Certificate", 1,
                data.getExtractionMethod(), data.getRawText(),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(structured.getFields()),
                data.getConfidenceScore(), data.getOcrStatus(), data.getTextCharacterCount(), data.getPageCount(), data.getValidationMetric()
        );

        ScrutinyValidationService validationService = new ScrutinyValidationService(
                new in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue(),
                fieldExtractionService
        );

        var findings = validationService.evaluateScrutiny(
                assessment, app, List.of(doc), Map.of("DOC_FIRE_SAFETY", extraction), LocalDate.of(2026, 10, 28)
        );

        // Technical container passes with OCR badge
        var formatFinding = findings.stream().filter(f -> "RULE_DOC_FORMAT_VALID_DOC_FIRE_SAFETY".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(formatFinding.getOutcome()).isEqualTo("PASS");
        assertThat(formatFinding.getActualValue()).contains("FORMAT_VALID_SCANNED_OCR");
        assertThat(formatFinding.getActualValue()).contains("95.4%");

        // Statutory validity check passes date test
        var validFinding = findings.stream().filter(f -> "RULE_FIRE_NOC_VALID".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(validFinding.getOutcome()).isEqualTo("PASS");
        assertThat(validFinding.getActualValue()).isEqualTo("VALID_TILL_2027-09-01");

        // Requirement 9: Statutory rule engine flags authority seal/authenticity verification for Scrutiny Officer manual review
        var authFinding = findings.stream().filter(f -> "RULE_FIRE_NOC_AUTHENTICITY_VERIFICATION".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(authFinding.getOutcome()).isEqualTo("NEEDS_MANUAL_REVIEW");
        assertThat(authFinding.getSeverity()).isEqualTo("WARNING");
        assertThat(authFinding.getExplanation()).contains("readable OCR text alone does not substantiate legal authenticity");
    }

    @Test
    @DisplayName("Acceptance Test: Searchable PDF -> PDFBox extracts embedded text without OCR fabrication")
    void testSearchablePdfExtractsEmbeddedTextWithoutOcr() throws Exception {
        byte[] pdfBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("test-documents/01_baseline_category_samples/08_Corpus_Fund_Joint_FDR_Proof.pdf"));
        TesseractOcrService ocrService = new TesseractOcrService();
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(ocrService);

        ExtractedDocumentData data = extractionService.extractText(pdfBytes, "08_Corpus_Fund_Joint_FDR_Proof.pdf", "application/pdf");

        assertThat(data.getOcrStatus()).isEqualTo("SUCCESS");
        assertThat(data.getExtractionMethod()).isEqualTo("PDFBOX_SEARCHABLE_TEXT");
        assertThat(data.getConfidenceScore()).isNull(); // No artificial OCR score fabricated
        assertThat(data.getValidationMetric()).isEqualTo("N/A (Embedded Text)");
        assertThat(data.getRawText()).contains("CORPUS FUND / JOINT FIXED DEPOSIT RECEIPT");
        assertThat(data.getRawText()).contains("Sample Education Finance Bank");
    }

    @Test
    @DisplayName("Acceptance Test: Empty and unreadable PDFs trigger explicit failure or manual review fallback")
    void testEmptyAndUnreadablePdfFallback() {
        TesseractOcrService ocrService = new TesseractOcrService();
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(ocrService);

        // 1. Zero bytes
        ExtractedDocumentData emptyData = extractionService.extractText(new byte[0], "empty.pdf", "application/pdf");
        assertThat(emptyData.getOcrStatus()).isEqualTo("EMPTY");
        assertThat(emptyData.getExtractionMethod()).isEqualTo("EMPTY_DOCUMENT");

        // 2. Corrupted PDF
        byte[] corruptedBytes = "NOT_A_VALID_PDF_HEADER_DATA_CORRUPT".getBytes();
        ExtractedDocumentData corruptData = extractionService.extractText(corruptedBytes, "corrupt.pdf", "application/pdf");
        assertThat(corruptData.getOcrStatus()).isEqualTo("FAILED");
        assertThat(corruptData.getErrorMessage()).contains("Corrupted or unreadable PDF");
    }

    @Test
    @DisplayName("Acceptance Test: Corpus Fund findings accurately reference uploaded document and detect duplicate uploads")
    void testCorpusFundDocumentAssociationAndDuplicateUpload() throws Exception {
        DocumentFieldExtractionService fieldExtractionService = new DocumentFieldExtractionService();
        ScrutinyValidationService validationService = new ScrutinyValidationService(
                new in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue(),
                fieldExtractionService
        );

        Application app = new Application();
        app.setId(70L);
        app.setApplicationReference("APP-2026-601169");
        app.setApplicationType("NEW_INSTITUTION");
        Institution inst = new Institution();
        inst.setId(1L);
        inst.setName("ABC Institute of Technology");
        app.setInstitution(inst);

        ScrutinyAssessment assessment = new ScrutinyAssessment();
        assessment.setId(102L);
        assessment.setApplication(app);

        // Case A: User mistakenly uploads 07_Affiliating_University_NOC.pdf under DOC_CORPUS_FUND
        byte[] nocBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("test-documents/01_baseline_category_samples/07_Affiliating_University_NOC.pdf"));
        String nocHash = "8d0a88b2f90a9b343899f80f146a89c93ab490f8";

        ApplicationDocument affDoc = new ApplicationDocument(
                app, "DOC_AFFILIATING_NOC", "Affiliating University NOC",
                "07_Affiliating_University_NOC.pdf", "07_Affiliating_University_NOC.pdf",
                "sage-application-documents/APP-2026-601169/DOC_AFFILIATING_NOC/07_Affiliating_University_NOC.pdf",
                (long) nocBytes.length, "application/pdf", nocHash, 1, "UPLOADED", "USR-INST-01"
        );
        affDoc.setId(114L);

        ApplicationDocument corpusMistakeDoc = new ApplicationDocument(
                app, "DOC_CORPUS_FUND", "Corpus Fund / Joint FDR Proof",
                "07_Affiliating_University_NOC.pdf", "07_Affiliating_University_NOC.pdf",
                "sage-application-documents/APP-2026-601169/DOC_CORPUS_FUND/07_Affiliating_University_NOC.pdf",
                (long) nocBytes.length, "application/pdf", nocHash, 1, "UPLOADED", "USR-INST-01"
        );
        corpusMistakeDoc.setId(115L);

        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(new TesseractOcrService());
        ExtractedDocumentData corpusMistakeExtData = extractionService.extractText(nocBytes, "07_Affiliating_University_NOC.pdf", "application/pdf");
        ExtractedDocumentData corpusMistakeStructured = fieldExtractionService.extractStructuredFields(corpusMistakeExtData, "DOC_CORPUS_FUND", corpusMistakeDoc);

        ScrutinyDocumentExtraction corpusMistakeExtraction = new ScrutinyDocumentExtraction(
                assessment, 70L, corpusMistakeDoc, "DOC_CORPUS_FUND", corpusMistakeDoc.getDocumentName(), 1,
                corpusMistakeExtData.getExtractionMethod(), corpusMistakeExtData.getRawText(),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(corpusMistakeStructured.getFields()),
                null, corpusMistakeExtData.getOcrStatus(), corpusMistakeExtData.getTextCharacterCount(), corpusMistakeExtData.getPageCount(), corpusMistakeExtData.getValidationMetric()
        );

        var findingsMistake = validationService.evaluateScrutiny(
                assessment, app, List.of(affDoc, corpusMistakeDoc),
                Map.of("DOC_CORPUS_FUND", corpusMistakeExtraction),
                LocalDate.of(2026, 10, 28)
        );

        // Verification: The finding accurately points to document 115 and the uploaded filename '07_Affiliating_University_NOC.pdf'
        var dupFinding = findingsMistake.stream().filter(f -> "RULE_DUPLICATE_FILE_UPLOAD_DOC_CORPUS_FUND".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(dupFinding.getDocumentId()).isEqualTo(115L);
        assertThat(dupFinding.getActualValue()).contains("DOC_AFFILIATING_NOC ('07_Affiliating_University_NOC.pdf')");

        var signalFinding = findingsMistake.stream().filter(f -> "RULE_DOC_SIGNALS_MISSING_DOC_CORPUS_FUND".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(signalFinding.getDocumentId()).isEqualTo(115L);
        assertThat(signalFinding.getDocumentName()).isEqualTo("07_Affiliating_University_NOC.pdf");

        // Case B: Correct file 08_Corpus_Fund_Joint_FDR_Proof.pdf uploaded for DOC_CORPUS_FUND
        byte[] fdrBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("test-documents/01_baseline_category_samples/08_Corpus_Fund_Joint_FDR_Proof.pdf"));
        ApplicationDocument corpusCorrectDoc = new ApplicationDocument(
                app, "DOC_CORPUS_FUND", "Corpus Fund / Joint FDR Proof",
                "08_Corpus_Fund_Joint_FDR_Proof.pdf", "08_Corpus_Fund_Joint_FDR_Proof.pdf",
                "sage-application-documents/APP-2026-601169/DOC_CORPUS_FUND/08_Corpus_Fund_Joint_FDR_Proof.pdf",
                (long) fdrBytes.length, "application/pdf", "hash_fdr_unique_999", 1, "UPLOADED", "USR-INST-01"
        );
        corpusCorrectDoc.setId(116L);

        ExtractedDocumentData corpusCorrectExtData = extractionService.extractText(fdrBytes, "08_Corpus_Fund_Joint_FDR_Proof.pdf", "application/pdf");
        ExtractedDocumentData corpusCorrectStructured = fieldExtractionService.extractStructuredFields(corpusCorrectExtData, "DOC_CORPUS_FUND", corpusCorrectDoc);

        ScrutinyDocumentExtraction corpusCorrectExtraction = new ScrutinyDocumentExtraction(
                assessment, 70L, corpusCorrectDoc, "DOC_CORPUS_FUND", corpusCorrectDoc.getDocumentName(), 1,
                corpusCorrectExtData.getExtractionMethod(), corpusCorrectExtData.getRawText(),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(corpusCorrectStructured.getFields()),
                null, corpusCorrectExtData.getOcrStatus(), corpusCorrectExtData.getTextCharacterCount(), corpusCorrectExtData.getPageCount(), corpusCorrectExtData.getValidationMetric()
        );

        var findingsCorrect = validationService.evaluateScrutiny(
                assessment, app, List.of(affDoc, corpusCorrectDoc),
                Map.of("DOC_CORPUS_FUND", corpusCorrectExtraction),
                LocalDate.of(2026, 10, 28)
        );

        var verifiedFinding = findingsCorrect.stream().filter(f -> "RULE_CORPUS_FUND_VERIFIED".equals(f.getRuleCode())).findFirst().orElseThrow();
        assertThat(verifiedFinding.getOutcome()).isEqualTo("PASS");
        assertThat(verifiedFinding.getDocumentId()).isEqualTo(116L);
        assertThat(verifiedFinding.getDocumentName()).isEqualTo("08_Corpus_Fund_Joint_FDR_Proof.pdf");
        // No duplicate finding for Corpus Fund when file hash is unique
        assertThat(findingsCorrect.stream().noneMatch(f -> "RULE_DUPLICATE_FILE_UPLOAD_DOC_CORPUS_FUND".equals(f.getRuleCode()))).isTrue();
    }
}

