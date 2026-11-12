package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.UpdateStaffOrderStatusRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderStatusResponse;
import com.lumintra.backend.commerce.application.UpdateStaffOrderStatusUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateStaffOrderStatusUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private BundleLookupService bundleLookupService;

    @Mock
    private LedInventoryService ledInventoryService;

    private UpdateStaffOrderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateStaffOrderStatusUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderItemRepository,
                productLookupService,
                bundleLookupService,
                ledInventoryService,
                new OrderMapper()
        );
    }

    private OrderEntity sampleOrder(Long orderId, OrderStatus status, OrderType orderType) {
        return OrderEntity.of(
                orderId,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                orderType,
                status,
                Instant.parse("2026-11-12T12:00:00Z")
        );
    }

    @Test
    @DisplayName("COM-12 UTCID01 [N]: Active staff transitions DELIVERED->COMPLETED and DELIVERY_FAILED->PROCESSING without touching LED stock")
    void execute_deliveredToCompletedAndFailedToProcessing_succeedsWithoutStockChanges() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);

        OrderEntity deliveredOrder = sampleOrder(102L, OrderStatus.DELIVERED, OrderType.PHYSICAL);
        when(orderRepository.findById(102L)).thenReturn(Optional.of(deliveredOrder));
        when(orderRepository.updateStatusIfCurrent(
                eq(102L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);

        StaffOrderStatusResponse completedResponse = useCase.execute(
                "7", 102L, new UpdateStaffOrderStatusRequest("COMPLETED"));

        assertThat(completedResponse.orderId()).isEqualTo(102L);
        assertThat(completedResponse.previousStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(completedResponse.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completedResponse.updatedAt()).isNotNull();

        OrderEntity failedOrder = sampleOrder(103L, OrderStatus.DELIVERY_FAILED, OrderType.MIXED);
        when(orderRepository.findById(103L)).thenReturn(Optional.of(failedOrder));
        when(orderRepository.updateStatusIfCurrent(
                eq(103L), eq(OrderStatus.DELIVERY_FAILED), eq(OrderStatus.PROCESSING), any(Instant.class)))
                .thenReturn(1);

        StaffOrderStatusResponse retryResponse = useCase.execute(
                "7", 103L, new UpdateStaffOrderStatusRequest("PROCESSING"));

        assertThat(retryResponse.orderId()).isEqualTo(103L);
        assertThat(retryResponse.previousStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(retryResponse.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(retryResponse.updatedAt()).isNotNull();

        verifyNoInteractions(orderItemRepository, productLookupService, bundleLookupService, ledInventoryService);
    }

    @Test
    @DisplayName("COM-12 UTCID02 [N]: Canceling PENDING_PAYMENT order aggregates direct LED and BUNDLE LED component quantities and restores stock")
    void execute_pendingPaymentToCanceled_restoresDirectAndBundleLedStocks() {
        Long orderId = 104L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PENDING_PAYMENT, OrderType.MIXED);

        OrderItemEntity directLedItem = OrderItemEntity.create(order, 5L, 2, new BigDecimal("200000.00"));
        OrderItemEntity bundleItem = OrderItemEntity.create(order, 20L, 3, new BigDecimal("350000.00"));
        OrderItemEntity characterItem = OrderItemEntity.create(order, 8L, 1, new BigDecimal("50000.00"));

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(directLedItem, bundleItem, characterItem));

        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(5L, 20L, 8L))))
                .thenReturn(Map.of(
                        5L, new ProductSnapshot(5L, "LED Matrix v1", ProductType.LED, null,
                                new BigDecimal("200000.00"), ProductStatus.PUBLISHED),
                        20L, new ProductSnapshot(20L, "Holiday LED Bundle", ProductType.BUNDLE, null,
                                new BigDecimal("350000.00"), ProductStatus.UNPUBLISHED),
                        8L, new ProductSnapshot(8L, "Pixel Cat", ProductType.CHARACTER, null,
                                new BigDecimal("50000.00"), ProductStatus.PUBLISHED)
                ));

        when(bundleLookupService.findByProductId(20L))
                .thenReturn(Optional.of(new BundleSnapshot(
                        301L,
                        20L,
                        "Holiday LED Bundle",
                        new BigDecimal("350000.00"),
                        "EXPIRED",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z"),
                        List.of(
                                new BundleSnapshot.BundleItemSnapshot(1L, BundleItemType.LED, 5L, null, 1),
                                new BundleSnapshot.BundleItemSnapshot(2L, BundleItemType.LED, 9L, null, 2),
                                new BundleSnapshot.BundleItemSnapshot(3L, BundleItemType.CHARACTER, null, 77L, 1)
                        )
                )));

        StaffOrderStatusResponse response = useCase.execute(
                "7", orderId, new UpdateStaffOrderStatusRequest("CANCELLED"));

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.previousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(response.updatedAt()).isNotNull();

        // Model 5: 2 direct + (1 * 3) from bundle = 5; Model 9: (2 * 3) from bundle = 6
        verify(ledInventoryService).restoreStocks(Map.of(5L, 5L, 9L, 6L));
    }

    @Test
    @DisplayName("COM-12 UTCID03 [B]: Canceling digital-only or missing-snapshot orders completes without failing or restoring stock")
    void execute_cancelWithDigitalOnlyOrMissingSnapshots_skipsLedRestoreGracefully() {
        Long digitalOrderId = 105L;
        OrderEntity digitalOrder = sampleOrder(digitalOrderId, OrderStatus.PENDING_PAYMENT, OrderType.DIGITAL);
        OrderItemEntity subscriptionItem = OrderItemEntity.create(digitalOrder, 30L, 1, new BigDecimal("99000.00"));
        OrderItemEntity missingProductItem = OrderItemEntity.create(digitalOrder, 999L, 1, new BigDecimal("10000.00"));
        OrderItemEntity missingBundleItem = OrderItemEntity.create(digitalOrder, 40L, 2, new BigDecimal("150000.00"));

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(digitalOrderId)).thenReturn(Optional.of(digitalOrder));
        when(orderRepository.updateStatusIfCurrent(
                eq(digitalOrderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(digitalOrderId))
                .thenReturn(List.of(subscriptionItem, missingProductItem, missingBundleItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(30L, 999L, 40L))))
                .thenReturn(Map.of(
                        30L, new ProductSnapshot(30L, "Pro Monthly", ProductType.SUBSCRIPTION, null,
                                new BigDecimal("99000.00"), ProductStatus.PUBLISHED),
                        40L, new ProductSnapshot(40L, "Deleted Bundle", ProductType.BUNDLE, null,
                                new BigDecimal("150000.00"), ProductStatus.ARCHIVED)
                ));
        when(bundleLookupService.findByProductId(40L)).thenReturn(Optional.empty());

        StaffOrderStatusResponse response = useCase.execute(
                "7", digitalOrderId, new UpdateStaffOrderStatusRequest("CANCELED"));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(ledInventoryService);

        Long emptyOrderId = 106L;
        OrderEntity emptyOrder = sampleOrder(emptyOrderId, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);
        when(orderRepository.findById(emptyOrderId)).thenReturn(Optional.of(emptyOrder));
        when(orderRepository.updateStatusIfCurrent(
                eq(emptyOrderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(emptyOrderId))
                .thenReturn(Collections.emptyList());

        StaffOrderStatusResponse emptyResponse = useCase.execute(
                "7", emptyOrderId, new UpdateStaffOrderStatusRequest("CANCELED"));

        assertThat(emptyResponse.status()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(ledInventoryService);
    }

    @Test
    @DisplayName("COM-12 UTCID04 [A]: Disallowed transitions and concurrent status conflicts throw OrderStatusTransitionException before stock restoration")
    void execute_invalidTransitionOrConcurrentConflict_throwsTransitionExceptionWithoutRestoringStock() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);

        OrderEntity processingOrder = sampleOrder(107L, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        when(orderRepository.findById(107L)).thenReturn(Optional.of(processingOrder));

        assertThatThrownBy(() -> useCase.execute("7", 107L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessage("Cannot transition order status from PROCESSING to COMPLETED.");

        OrderEntity pendingOrder = sampleOrder(108L, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);
        when(orderRepository.findById(108L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.updateStatusIfCurrent(
                eq(108L), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.CANCELED), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute("7", 108L, new UpdateStaffOrderStatusRequest("CANCELLED")))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("concurrently");

        verifyNoInteractions(orderItemRepository, productLookupService, bundleLookupService, ledInventoryService);
    }

    @Test
    @DisplayName("COM-12 UTCID05 [A]: Denies stale/invalid employees, throws OrderNotFoundException when missing, and propagates storage failures")
    void execute_unauthorizedNotFoundOrStorageFailure_throwsExpectedException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute("7", 102L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .isInstanceOf(AccessDeniedException.class);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("bad", ALLOWED_ROLES))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        assertThatThrownBy(() -> useCase.execute("bad", 102L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(orderRepository, orderItemRepository, productLookupService, bundleLookupService, ledInventoryService);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("8", ALLOWED_ROLES)).thenReturn(8L);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("8", 999L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        when(orderRepository.findById(109L))
                .thenThrow(new IllegalStateException("PostgreSQL timeout"));

        assertThatThrownBy(() -> useCase.execute("8", 109L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL timeout");
    }
}
