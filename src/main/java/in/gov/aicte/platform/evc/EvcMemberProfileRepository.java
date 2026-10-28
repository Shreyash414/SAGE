package in.gov.aicte.platform.evc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EvcMemberProfileRepository extends JpaRepository<EvcMemberProfile, Long> {

    Optional<EvcMemberProfile> findByMemberId(String memberId);

    Optional<EvcMemberProfile> findByUserId(Long userId);

    @Query("SELECT p FROM EvcMemberProfile p WHERE p.user.userId = :userId")
    Optional<EvcMemberProfile> findByUserUserId(@Param("userId") String userId);

    @Query("SELECT p FROM EvcMemberProfile p WHERE LOWER(p.user.email) = LOWER(:email)")
    Optional<EvcMemberProfile> findByUserEmailIgnoreCase(@Param("email") String email);

    boolean existsByMemberId(String memberId);
}

