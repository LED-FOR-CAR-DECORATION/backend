package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.CancelOrderRequest;
import com.lumintra.backend.commerce.api.response.CancelOrderResponse;
import com.lumintra.backend.commerce.application.CancelOrderUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private BundleLookupService bundleLookupService;

    @Mock
    private LedInventoryService ledInventoryService;

    private CancelOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelOrderUseCase(
                orderRepository,
                orderDeliveryRepository,
                orderItemRepository,
                productLookupService,
                bundleLookupService,
                ledInventoryService,
                new OrderMapper()
        );
    }

    private OrderEntity sampleOrder(Long orderId, Long customerId, OrderStatus status, OrderType orderType) {
        return OrderEntity.of(
                orderId,
                customerId,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                orderType,
                status,
                Instant.parse("2026-11-12T12:00:00Z")
        );
    }

    private ResolvedShippingAddress sampleShipping() {
        return new ResolvedShippingAddress(
                10L,
                "Nguyen Van A",
                "0901234567",
                "123 Nguyen Hue, Quan 1, TP.HCM",
                new BigDecimal("10.7769"),
                new BigDecimal("106.7009")
        );
    }

    @Test
    @DisplayName("COM-09 UTCID01 [N]: Customer cancels PENDING_PAYMENT physical order with direct LED items -> transitions to CANCELED and restores LED stock")
    void execute_pendingPaymentPhysicalOrder_transitionsToCanceledAndRestoresStock() {
        Long customerId = 42L;
        Long orderId = 101L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);

        OrderItemEntity ledItem = OrderItemEntity.create(order, 5L, 2, new BigDecimal("200000.00"));

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(ledItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(5L))))
                .thenReturn(Map.of(
                        5L, new ProductSnapshot(5L, "LED Matrix v1", ProductType.LED, null,
                                new BigDecimal("200000.00"), ProductStatus.PUBLISHED)
                ));

        CancelOrderResponse response = useCase.execute(customerId, orderId, new CancelOrderRequest("Changed my mind"));

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.previousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(response.updatedAt()).isNotNull();

        verify(ledInventoryService).restoreStocks(Map.of(5L, 2L));
    }

    @Test
    @DisplayName("COM-09 UTCID02 [N]: Customer cancels PROCESSING physical order with bundle LED items -> transitions to REFUNDED and restores bundle LED stock")
    void execute_processingPhysicalOrder_transitionsToRefundedAndRestoresBundleLedStock() {
        Long customerId = 42L;
        Long orderId = 102L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PROCESSING, OrderType.PHYSICAL);

        OrderDeliveryEntity pendingDelivery = OrderDeliveryEntity.createPending(order, sampleShipping());
        OrderItemEntity bundleItem = OrderItemEntity.create(order, 20L, 3, new BigDecimal("350000.00"));

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(pendingDelivery));
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PROCESSING), eq(OrderStatus.REFUNDED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(bundleItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(20L))))
                .thenReturn(Map.of(
                        20L, new ProductSnapshot(20L, "Holiday LED Bundle", ProductType.BUNDLE, null,
                                new BigDecimal("350000.00"), ProductStatus.PUBLISHED)
                ));
        when(bundleLookupService.findByProductId(20L))
                .thenReturn(Optional.of(new BundleSnapshot(
                        301L,
                        20L,
                        "Holiday LED Bundle",
                        new BigDecimal("350000.00"),
                        "ACTIVE",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"),
                        List.of(
                                new BundleSnapshot.BundleItemSnapshot(1L, BundleItemType.LED, 7L, null, 2),
                                new BundleSnapshot.BundleItemSnapshot(2L, BundleItemType.CHARACTER, null, 99L, 1)
                        )
                )));

        CancelOrderResponse response = useCase.execute(customerId, orderId, null);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.previousStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(response.updatedAt()).isNotNull();

        // 2 units of LED model 7 per bundle * 3 bundles = 6 units
        verify(ledInventoryService).restoreStocks(Map.of(7L, 6L));
    }

    @Test
    @DisplayName("COM-09 UTCID03 [N]: Customer cancels PENDING_PAYMENT digital order -> transitions to CANCELED without querying stock or restoring inventory")
    void execute_pendingPaymentDigitalOrder_transitionsToCanceledAndSkipsStockRestore() {
        Long customerId = 42L;
        Long orderId = 103L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PENDING_PAYMENT, OrderType.DIGITAL);

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);

        CancelOrderResponse response = useCase.execute(customerId, orderId, new CancelOrderRequest(null));

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.previousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);

        verifyNoInteractions(orderItemRepository, productLookupService, bundleLookupService, ledInventoryService);
    }

    @Test
    @DisplayName("COM-09 UTCID04 [A]: Throws OrderNotFoundException when order does not exist or belongs to another customer (IDOR)")
    void execute_missingOrOtherCustomerOrder_throwsOrderNotFoundException() {
        Long customerId = 42L;
        Long orderId = 999L;

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(customerId, orderId, null))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        verifyNoInteractions(orderDeliveryRepository, orderItemRepository, ledInventoryService);
    }

    @Test
    @DisplayName("COM-09 UTCID05 [A]: Rejects cancellation for disallowed statuses (SHIPPING, DELIVERED, COMPLETED, CANCELED, REFUNDED) with OrderStatusTransitionException")
    void execute_disallowedOrderStatus_throwsOrderStatusTransitionException() {
        Long customerId = 42L;
        OrderStatus[] disallowedStatuses = {
                OrderStatus.SHIPPING,
                OrderStatus.DELIVERED,
                OrderStatus.DELIVERY_FAILED,
                OrderStatus.COMPLETED,
                OrderStatus.CANCELED,
                OrderStatus.REFUNDED
        };

        for (OrderStatus status : disallowedStatuses) {
            OrderEntity order = sampleOrder(201L, customerId, status, OrderType.PHYSICAL);
            when(orderRepository.findByOrderIdAndCustomerId(201L, customerId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> useCase.execute(customerId, 201L, null))
                    .isInstanceOf(OrderStatusTransitionException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("Only PENDING_PAYMENT or PROCESSING orders can be cancelled.");
        }

        verifyNoInteractions(orderDeliveryRepository, orderItemRepository, ledInventoryService);
    }

    @Test
    @DisplayName("COM-09 UTCID06 [A]: Rejects cancellation if order already has an active delivery shipment or tracking number")
    void execute_activeShipmentOrTrackingNumber_throwsOrderStatusTransitionException() {
        Long customerId = 42L;
        Long orderId = 105L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PROCESSING, OrderType.PHYSICAL);

        // Case A: Active shipment status (e.g. ASSIGNED)
        OrderDeliveryEntity assignedDelivery = OrderDeliveryEntity.createPending(order, sampleShipping());
        assignedDelivery.setDeliveryStatus(DeliveryStatus.ASSIGNED);

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(assignedDelivery));

        assertThatThrownBy(() -> useCase.execute(customerId, orderId, null))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessage("Order cannot be cancelled because it has already been dispatched to the delivery partner.");

        // Case B: Pending delivery status but tracking number is already generated
        OrderDeliveryEntity trackingDelivery = OrderDeliveryEntity.createPending(order, sampleShipping());
        trackingDelivery.setTrackingNumber("AHA-TRACK-999");
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(trackingDelivery));

        assertThatThrownBy(() -> useCase.execute(customerId, orderId, null))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessage("Order cannot be cancelled because it has already been dispatched to the delivery partner.");

        verifyNoInteractions(orderItemRepository, ledInventoryService);
    }

    @Test
    @DisplayName("COM-09 UTCID07 [A]: Concurrent status modification (CAS updateStatusIfCurrent returns 0) throws OrderStatusTransitionException")
    void execute_concurrentUpdateConflict_throwsOrderStatusTransitionException() {
        Long customerId = 42L;
        Long orderId = 106L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(customerId, orderId, new CancelOrderRequest("Reason")))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("concurrently");

        verifyNoInteractions(orderItemRepository, ledInventoryService);
    }

    @Test
    @DisplayName("COM-09 UTCID08 [B]: Physical order with missing snapshots or non-LED products restores only valid LED quantities")
    void execute_missingSnapshotsOrNonLedProducts_restoresOnlyValidLedStock() {
        Long customerId = 42L;
        Long orderId = 107L;
        OrderEntity order = sampleOrder(orderId, customerId, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);

        OrderItemEntity ledItem = OrderItemEntity.create(order, 5L, 2, new BigDecimal("200000.00"));
        OrderItemEntity nonLedItem = OrderItemEntity.create(order, 10L, 1, new BigDecimal("50000.00"));
        OrderItemEntity missingItem = OrderItemEntity.create(order, 999L, 1, new BigDecimal("10000.00"));

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(ledItem, nonLedItem, missingItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(5L, 10L, 999L))))
                .thenReturn(Map.of(
                        5L, new ProductSnapshot(5L, "LED Matrix v1", ProductType.LED, null,
                                new BigDecimal("200000.00"), ProductStatus.PUBLISHED)
                ));

        CancelOrderResponse response = useCase.execute(customerId, orderId, new CancelOrderRequest("   "));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
        verify(ledInventoryService).restoreStocks(Map.of(5L, 2L));
    }
}
