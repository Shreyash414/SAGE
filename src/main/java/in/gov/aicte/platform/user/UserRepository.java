package in.gov.aicte.platform.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUserId(String userId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUserId(String userId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, String userId);

    long countByStatus(String status);

    long countByRole(String role);

    List<User> findTop10ByLastLoginAtIsNotNullOrderByLastLoginAtDesc();
}
