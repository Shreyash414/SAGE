package in.gov.aicte.platform.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> withFilters(
            String eventType,
            String actorId,
            String targetEntityType,
            String targetEntityId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("eventType")), eventType.trim().toLowerCase()));
            }
            if (actorId != null && !actorId.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("actorId")), actorId.trim().toLowerCase()));
            }
            if (targetEntityType != null && !targetEntityType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("targetEntityType")), targetEntityType.trim().toLowerCase()));
            }
            if (targetEntityId != null && !targetEntityId.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("targetEntityId")), targetEntityId.trim().toLowerCase()));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
