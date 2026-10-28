package in.gov.aicte.platform.evc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvcMemberAssignmentRepository extends JpaRepository<EvcMemberAssignment, Long> {

    List<EvcMemberAssignment> findByMemberId(Long memberId);

    List<EvcMemberAssignment> findByEvcVisitId(Long evcVisitId);

    boolean existsByEvcVisitIdAndMemberId(Long evcVisitId, Long memberId);

    Optional<EvcMemberAssignment> findByEvcVisitIdAndMemberId(Long evcVisitId, Long memberId);

    void deleteByEvcVisitIdAndMemberId(Long evcVisitId, Long memberId);

    @Query("SELECT a FROM EvcMemberAssignment a WHERE a.member.memberId = :memberId")
    List<EvcMemberAssignment> findByMemberMemberId(@Param("memberId") String memberId);

    @Query("SELECT a FROM EvcMemberAssignment a WHERE a.member.user.userId = :userId")
    List<EvcMemberAssignment> findByMemberUserUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(a) FROM EvcMemberAssignment a WHERE a.member.id = :memberId")
    long countByMemberId(@Param("memberId") Long memberId);
}

