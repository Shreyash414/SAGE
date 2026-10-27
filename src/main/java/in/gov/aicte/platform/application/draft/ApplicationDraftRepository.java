package in.gov.aicte.platform.application.draft;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationDraftRepository extends JpaRepository<ApplicationDraft, Long> {

    Optional<ApplicationDraft> findByDraftReference(String draftReference);

    Optional<ApplicationDraft> findByDraftReferenceAndInstitutionId(String draftReference, Long institutionId);

    Optional<ApplicationDraft> findByIdAndInstitutionId(Long id, Long institutionId);

    List<ApplicationDraft> findByInstitutionIdOrderByUpdatedAtDesc(Long institutionId);

    List<ApplicationDraft> findByInstitutionIdAndStatusOrderByUpdatedAtDesc(Long institutionId, String status);

    boolean existsByDraftReference(String draftReference);
}
