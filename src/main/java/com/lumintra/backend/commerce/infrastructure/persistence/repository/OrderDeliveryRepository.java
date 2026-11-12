package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDeliveryEntity, Long> {

    Optional<OrderDeliveryEntity> findByOrder_OrderId(Long orderId);

    @Query("""
            SELECT new com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliverySummaryRow(
                d.order.orderId,
                d.deliveryStatus,
                d.recipientName,
                d.recipientPhone
            )
            FROM OrderDeliveryEntity d
            WHERE d.order.orderId IN :orderIds
            """)
    List<OrderDeliverySummaryRow> findSummaryRowsByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
