package in.gov.aicte.platform.application.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    List<ApplicationDocument> findByApplicationIdOrderByUploadedAtAsc(Long applicationId);

    Optional<ApplicationDocument> findByApplicationIdAndRequirementCode(Long applicationId, String requirementCode);

    Optional<ApplicationDocument> findByIdAndApplicationId(Long id, Long applicationId);

    void deleteByApplicationId(Long applicationId);

    long countByApplicationId(Long applicationId);

    List<ApplicationDocument> findByDraftIdOrderByUploadedAtAsc(Long draftId);

    Optional<ApplicationDocument> findByDraftIdAndRequirementCode(Long draftId, String requirementCode);

    Optional<ApplicationDocument> findByIdAndDraftId(Long id, Long draftId);

    void deleteByDraftId(Long draftId);

    long countByDraftId(Long draftId);
}
