package in.gov.aicte.platform.admin.dto;

import java.util.Map;

public record AdminMetricsResponse(
        long totalUsers,
        long activeUsers,
        long suspendedUsers,
        long deactivatedUsers,
        Map<String, Long> roleCounts,
        long totalAuditEvents
) {
}
