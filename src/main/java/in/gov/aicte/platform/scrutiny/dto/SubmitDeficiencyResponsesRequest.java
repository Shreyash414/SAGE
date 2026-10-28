package in.gov.aicte.platform.scrutiny.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SubmitDeficiencyResponsesRequest(
        // Map of deficiencyId -> explanation text
        Map<Long, String> explanations,

        // Array of response items from frontend
        List<DeficiencyResponseItem> responses,

        // General explanatory note for the entire submission
        String submissionNote
) {
    public record DeficiencyResponseItem(Long deficiencyId, String explanation) {}

    public Map<Long, String> getEffectiveExplanations() {
        Map<Long, String> result = new HashMap<>();
        if (explanations != null) {
            result.putAll(explanations);
        }
        if (responses != null) {
            for (DeficiencyResponseItem item : responses) {
                if (item.deficiencyId() != null && item.explanation() != null) {
                    result.put(item.deficiencyId(), item.explanation());
                }
            }
        }
        return result;
    }
}
