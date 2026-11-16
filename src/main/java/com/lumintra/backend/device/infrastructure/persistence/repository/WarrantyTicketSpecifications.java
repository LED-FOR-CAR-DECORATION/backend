package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class WarrantyTicketSpecifications {

    private WarrantyTicketSpecifications() {}

    public static Specification<WarrantyTicketEntity> withFilter(
            WarrantyTicketStatus status,
            String query,
            Set<Long> matchedCustomerIds) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                Predicate serialPredicate = cb.like(cb.lower(root.get("deviceSerialNumber")), pattern);
                Predicate issuePredicate = cb.like(cb.lower(root.get("issueDescription")), pattern);

                if (matchedCustomerIds != null && !matchedCustomerIds.isEmpty()) {
                    Predicate customerPredicate = root.get("customerId").in(matchedCustomerIds);
                    predicates.add(cb.or(serialPredicate, issuePredicate, customerPredicate));
                } else {
                    predicates.add(cb.or(serialPredicate, issuePredicate));
                }
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
