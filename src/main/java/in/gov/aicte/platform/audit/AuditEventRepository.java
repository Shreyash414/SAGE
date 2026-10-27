package in.gov.aicte.platform.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    List<AuditEvent> findByTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc(String targetEntityType, String targetEntityId);

    List<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<AuditEvent> findTop15ByOrderByCreatedAtDesc();
}
