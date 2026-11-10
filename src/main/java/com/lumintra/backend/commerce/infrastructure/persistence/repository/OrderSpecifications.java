package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<OrderEntity> withCustomerFilter(Long customerId, OrderFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // AC01: Strictly scope query to authenticated customer's own orders (prevent IDOR)
            predicates.add(cb.equal(root.get("customerId"), customerId));

            if (request == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // AC03: Optional exact match on order status
            if (request.status() != null) {
                predicates.add(cb.equal(root.get("status"), request.status()));
            }

            // AC03: Optional inclusive UTC date range on createdAt
            Path<Instant> createdAtPath = root.get("createdAt");
            if (request.fromDate() != null) {
                Instant fromInclusive = request.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(createdAtPath, fromInclusive));
            }
            if (request.toDate() != null) {
                if (request.toDate().isBefore(LocalDate.MAX)) {
                    Instant toExclusive = request.toDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    predicates.add(cb.lessThan(createdAtPath, toExclusive));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(createdAtPath, Instant.MAX));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
