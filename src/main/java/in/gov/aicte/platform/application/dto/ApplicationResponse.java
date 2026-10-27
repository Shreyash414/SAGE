package in.gov.aicte.platform.application.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.aicte.platform.application.Application;
import in.gov.aicte.platform.workflow.dto.WorkflowTimelineItemResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ApplicationResponse(
        Long id,
        String applicationReference,
        String applicationType,
        String academicYear,
        String status,
        String currentStage,
        String ownerName,
        String ownerId,
        LocalDate dueDate,
        String observations,
        List<String> deficiencies,
        Long ageDays,
        Long version,
        InstitutionSummaryResponse institution,
        List<WorkflowTimelineItemResponse> timeline,
        Map<String, Object> formData,
        Instant submittedAt,
        String submittedBy,
        Instant createdAt,
        Instant updatedAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ApplicationResponse from(Application app) {
        return from(app, Collections.emptyList());
    }

    public static ApplicationResponse from(Application app, List<WorkflowTimelineItemResponse> timeline) {
        List<String> deficienciesList = Collections.emptyList();
        if (app.getDeficienciesJson() != null && !app.getDeficienciesJson().isBlank()) {
            try {
                deficienciesList = OBJECT_MAPPER.readValue(app.getDeficienciesJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> formData = Collections.emptyMap();
        if (app.getFormDataJson() != null && !app.getFormDataJson().isBlank()) {
            try {
                formData = OBJECT_MAPPER.readValue(app.getFormDataJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        long ageDays = 0L;
        if (app.getCreatedAt() != null) {
            ageDays = Math.max(0L, Duration.between(app.getCreatedAt(), Instant.now()).toDays());
        }

        return new ApplicationResponse(
                app.getId(),
                app.getApplicationReference(),
                app.getApplicationType(),
                app.getAcademicYear(),
                app.getStatus(),
                app.getCurrentStage(),
                app.getOwnerName(),
                app.getOwnerId(),
                app.getDueDate(),
                app.getObservations(),
                deficienciesList,
                ageDays,
                app.getVersion(),
                InstitutionSummaryResponse.from(app.getInstitution()),
                timeline != null ? timeline : Collections.emptyList(),
                formData,
                app.getSubmittedAt(),
                app.getSubmittedBy(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
