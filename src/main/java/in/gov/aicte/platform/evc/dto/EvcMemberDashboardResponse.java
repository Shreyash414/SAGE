package in.gov.aicte.platform.evc.dto;

import java.util.List;

public record EvcMemberDashboardResponse(
        EvcMemberProfileResponse profile,
        EvcMetricsResponse metrics,
        List<EvcResponse> assignedInspections,
        List<EvcResponse> upcomingVisits
) {
}

