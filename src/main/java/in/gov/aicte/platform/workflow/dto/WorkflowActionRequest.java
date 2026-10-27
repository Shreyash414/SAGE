package in.gov.aicte.platform.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record WorkflowActionRequest(
        @com.fasterxml.jackson.annotation.JsonAlias({"action", "action_type"})
        @NotBlank(message = "Action type is required (e.g. RETURN_FOR_CORRECTION, SUBMIT_CORRECTIONS, COMPLETE_SCRUTINY)")
        String actionType,

        String actorName,

        String actorRole,

        String reason,

        String observations,

        List<String> newDeficiencies,

        String nextStage
) {
}
