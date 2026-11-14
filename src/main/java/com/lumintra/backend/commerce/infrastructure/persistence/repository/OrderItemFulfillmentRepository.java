package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemFulfillmentRepository extends JpaRepository<OrderItemFulfillmentEntity, Long> {

    boolean existsByInstanceId(Long instanceId);

    @Query("""
            SELECT f
            FROM OrderItemFulfillmentEntity f
            JOIN FETCH f.orderItem oi
            JOIN FETCH oi.order o
            WHERE f.instanceId IN :instanceIds
            """)
    List<OrderItemFulfillmentEntity> findAllWithOrderByInstanceIdIn(@Param("instanceIds") Collection<Long> instanceIds);

    @Query("""
            SELECT f
            FROM OrderItemFulfillmentEntity f
            JOIN FETCH f.orderItem oi
            JOIN FETCH oi.order o
            WHERE f.instanceId = :instanceId
            """)
    Optional<OrderItemFulfillmentEntity> findWithOrderByInstanceId(@Param("instanceId") Long instanceId);
}
