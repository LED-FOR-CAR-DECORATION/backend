package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OrderSpecifications {

    public static final Set<OrderStatus> DEFAULT_STAFF_QUEUE_STATUSES = Set.of(
            OrderStatus.PROCESSING,
            OrderStatus.PARTIALLY_FULFILLED
    );

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

    public static Specification<OrderEntity> withStaffFulfillmentFilter(StaffOrderFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // AC01 (Physical/LED fulfillment scope): require an OrderDeliveryEntity record
            Subquery<Long> deliverySubquery = query.subquery(Long.class);
            Root<OrderDeliveryEntity> deliveryRoot = deliverySubquery.from(OrderDeliveryEntity.class);
            List<Predicate> deliveryPredicates = new ArrayList<>();
            deliveryPredicates.add(cb.equal(deliveryRoot.get("order").get("orderId"), root.get("orderId")));
            if (request != null && request.deliveryStatus() != null) {
                deliveryPredicates.add(cb.equal(deliveryRoot.get("deliveryStatus"), request.deliveryStatus()));
            }
            deliverySubquery.select(deliveryRoot.get("deliveryId"))
                    .where(deliveryPredicates.toArray(new Predicate[0]));
            predicates.add(cb.exists(deliverySubquery));

            // AC01 & AC02: Default fulfillment queue is PROCESSING / PARTIALLY_FULFILLED when status omitted
            if (request != null && request.status() != null) {
                predicates.add(cb.equal(root.get("status"), request.status()));
            } else {
                predicates.add(root.get("status").in(DEFAULT_STAFF_QUEUE_STATUSES));
            }

            // AC02: Optional filter against the latest payment_transaction row for each order
            if (request != null && request.paymentStatus() != null) {
                Subquery<Long> latestPaymentSubquery = query.subquery(Long.class);
                Root<PaymentTransactionEntity> ptRoot = latestPaymentSubquery.from(PaymentTransactionEntity.class);

                Subquery<Long> newerPaymentSubquery = latestPaymentSubquery.subquery(Long.class);
                Root<PaymentTransactionEntity> ptNewer = newerPaymentSubquery.from(PaymentTransactionEntity.class);
                newerPaymentSubquery.select(ptNewer.get("paymentId"))
                        .where(
                                cb.equal(ptNewer.get("orderId"), root.get("orderId")),
                                cb.or(
                                        cb.greaterThan(ptNewer.get("createdAt"), ptRoot.get("createdAt")),
                                        cb.and(
                                                cb.equal(ptNewer.get("createdAt"), ptRoot.get("createdAt")),
                                                cb.greaterThan(ptNewer.get("paymentId"), ptRoot.get("paymentId"))
                                        )
                                )
                        );

                latestPaymentSubquery.select(ptRoot.get("paymentId"))
                        .where(
                                cb.equal(ptRoot.get("orderId"), root.get("orderId")),
                                cb.equal(ptRoot.get("status"), request.paymentStatus()),
                                cb.not(cb.exists(newerPaymentSubquery))
                        );

                predicates.add(cb.exists(latestPaymentSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
