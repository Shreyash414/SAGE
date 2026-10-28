package in.gov.aicte.platform.scrutiny.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDate;
import java.util.List;

public record IssueDeficienciesRequest(
        List<Long> findingIds,
        String formalNoticeTitle,

        @JsonAlias({"officerObservation", "observations", "remarks"})
        String generalObservations,

        @JsonAlias({"dueDate", "statutoryDueDate"})
        LocalDate responseDueDate,

        Boolean returnApplicationForCorrection,

        Boolean blocking
) {
    public String effectiveObservations() {
        return generalObservations != null && !generalObservations.isBlank()
                ? generalObservations
                : "Mandatory rectifications required as per AICTE Approval Process Handbook.";
    }

    public LocalDate effectiveDueDate() {
        return responseDueDate != null
                ? responseDueDate
                : LocalDate.now().plusDays(10);
    }
}
