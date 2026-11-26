package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.api.request.FirmwareFilterRequest;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filters for the firmware release catalog (DEV-11). */
public final class FirmwareVersionSpecifications {

    private FirmwareVersionSpecifications() {
    }

    public static Specification<FirmwareVersionEntity> withFilter(FirmwareFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && filter.modelId() != null) {
                predicates.add(cb.equal(root.get("modelId"), filter.modelId()));
            }
            if (filter != null && filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
