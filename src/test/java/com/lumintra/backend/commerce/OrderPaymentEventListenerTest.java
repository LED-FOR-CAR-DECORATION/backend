package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.application.listener.OrderPaymentEventListener;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OwnedCharacterRepository ownedCharacterRepository;

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private BundleLookupService bundleLookupService;

    private OrderPaymentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaymentEventListener(
                orderRepository,
                orderItemRepository,
                ownedCharacterRepository,
                customerSubscriptionRepository,
                productLookupService,
                characterLookupService,
                bundleLookupService
        );
    }

    @Test
    @DisplayName("OPL-01 [N]: Physical order transitions to PROCESSING upon payment confirmation")
    void onPaymentSucceeded_physicalOrder_transitionsToProcessing() {
        Long orderId = 100L;
        Long customerId = 42L;

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                10L,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                Instant.now().plusSeconds(900)
        );

        OrderItemEntity item = OrderItemEntity.create(order, 1L, 1, new BigDecimal("500000.00"));
        item.setOrderItemId(201L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.PROCESSING), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(item));
        when(productLookupService.findSnapshotsByIds(Set.of(1L))).thenReturn(Map.of(
                1L, new ProductSnapshot(1L, "LED Display Matrix", ProductType.LED, null, new BigDecimal("500000.00"), ProductStatus.PUBLISHED)
        ));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                1L, "LMTORD100", PaymentTargetType.ORDER, orderId, customerId,
                new BigDecimal("500000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verify(orderRepository).updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.PROCESSING), any(Instant.class));
        verifyNoInteractions(ownedCharacterRepository);
        verifyNoInteractions(customerSubscriptionRepository);
    }

    @Test
    @DisplayName("OPL-02 [N]: Mixed order transitions to PARTIALLY_FULFILLED and provisions digital items")
    void onPaymentSucceeded_mixedOrder_transitionsToPartiallyFulfilledAndProvisionsDigital() {
        Long orderId = 101L;
        Long customerId = 42L;

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                10L,
                new BigDecimal("650000.00"),
                BigDecimal.ZERO,
                new BigDecimal("650000.00"),
                OrderType.MIXED,
                OrderStatus.PENDING_PAYMENT,
                Instant.now().plusSeconds(900)
        );

        OrderItemEntity ledItem = OrderItemEntity.create(order, 1L, 1, new BigDecimal("500000.00"));
        ledItem.setOrderItemId(201L);

        OrderItemEntity charItem = OrderItemEntity.create(order, 2L, 1, new BigDecimal("100000.00"));
        charItem.setOrderItemId(202L);

        OrderItemEntity subItem = OrderItemEntity.create(order, 3L, 1, new BigDecimal("50000.00"));
        subItem.setOrderItemId(203L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.PARTIALLY_FULFILLED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(ledItem, charItem, subItem));

        when(productLookupService.findSnapshotsByIds(Set.of(1L, 2L, 3L))).thenReturn(Map.of(
                1L, new ProductSnapshot(1L, "LED Matrix", ProductType.LED, null, new BigDecimal("500000.00"), ProductStatus.PUBLISHED),
                2L, new ProductSnapshot(2L, "Cyber Cat", ProductType.CHARACTER, null, new BigDecimal("100000.00"), ProductStatus.PUBLISHED),
                3L, new ProductSnapshot(3L, "Lumintra Pro", ProductType.SUBSCRIPTION, null, new BigDecimal("50000.00"), ProductStatus.PUBLISHED)
        ));

        when(characterLookupService.findVersionIdByProductId(2L)).thenReturn(Optional.of(301L));
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, 301L)).thenReturn(false);
        when(customerSubscriptionRepository.existsByCustomerIdAndStatus(customerId, "ACTIVE")).thenReturn(false);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                2L, "LMTORD101", PaymentTargetType.ORDER, orderId, customerId,
                new BigDecimal("650000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verify(orderRepository).updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.PARTIALLY_FULFILLED), any(Instant.class));

        ArgumentCaptor<OwnedCharacterEntity> charCaptor = ArgumentCaptor.forClass(OwnedCharacterEntity.class);
        verify(ownedCharacterRepository).save(charCaptor.capture());
        assertThat(charCaptor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(charCaptor.getValue().getVersionId()).isEqualTo(301L);
        assertThat(charCaptor.getValue().getOrderItemId()).isEqualTo(202L);

        ArgumentCaptor<CustomerSubscriptionEntity> subCaptor = ArgumentCaptor.forClass(CustomerSubscriptionEntity.class);
        verify(customerSubscriptionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(subCaptor.getValue().getProductId()).isEqualTo(3L);
        assertThat(subCaptor.getValue().getOrderItemId()).isEqualTo(203L);
    }

    @Test
    @DisplayName("OPL-03 [N]: Digital order transitions to COMPLETED and provisions bundle character items")
    void onPaymentSucceeded_digitalOrder_transitionsToCompletedAndProvisionsBundle() {
        Long orderId = 102L;
        Long customerId = 42L;

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                null,
                new BigDecimal("300000.00"),
                BigDecimal.ZERO,
                new BigDecimal("300000.00"),
                OrderType.DIGITAL,
                OrderStatus.PENDING_PAYMENT,
                Instant.now().plusSeconds(900)
        );

        OrderItemEntity bundleItem = OrderItemEntity.create(order, 5L, 1, new BigDecimal("300000.00"));
        bundleItem.setOrderItemId(205L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(bundleItem));

        when(productLookupService.findSnapshotsByIds(Set.of(5L))).thenReturn(Map.of(
                5L, new ProductSnapshot(5L, "Animation Pack", ProductType.BUNDLE, null, new BigDecimal("300000.00"), ProductStatus.PUBLISHED)
        ));

        BundleSnapshot bundleSnapshot = new BundleSnapshot(
                10L, 5L, "Animation Pack", new BigDecimal("300000.00"), "PUBLISHED", null, null,
                List.of(
                        new BundleSnapshot.BundleItemSnapshot(1L, BundleItemType.CHARACTER, null, 401L, 1),
                        new BundleSnapshot.BundleItemSnapshot(2L, BundleItemType.CHARACTER, null, 402L, 1)
                )
        );
        when(bundleLookupService.findByProductId(5L)).thenReturn(Optional.of(bundleSnapshot));
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, 401L)).thenReturn(false);
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, 402L)).thenReturn(false);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                3L, "LMTORD102", PaymentTargetType.ORDER, orderId, customerId,
                new BigDecimal("300000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verify(orderRepository).updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.COMPLETED), any(Instant.class));
        verify(ownedCharacterRepository, times(2)).save(any(OwnedCharacterEntity.class));
    }

    @Test
    @DisplayName("OPL-04 [B]: Ignores non-order target type events (e.g. commission payments)")
    void onPaymentSucceeded_nonOrderTargetType_ignored() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                4L, "LMTCMS50", PaymentTargetType.COMMISSION_INITIAL, 50L, 42L,
                new BigDecimal("100000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    @DisplayName("OPL-05 [B]: Ignores order already in non-pending status")
    void onPaymentSucceeded_alreadyProcessedOrder_skipsUpdate() {
        Long orderId = 103L;
        OrderEntity order = OrderEntity.of(
                orderId,
                42L,
                10L,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                5L, "LMTORD103", PaymentTargetType.ORDER, orderId, 42L,
                new BigDecimal("500000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verify(orderRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    @DisplayName("OPL-06 [B]: Skips digital grant if already owned or subscription already active")
    void onPaymentSucceeded_alreadyOwned_skipsSave() {
        Long orderId = 104L;
        Long customerId = 42L;

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                null,
                new BigDecimal("150000.00"),
                BigDecimal.ZERO,
                new BigDecimal("150000.00"),
                OrderType.DIGITAL,
                OrderStatus.PENDING_PAYMENT,
                Instant.now().plusSeconds(900)
        );

        OrderItemEntity charItem = OrderItemEntity.create(order, 2L, 1, new BigDecimal("100000.00"));
        charItem.setOrderItemId(202L);

        OrderItemEntity subItem = OrderItemEntity.create(order, 3L, 1, new BigDecimal("50000.00"));
        subItem.setOrderItemId(203L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(charItem, subItem));

        when(productLookupService.findSnapshotsByIds(Set.of(2L, 3L))).thenReturn(Map.of(
                2L, new ProductSnapshot(2L, "Cyber Cat", ProductType.CHARACTER, null, new BigDecimal("100000.00"), ProductStatus.PUBLISHED),
                3L, new ProductSnapshot(3L, "Lumintra Pro", ProductType.SUBSCRIPTION, null, new BigDecimal("50000.00"), ProductStatus.PUBLISHED)
        ));

        when(characterLookupService.findVersionIdByProductId(2L)).thenReturn(Optional.of(301L));
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, 301L)).thenReturn(true);
        when(customerSubscriptionRepository.existsByCustomerIdAndStatus(customerId, "ACTIVE")).thenReturn(true);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                6L, "LMTORD104", PaymentTargetType.ORDER, orderId, customerId,
                new BigDecimal("150000.00"), 9999L, "REF123", Instant.now()
        );

        listener.onPaymentSucceeded(event);

        verify(orderRepository).updateStatusIfCurrent(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.COMPLETED), any(Instant.class));
        verify(ownedCharacterRepository, never()).save(any());
        verify(customerSubscriptionRepository, never()).save(any());
    }
}
