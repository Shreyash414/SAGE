package in.gov.aicte.platform.scrutiny.repository;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyRescrutinyComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrutinyRescrutinyComparisonRepository extends JpaRepository<ScrutinyRescrutinyComparison, Long> {

    List<ScrutinyRescrutinyComparison> findByAssessmentIdOrderByIdAsc(Long assessmentId);

    List<ScrutinyRescrutinyComparison> findByDeficiencyIdOrderByCreatedAtDesc(Long deficiencyId);

    List<ScrutinyRescrutinyComparison> findByAssessmentApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ScrutinyRescrutinyComparison> findByAssessmentApplicationIdOrderByIdAsc(Long applicationId);
}
