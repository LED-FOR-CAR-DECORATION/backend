package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic JPA {@link Specification} builders for filtering {@link LedModelEntity} records.
 */
public final class LedModelSpecifications {

    private LedModelSpecifications() {}

    public static Specification<LedModelEntity> withFilter(LedModelFilterRequest filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.widthPx() != null) {
                predicates.add(cb.equal(root.get("widthPx"), filter.widthPx()));
            }
            if (filter.heightPx() != null) {
                predicates.add(cb.equal(root.get("heightPx"), filter.heightPx()));
            }
            if (Boolean.TRUE.equals(filter.unlinkedToProductOnly())) {
                predicates.add(cb.isNull(root.get("productId")));
            }
            if (Boolean.TRUE.equals(filter.lowStockOnly())) {
                predicates.add(cb.lessThan(root.get("stockQuantity"), LedModelEntity.LOW_STOCK_THRESHOLD));
            }
            if (filter.keyword() != null) {
                String pattern = "%" + escapeLike(filter.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("modelName")), pattern, '\\'));
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
