package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDeliveryEntity, Long> {

    Optional<OrderDeliveryEntity> findByOrder_OrderId(Long orderId);

    Optional<OrderDeliveryEntity> findFirstByTrackingNumberOrderByDeliveryIdDesc(String trackingNumber);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrderDeliveryEntity d
            SET d.trackingNumber = :trackingNumber,
                d.trackingUrl = :trackingUrl,
                d.carrierFee = :carrierFee,
                d.deliveryStatus = :targetStatus,
                d.updatedAt = :updatedAt
            WHERE d.order.orderId = :orderId
              AND d.deliveryStatus IN :eligibleStatuses
            """)
    int updateDispatchInfoIfEligible(
            @Param("orderId") Long orderId,
            @Param("eligibleStatuses") Collection<DeliveryStatus> eligibleStatuses,
            @Param("targetStatus") DeliveryStatus targetStatus,
            @Param("trackingNumber") String trackingNumber,
            @Param("trackingUrl") String trackingUrl,
            @Param("carrierFee") BigDecimal carrierFee,
            @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrderDeliveryEntity d
            SET d.deliveryStatus = :targetStatus,
                d.trackingUrl = COALESCE(:trackingUrl, d.trackingUrl),
                d.carrierFee = COALESCE(:carrierFee, d.carrierFee),
                d.deliveredAt = COALESCE(:deliveredAt, d.deliveredAt),
                d.updatedAt = :updatedAt
            WHERE d.deliveryId = :deliveryId
              AND d.deliveryStatus IN :allowedStatuses
            """)
    int updateWebhookDeliveryStatusIfAllowed(
            @Param("deliveryId") Long deliveryId,
            @Param("allowedStatuses") Collection<DeliveryStatus> allowedStatuses,
            @Param("targetStatus") DeliveryStatus targetStatus,
            @Param("trackingUrl") String trackingUrl,
            @Param("carrierFee") BigDecimal carrierFee,
            @Param("deliveredAt") Instant deliveredAt,
            @Param("updatedAt") Instant updatedAt);
}
