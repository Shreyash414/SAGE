package in.gov.aicte.platform.scrutiny.repository;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrutinyAssessmentRepository extends JpaRepository<ScrutinyAssessment, Long> {

    List<ScrutinyAssessment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    Optional<ScrutinyAssessment> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    Optional<ScrutinyAssessment> findByAssessmentReference(String assessmentReference);

    long countByApplicationId(Long applicationId);
}
