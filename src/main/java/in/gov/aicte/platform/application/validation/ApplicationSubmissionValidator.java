package in.gov.aicte.platform.application.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;
import in.gov.aicte.platform.application.catalogue.DocumentRequirementDefinition;
import in.gov.aicte.platform.application.catalogue.FormFieldDefinition;
import in.gov.aicte.platform.application.catalogue.FormSectionDefinition;
import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.application.document.ApplicationDocumentRepository;
import in.gov.aicte.platform.application.draft.ApplicationDraft;
import in.gov.aicte.platform.application.draft.dto.DraftChecklistResponse;
import in.gov.aicte.platform.application.dto.ApplicationChecklistResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ApplicationSubmissionValidator {

    private final ApplicationTypeCatalogue catalogue;
    private final ApplicationDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    public ApplicationSubmissionValidator(
            ApplicationTypeCatalogue catalogue,
            ApplicationDocumentRepository documentRepository,
            ObjectMapper objectMapper
    ) {
        this.catalogue = catalogue;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
    }

    public record ValidationReport(
            boolean valid,
            List<String> missingFields,
            List<String> missingDocuments,
            List<String> blockingErrors
    ) {}

    public ValidationReport validate(Application application) {
        List<String> missingFields = new ArrayList<>();
        List<String> missingDocuments = new ArrayList<>();
        List<String> blockingErrors = new ArrayList<>();

        Optional<ApplicationTypeDefinition> defOpt = catalogue.findByCode(application.getApplicationType());
        if (defOpt.isEmpty()) {
            blockingErrors.add("Unrecognized application type: " + application.getApplicationType());
            return new ValidationReport(false, missingFields, missingDocuments, blockingErrors);
        }

        ApplicationTypeDefinition typeDef = defOpt.get();
        Map<String, Object> formData = parseFormData(application.getFormDataJson());

        // Validate form fields
        for (FormSectionDefinition section : typeDef.sections()) {
            for (FormFieldDefinition field : section.fields()) {
                if (field.required()) {
                    Object val = formData.get(field.name());
                    if (val == null || String.valueOf(val).trim().isEmpty()) {
                        missingFields.add(String.format("[%s] %s is required", section.title(), field.label()));
                    } else if ("checkbox".equalsIgnoreCase(field.type())) {
                        if (!Boolean.parseBoolean(String.valueOf(val))) {
                            missingFields.add(String.format("[%s] %s must be accepted and certified", section.title(), field.label()));
                        }
                    } else if ("number".equalsIgnoreCase(field.type())) {
                        try {
                            double num = Double.parseDouble(String.valueOf(val));
                            if (num <= 0) {
                                missingFields.add(String.format("[%s] %s must be greater than zero", section.title(), field.label()));
                            }
                        } catch (NumberFormatException e) {
                            missingFields.add(String.format("[%s] %s must be a valid number", section.title(), field.label()));
                        }
                    }
                }
            }
        }

        // Validate mandatory documents
        List<ApplicationDocument> uploadedDocs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(application.getId());
        Map<String, ApplicationDocument> docMap = uploadedDocs.stream()
                .collect(Collectors.toMap(
                        ApplicationDocument::getRequirementCode,
                        d -> d,
                        (existing, replacement) -> replacement
                ));

        for (DocumentRequirementDefinition req : typeDef.documentRequirements()) {
            if (req.mandatory()) {
                ApplicationDocument doc = docMap.get(req.code());
                if (doc == null || doc.getFileSize() == null || doc.getFileSize() <= 0) {
                    missingDocuments.add(String.format("%s (%s - %s)", req.name(), req.code(), req.aphClause()));
                }
            }
        }

        blockingErrors.addAll(missingFields);
        blockingErrors.addAll(missingDocuments);

        return new ValidationReport(blockingErrors.isEmpty(), missingFields, missingDocuments, blockingErrors);
    }

    public ApplicationChecklistResponse generateChecklist(Application application) {
        Optional<ApplicationTypeDefinition> defOpt = catalogue.findByCode(application.getApplicationType());
        if (defOpt.isEmpty()) {
            return new ApplicationChecklistResponse(
                    application.getId(),
                    application.getApplicationReference(),
                    application.getApplicationType(),
                    application.getStatus(),
                    false,
                    0,
                    0,
                    0,
                    List.of("Unrecognized application type: " + application.getApplicationType()),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        ApplicationTypeDefinition typeDef = defOpt.get();
        Map<String, Object> formData = parseFormData(application.getFormDataJson());
        List<ApplicationDocument> uploadedDocs = documentRepository.findByApplicationIdOrderByUploadedAtAsc(application.getId());
        Map<String, ApplicationDocument> docMap = uploadedDocs.stream()
                .collect(Collectors.toMap(
                        ApplicationDocument::getRequirementCode,
                        d -> d,
                        (existing, replacement) -> replacement
                ));

        List<ApplicationChecklistResponse.ChecklistSectionItem> sectionItems = new ArrayList<>();
        int totalRequiredFields = 0;
        int completedFields = 0;

        for (FormSectionDefinition sec : typeDef.sections()) {
            List<String> secMissing = new ArrayList<>();
            int secRequired = 0;
            int secDone = 0;

            for (FormFieldDefinition field : sec.fields()) {
                if (field.required()) {
                    secRequired++;
                    totalRequiredFields++;
                    Object val = formData.get(field.name());
                    boolean fieldComplete = false;
                    if (val != null && !String.valueOf(val).trim().isEmpty()) {
                        if ("checkbox".equalsIgnoreCase(field.type())) {
                            fieldComplete = Boolean.parseBoolean(String.valueOf(val));
                        } else if ("number".equalsIgnoreCase(field.type())) {
                            try {
                                fieldComplete = Double.parseDouble(String.valueOf(val)) > 0;
                            } catch (NumberFormatException ignored) {}
                        } else {
                            fieldComplete = true;
                        }
                    }

                    if (fieldComplete) {
                        secDone++;
                        completedFields++;
                    } else {
                        secMissing.add(field.label());
                    }
                }
            }

            sectionItems.add(new ApplicationChecklistResponse.ChecklistSectionItem(
                    sec.id(),
                    sec.title(),
                    secMissing.isEmpty(),
                    secRequired,
                    secDone,
                    secMissing
            ));
        }

        List<ApplicationChecklistResponse.ChecklistDocumentItem> docItems = new ArrayList<>();
        int totalMandatoryDocs = 0;
        int uploadedMandatoryDocs = 0;
        List<String> blockingDeficiencies = new ArrayList<>();

        for (DocumentRequirementDefinition req : typeDef.documentRequirements()) {
            if (req.mandatory()) {
                totalMandatoryDocs++;
            }
            ApplicationDocument doc = docMap.get(req.code());
            boolean isUploaded = (doc != null && doc.getFileSize() != null && doc.getFileSize() > 0);

            if (req.mandatory()) {
                if (isUploaded) {
                    uploadedMandatoryDocs++;
                } else {
                    blockingDeficiencies.add("Missing document: " + req.name() + " (" + req.aphClause() + ")");
                }
            }

            docItems.add(new ApplicationChecklistResponse.ChecklistDocumentItem(
                    req.code(),
                    req.name(),
                    req.aphClause(),
                    req.mandatory(),
                    isUploaded,
                    isUploaded ? doc.getId() : null,
                    isUploaded ? doc.getOriginalFileName() : null,
                    isUploaded ? doc.getFileSize() : null,
                    isUploaded ? doc.getFileHash() : null,
                    isUploaded ? doc.getVersion() : 0,
                    isUploaded ? doc.getUploadedAt() : null
            ));
        }

        for (var sec : sectionItems) {
            for (String mf : sec.missingFields()) {
                blockingDeficiencies.add("Incomplete field: [" + sec.title() + "] " + mf);
            }
        }

        int formPct = totalRequiredFields > 0 ? (completedFields * 100 / totalRequiredFields) : 100;
        int docPct = totalMandatoryDocs > 0 ? (uploadedMandatoryDocs * 100 / totalMandatoryDocs) : 100;
        int overallPct = (formPct + docPct) / 2;
        boolean submittable = blockingDeficiencies.isEmpty();

        return new ApplicationChecklistResponse(
                application.getId(),
                application.getApplicationReference(),
                application.getApplicationType(),
                application.getStatus(),
                submittable,
                overallPct,
                formPct,
                docPct,
                blockingDeficiencies,
                sectionItems,
                docItems
        );
    }

    public ValidationReport validateDraft(ApplicationDraft draft) {
        List<String> missingFields = new ArrayList<>();
        List<String> missingDocuments = new ArrayList<>();
        List<String> blockingErrors = new ArrayList<>();

        Optional<ApplicationTypeDefinition> defOpt = catalogue.findByCode(draft.getApplicationType());
        if (defOpt.isEmpty()) {
            blockingErrors.add("Unrecognized application type: " + draft.getApplicationType());
            return new ValidationReport(false, missingFields, missingDocuments, blockingErrors);
        }

        ApplicationTypeDefinition typeDef = defOpt.get();
        Map<String, Object> formData = parseFormData(draft.getFormDataJson());

        // Validate form fields
        for (FormSectionDefinition section : typeDef.sections()) {
            for (FormFieldDefinition field : section.fields()) {
                if (field.required()) {
                    Object val = formData.get(field.name());
                    if (val == null || String.valueOf(val).trim().isEmpty()) {
                        missingFields.add(String.format("[%s] %s is required", section.title(), field.label()));
                    } else if ("checkbox".equalsIgnoreCase(field.type())) {
                        if (!Boolean.parseBoolean(String.valueOf(val))) {
                            missingFields.add(String.format("[%s] %s must be accepted and certified", section.title(), field.label()));
                        }
                    } else if ("number".equalsIgnoreCase(field.type())) {
                        try {
                            double num = Double.parseDouble(String.valueOf(val));
                            if (num <= 0) {
                                missingFields.add(String.format("[%s] %s must be greater than zero", section.title(), field.label()));
                            }
                        } catch (NumberFormatException e) {
                            missingFields.add(String.format("[%s] %s must be a valid number", section.title(), field.label()));
                        }
                    }
                }
            }
        }

        // Validate mandatory documents
        List<ApplicationDocument> uploadedDocs = documentRepository.findByDraftIdOrderByUploadedAtAsc(draft.getId());
        Map<String, ApplicationDocument> docMap = uploadedDocs.stream()
                .collect(Collectors.toMap(
                        ApplicationDocument::getRequirementCode,
                        d -> d,
                        (existing, replacement) -> replacement
                ));

        for (DocumentRequirementDefinition req : typeDef.documentRequirements()) {
            if (req.mandatory()) {
                ApplicationDocument doc = docMap.get(req.code());
                if (doc == null || doc.getFileSize() == null || doc.getFileSize() <= 0) {
                    missingDocuments.add(String.format("%s (%s - %s)", req.name(), req.code(), req.aphClause()));
                }
            }
        }

        blockingErrors.addAll(missingFields);
        blockingErrors.addAll(missingDocuments);

        return new ValidationReport(blockingErrors.isEmpty(), missingFields, missingDocuments, blockingErrors);
    }

    public DraftChecklistResponse generateDraftChecklist(ApplicationDraft draft) {
        Optional<ApplicationTypeDefinition> defOpt = catalogue.findByCode(draft.getApplicationType());
        if (defOpt.isEmpty()) {
            return new DraftChecklistResponse(
                    draft.getId(),
                    draft.getDraftReference(),
                    draft.getApplicationType(),
                    draft.getStatus(),
                    false,
                    0,
                    0,
                    0,
                    List.of("Unrecognized application type: " + draft.getApplicationType()),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        ApplicationTypeDefinition typeDef = defOpt.get();
        Map<String, Object> formData = parseFormData(draft.getFormDataJson());
        List<ApplicationDocument> uploadedDocs = documentRepository.findByDraftIdOrderByUploadedAtAsc(draft.getId());
        Map<String, ApplicationDocument> docMap = uploadedDocs.stream()
                .collect(Collectors.toMap(
                        ApplicationDocument::getRequirementCode,
                        d -> d,
                        (existing, replacement) -> replacement
                ));

        List<ApplicationChecklistResponse.ChecklistSectionItem> sectionItems = new ArrayList<>();
        int totalRequiredFields = 0;
        int completedFields = 0;

        for (FormSectionDefinition sec : typeDef.sections()) {
            List<String> secMissing = new ArrayList<>();
            int secRequired = 0;
            int secDone = 0;

            for (FormFieldDefinition field : sec.fields()) {
                if (field.required()) {
                    secRequired++;
                    totalRequiredFields++;
                    Object val = formData.get(field.name());
                    boolean fieldComplete = false;
                    if (val != null && !String.valueOf(val).trim().isEmpty()) {
                        if ("checkbox".equalsIgnoreCase(field.type())) {
                            fieldComplete = Boolean.parseBoolean(String.valueOf(val));
                        } else if ("number".equalsIgnoreCase(field.type())) {
                            try {
                                fieldComplete = Double.parseDouble(String.valueOf(val)) > 0;
                            } catch (NumberFormatException ignored) {}
                        } else {
                            fieldComplete = true;
                        }
                    }

                    if (fieldComplete) {
                        secDone++;
                        completedFields++;
                    } else {
                        secMissing.add(field.label());
                    }
                }
            }

            sectionItems.add(new ApplicationChecklistResponse.ChecklistSectionItem(
                    sec.id(),
                    sec.title(),
                    secMissing.isEmpty(),
                    secRequired,
                    secDone,
                    secMissing
            ));
        }

        List<ApplicationChecklistResponse.ChecklistDocumentItem> docItems = new ArrayList<>();
        int totalMandatoryDocs = 0;
        int uploadedMandatoryDocs = 0;
        List<String> blockingDeficiencies = new ArrayList<>();

        for (DocumentRequirementDefinition req : typeDef.documentRequirements()) {
            if (req.mandatory()) {
                totalMandatoryDocs++;
            }
            ApplicationDocument doc = docMap.get(req.code());
            boolean isUploaded = (doc != null && doc.getFileSize() != null && doc.getFileSize() > 0);

            if (req.mandatory()) {
                if (isUploaded) {
                    uploadedMandatoryDocs++;
                } else {
                    blockingDeficiencies.add("Missing document: " + req.name() + " (" + req.aphClause() + ")");
                }
            }

            docItems.add(new ApplicationChecklistResponse.ChecklistDocumentItem(
                    req.code(),
                    req.name(),
                    req.aphClause(),
                    req.mandatory(),
                    isUploaded,
                    isUploaded ? doc.getId() : null,
                    isUploaded ? doc.getOriginalFileName() : null,
                    isUploaded ? doc.getFileSize() : null,
                    isUploaded ? doc.getFileHash() : null,
                    isUploaded ? doc.getVersion() : 0,
                    isUploaded ? doc.getUploadedAt() : null
            ));
        }

        for (var sec : sectionItems) {
            for (String mf : sec.missingFields()) {
                blockingDeficiencies.add("Incomplete field: [" + sec.title() + "] " + mf);
            }
        }

        int formPct = totalRequiredFields > 0 ? (completedFields * 100 / totalRequiredFields) : 100;
        int docPct = totalMandatoryDocs > 0 ? (uploadedMandatoryDocs * 100 / totalMandatoryDocs) : 100;
        int overallPct = (formPct + docPct) / 2;
        boolean submittable = blockingDeficiencies.isEmpty();

        return new DraftChecklistResponse(
                draft.getId(),
                draft.getDraftReference(),
                draft.getApplicationType(),
                draft.getStatus(),
                submittable,
                overallPct,
                formPct,
                docPct,
                blockingDeficiencies,
                sectionItems,
                docItems
        );
    }

    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
