package in.gov.aicte.platform.admin.dto;

import in.gov.aicte.platform.audit.AuditEvent;

import java.util.List;

public record UserDetailResponse(
        UserResponse user,
        List<AuditEvent> recentAuditEvents
) {
}
