package in.gov.aicte.platform.scrutiny.repository;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyDocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrutinyDocumentExtractionRepository extends JpaRepository<ScrutinyDocumentExtraction, Long> {

    List<ScrutinyDocumentExtraction> findByAssessmentIdOrderByCreatedAtAsc(Long assessmentId);

    List<ScrutinyDocumentExtraction> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    Optional<ScrutinyDocumentExtraction> findByAssessmentIdAndRequirementCode(Long assessmentId, String requirementCode);

    Optional<ScrutinyDocumentExtraction> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
