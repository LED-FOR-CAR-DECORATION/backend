package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<ProductEntity> withFilter(ProductFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // AC03: Strictly hide unpublished or archived products from public view
            predicates.add(cb.equal(root.get("status"), ProductStatus.PUBLISHED));

            if (request == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // AC02: Filtering by product type
            if (request.productType() != null) {
                predicates.add(cb.equal(root.get("productType"), request.productType()));
            }

            // AC02: Filtering by category tag
            if (request.categoryTag() != null && !request.categoryTag().isBlank()) {
                Join<ProductEntity, TagEntity> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(cb.equal(cb.lower(tagJoin.get("name")), request.categoryTag().trim().toLowerCase()));
                if (query != null) {
                    query.distinct(true);
                }
            }

            // AC02: Filtering by price range
            if (request.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), request.minPrice()));
            }
            if (request.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), request.maxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
