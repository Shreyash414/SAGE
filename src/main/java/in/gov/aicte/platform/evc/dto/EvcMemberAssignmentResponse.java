package in.gov.aicte.platform.evc.dto;

import in.gov.aicte.platform.evc.EvcMemberAssignment;

import java.time.Instant;

public record EvcMemberAssignmentResponse(
        Long id,
        Long evcVisitId,
        String evcReference,
        String applicationId,
        String institutionName,
        String memberId,
        String memberName,
        String committeeRole,
        String notes,
        Instant assignedAt
) {
    public static EvcMemberAssignmentResponse from(EvcMemberAssignment assignment) {
        return new EvcMemberAssignmentResponse(
                assignment.getId(),
                assignment.getEvcVisit() != null ? assignment.getEvcVisit().getId() : null,
                assignment.getEvcVisit() != null ? assignment.getEvcVisit().getEvcReference() : null,
                assignment.getEvcVisit() != null && assignment.getEvcVisit().getApplication() != null
                        ? assignment.getEvcVisit().getApplication().getApplicationReference() : null,
                assignment.getEvcVisit() != null && assignment.getEvcVisit().getApplication() != null
                        && assignment.getEvcVisit().getApplication().getInstitution() != null
                        ? assignment.getEvcVisit().getApplication().getInstitution().getName() : null,
                assignment.getMember() != null ? assignment.getMember().getMemberId() : null,
                assignment.getMember() != null ? assignment.getMember().getFullName() : null,
                assignment.getCommitteeRole(),
                assignment.getNotes(),
                assignment.getAssignedAt()
        );
    }
}

