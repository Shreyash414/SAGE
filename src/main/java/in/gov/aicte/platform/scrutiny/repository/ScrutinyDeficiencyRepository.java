package in.gov.aicte.platform.scrutiny.repository;

import in.gov.aicte.platform.scrutiny.entity.ScrutinyDeficiency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrutinyDeficiencyRepository extends JpaRepository<ScrutinyDeficiency, Long> {

    List<ScrutinyDeficiency> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);

    List<ScrutinyDeficiency> findByApplicationIdAndStatus(Long applicationId, String status);

    Optional<ScrutinyDeficiency> findByDeficiencyReference(String deficiencyReference);

    Optional<ScrutinyDeficiency> findByIdAndApplicationId(Long id, Long applicationId);

    Optional<ScrutinyDeficiency> findByFindingId(Long findingId);

    long countByApplicationIdAndStatus(Long applicationId, String status);

    long countByApplicationIdAndBlockingTrueAndStatusIn(Long applicationId, List<String> statuses);
}
