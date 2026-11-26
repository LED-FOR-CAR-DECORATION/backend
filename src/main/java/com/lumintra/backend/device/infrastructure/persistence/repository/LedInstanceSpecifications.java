package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic JPA {@link Specification} builders for filtering {@link LedInstanceEntity} records.
 */
public final class LedInstanceSpecifications {

    private LedInstanceSpecifications() {}

    public static Specification<LedInstanceEntity> withFilter(LedInstanceFilterRequest filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (filter.modelId() != null) {
                predicates.add(cb.equal(root.get("modelId"), filter.modelId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.search() != null) {
                String pattern = "%" + escapeLike(filter.search().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("serialNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("macAddress")), pattern, '\\')
                ));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
