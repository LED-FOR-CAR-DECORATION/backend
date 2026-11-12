package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByOrderIdAndCustomerId(Long orderId, Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrderEntity o
            SET o.status = :targetStatus,
                o.updatedAt = :updatedAt
            WHERE o.orderId = :orderId
              AND o.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("targetStatus") OrderStatus targetStatus,
            @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrderEntity o
            SET o.status = :targetStatus,
                o.updatedAt = :updatedAt
            WHERE o.orderId = :orderId
              AND o.status IN :allowedStatuses
            """)
    int updateStatusIfIn(
            @Param("orderId") Long orderId,
            @Param("allowedStatuses") Collection<OrderStatus> allowedStatuses,
            @Param("targetStatus") OrderStatus targetStatus,
            @Param("updatedAt") Instant updatedAt);
}
