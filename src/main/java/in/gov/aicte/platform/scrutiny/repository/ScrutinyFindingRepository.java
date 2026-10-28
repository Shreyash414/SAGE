package in.gov.aicte.platform.scrutiny.repository;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrutinyFindingRepository extends JpaRepository<ScrutinyFinding, Long> {

    List<ScrutinyFinding> findByAssessmentIdOrderByIdAsc(Long assessmentId);

    List<ScrutinyFinding> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ScrutinyFinding> findByAssessmentIdAndOutcome(Long assessmentId, String outcome);

    long countByAssessmentIdAndOutcome(Long assessmentId, String outcome);

    long countByAssessmentIdAndOfficerDecision(Long assessmentId, String officerDecision);
}
