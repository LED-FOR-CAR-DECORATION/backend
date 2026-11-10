package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    @Query("""
            SELECT new com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemSummaryRow(
                oi.order.orderId,
                oi.productId
            )
            FROM OrderItemEntity oi
            WHERE oi.order.orderId IN :orderIds
            ORDER BY oi.order.orderId ASC, oi.orderItemId ASC
            """)
    List<OrderItemSummaryRow> findSummaryRowsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    List<OrderItemEntity> findAllByOrder_OrderIdOrderByOrderItemIdAsc(Long orderId);
}
