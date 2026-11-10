package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.application.CheckoutOrderUseCase;
import com.lumintra.backend.commerce.domain.event.OrderCreatedEvent;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.model.BundleCheckoutInspection;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.service.PaymentIntentPort;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.iam.domain.model.UserAddressSnapshot;
import com.lumintra.backend.iam.domain.service.UserAddressLookupService;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DeliveryAddressRequiredException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.InvalidCartItemException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
import com.lumintra.backend.shared.exception.commerce.ProductNotAvailableException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutOrderUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private ProductAvailabilityPort productAvailabilityPort;

    @Mock
    private LedInventoryService ledInventoryService;

    @Mock
    private UserAddressLookupService userAddressLookupService;

    @Mock
    private PaymentIntentPort paymentIntentPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CheckoutOrderUseCase checkoutOrderUseCase;

    @BeforeEach
    void setUp() {
        checkoutOrderUseCase = new CheckoutOrderUseCase(
                cartRepository,
                cartItemRepository,
                orderRepository,
                orderItemRepository,
                orderDeliveryRepository,
                productLookupService,
                productAvailabilityPort,
                ledInventoryService,
                userAddressLookupService,
                paymentIntentPort,
                new OrderMapper(),
                eventPublisher
        );
    }

    @Test
    @DisplayName("UTCID05 [N]: AC03 & AC05 mixed physical + digital checkout with deliveryAddressId snapshots delivery, deducts stock, initializes SEPAY, and deletes only selected cart items")
    void execute_mixedCheckoutWithSavedAddress_createsOrderAndSnapshotsDelivery() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(1L, cart, 101L, 2, new BigDecimal("350000.00"), now);
        CartItemEntity charItem = CartItemEntity.of(2L, cart, 103L, 1, new BigDecimal("59000.00"), now);

        ProductSnapshot ledSnapshot = new ProductSnapshot(101L, "LED 32x32", ProductType.LED, null, new BigDecimal("350000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot charSnapshot = new ProductSnapshot(103L, "Cyber Dragon", ProductType.CHARACTER, null, new BigDecimal("59000.00"), ProductStatus.PUBLISHED);

        UserAddressSnapshot savedAddress = new UserAddressSnapshot(
                10L, customerId, "Nguyen Van A", "0901234567",
                "123 Nguyen Hue, D1, HCMC", new BigDecimal("10.7768890"), new BigDecimal("106.7008060")
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L, 2L)))
                .thenReturn(List.of(ledItem, charItem));
        when(productLookupService.findSnapshotsByIds(Set.of(101L, 103L)))
                .thenReturn(Map.of(101L, ledSnapshot, 103L, charSnapshot));
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 103L, ProductType.CHARACTER)).thenReturn(false);
        when(userAddressLookupService.findByIdAndCustomerId(10L, customerId)).thenReturn(Optional.of(savedAddress));
        when(ledInventoryService.deductStocksIfAvailable(Map.of(101L, 2L))).thenReturn(true);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setOrderId(1001L);
            return o;
        });
        when(paymentIntentPort.initializePaymentIntent(eq(1001L), eq(new BigDecimal("759000.00")), eq(PaymentGateway.SEPAY)))
                .thenReturn(new PaymentIntentResult(5001L, PaymentGateway.SEPAY, PaymentStatus.PENDING, "SEPAY-ORD-1001"));

        CheckoutResponse response = checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L, 2L), 10L, null, PaymentGateway.SEPAY)
        );

        assertThat(response.orderId()).isEqualTo(1001L);
        assertThat(response.paymentId()).isEqualTo(5001L);
        assertThat(response.paymentGateway()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.totalAmount()).isEqualByComparingTo("759000.00");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.expiresAt()).isAfter(now.plus(14, ChronoUnit.MINUTES));

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo(OrderType.MIXED);
        assertThat(orderCaptor.getValue().getShippingFee()).isEqualByComparingTo("0.00");
        assertThat(orderCaptor.getValue().getDeliveryAddressId()).isEqualTo(10L);

        ArgumentCaptor<OrderDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(OrderDeliveryEntity.class);
        verify(orderDeliveryRepository).save(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getValue().getCarrier()).isEqualTo("AHAMOVE");
        assertThat(deliveryCaptor.getValue().getRecipientName()).isEqualTo("Nguyen Van A");
        assertThat(deliveryCaptor.getValue().getLatitude()).isEqualByComparingTo("10.7768890");

        verify(orderItemRepository).saveAll(argThat(items -> ((List<?>) items).size() == 2));
        verify(cartItemRepository).deleteAllByIdInBatch(Set.of(1L, 2L));
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("UTCID06 [N]: AC03 inline shippingDetails snapshots ORDER_DELIVERY without saved address book ID")
    void execute_physicalCheckoutWithInlineShippingDetails_snapshotsDeliveryWithNullAddressId() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(1L, cart, 101L, 1, new BigDecimal("350000.00"), now);
        ProductSnapshot ledSnapshot = new ProductSnapshot(101L, "LED 32x32", ProductType.LED, null, new BigDecimal("350000.00"), ProductStatus.PUBLISHED);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L))).thenReturn(List.of(ledItem));
        when(productLookupService.findSnapshotsByIds(Set.of(101L))).thenReturn(Map.of(101L, ledSnapshot));
        when(ledInventoryService.deductStocksIfAvailable(Map.of(101L, 1L))).thenReturn(true);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setOrderId(1002L);
            return o;
        });
        when(paymentIntentPort.initializePaymentIntent(eq(1002L), eq(new BigDecimal("350000.00")), eq(PaymentGateway.SEPAY)))
                .thenReturn(new PaymentIntentResult(5002L, PaymentGateway.SEPAY, PaymentStatus.PENDING, "SEPAY-ORD-1002"));

        CheckoutRequest.ShippingDetailsRequest inlineShipping = new CheckoutRequest.ShippingDetailsRequest(
                "Tran Thi B",
                "0909998887",
                "456 Le Loi, District 1, HCMC",
                new BigDecimal("10.7720000"),
                new BigDecimal("106.6980000")
        );

        CheckoutResponse response = checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L), null, inlineShipping, PaymentGateway.SEPAY)
        );

        assertThat(response.orderId()).isEqualTo(1002L);
        assertThat(response.totalAmount()).isEqualByComparingTo("350000.00");
        verifyNoInteractions(userAddressLookupService);

        ArgumentCaptor<OrderDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(OrderDeliveryEntity.class);
        verify(orderDeliveryRepository).save(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getValue().getRecipientName()).isEqualTo("Tran Thi B");
        assertThat(deliveryCaptor.getValue().getOrder().getDeliveryAddressId()).isNull();
        assertThat(deliveryCaptor.getValue().getOrder().getOrderType()).isEqualTo(OrderType.PHYSICAL);
    }

    @Test
    @DisplayName("UTCID07 [N]: AC04 purely digital checkout bypasses shipping address and skips ORDER_DELIVERY")
    void execute_purelyDigitalCheckout_bypassesDeliveryAndStockDeduction() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity charItem = CartItemEntity.of(2L, cart, 103L, 1, new BigDecimal("59000.00"), now);
        ProductSnapshot charSnapshot = new ProductSnapshot(103L, "Cyber Dragon", ProductType.CHARACTER, null, new BigDecimal("59000.00"), ProductStatus.PUBLISHED);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(2L))).thenReturn(List.of(charItem));
        when(productLookupService.findSnapshotsByIds(Set.of(103L))).thenReturn(Map.of(103L, charSnapshot));
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 103L, ProductType.CHARACTER)).thenReturn(false);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setOrderId(1003L);
            return o;
        });
        when(paymentIntentPort.initializePaymentIntent(eq(1003L), eq(new BigDecimal("59000.00")), eq(PaymentGateway.SEPAY)))
                .thenReturn(new PaymentIntentResult(5003L, PaymentGateway.SEPAY, PaymentStatus.PENDING, "SEPAY-ORD-1003"));

        CheckoutResponse response = checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(2L), null, null, PaymentGateway.SEPAY)
        );

        assertThat(response.orderId()).isEqualTo(1003L);
        assertThat(response.totalAmount()).isEqualByComparingTo("59000.00");
        verifyNoInteractions(userAddressLookupService, ledInventoryService, orderDeliveryRepository);

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo(OrderType.DIGITAL);
        assertThat(orderCaptor.getValue().getDeliveryAddressId()).isNull();
    }

    @Test
    @DisplayName("UTCID08 [A]: AC02 throws InvalidCartItemException when cart items invalid and ProductNotAvailableException when product unpublished")
    void execute_invalidCartItemOrUnpublishedProduct_throwsAndDoesNotPersist() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity item = CartItemEntity.of(1L, cart, 107L, 1, new BigDecimal("500000.00"), now);
        ProductSnapshot unpublished = new ProductSnapshot(107L, "Draft LED", ProductType.LED, null, new BigDecimal("500000.00"), ProductStatus.UNPUBLISHED);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L, 999L)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L, 999L), 10L, null, PaymentGateway.SEPAY)))
                .isInstanceOf(InvalidCartItemException.class);

        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L)))
                .thenReturn(List.of(item));
        when(productLookupService.findSnapshotsByIds(Set.of(107L))).thenReturn(Map.of(107L, unpublished));

        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L), 10L, null, PaymentGateway.SEPAY)))
                .isInstanceOf(ProductNotAvailableException.class);

        verifyNoInteractions(orderRepository, orderItemRepository, ledInventoryService, paymentIntentPort);
    }

    @Test
    @DisplayName("UTCID09 [A]: AC03 throws DeliveryAddressRequiredException when shipping missing and AddressNotFoundException when address not owned")
    void execute_missingOrForeignDeliveryAddress_throwsExpectedExceptions() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(1L, cart, 101L, 1, new BigDecimal("350000.00"), now);
        ProductSnapshot ledSnapshot = new ProductSnapshot(101L, "LED 32x32", ProductType.LED, null, new BigDecimal("350000.00"), ProductStatus.PUBLISHED);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L))).thenReturn(List.of(ledItem));
        when(productLookupService.findSnapshotsByIds(Set.of(101L))).thenReturn(Map.of(101L, ledSnapshot));

        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L), null, null, PaymentGateway.SEPAY)))
                .isInstanceOf(DeliveryAddressRequiredException.class);

        when(userAddressLookupService.findByIdAndCustomerId(99L, customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L), 99L, null, PaymentGateway.SEPAY)))
                .isInstanceOf(AddressNotFoundException.class);

        verifyNoInteractions(ledInventoryService, orderRepository, paymentIntentPort);
    }

    @Test
    @DisplayName("UTCID10 [B/A]: AC03 & AC04 throws InsufficientStockException when atomic LED deduction fails across LED+BUNDLE and ProductAlreadyOwnedException for owned character")
    void execute_insufficientCombinedStockOrOwnedBundleCharacter_throwsAndAbortsCheckout() {
        Long customerId = 42L;
        Instant now = Instant.now();
        CartEntity cart = CartEntity.of(10L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(1L, cart, 101L, 2, new BigDecimal("350000.00"), now);
        CartItemEntity bundleItem = CartItemEntity.of(3L, cart, 106L, 2, new BigDecimal("390000.00"), now);

        ProductSnapshot ledSnapshot = new ProductSnapshot(101L, "LED 32x32", ProductType.LED, null, new BigDecimal("350000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot bundleSnapshot = new ProductSnapshot(106L, "Starter Combo", ProductType.BUNDLE, null, new BigDecimal("390000.00"), ProductStatus.PUBLISHED);
        UserAddressSnapshot savedAddress = new UserAddressSnapshot(
                10L, customerId, "Nguyen Van A", "0901234567",
                "123 Nguyen Hue, D1, HCMC", new BigDecimal("10.7768890"), new BigDecimal("106.7008060")
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(10L, Set.of(1L, 3L)))
                .thenReturn(List.of(ledItem, bundleItem));
        when(productLookupService.findSnapshotsByIds(Set.of(101L, 106L)))
                .thenReturn(Map.of(101L, ledSnapshot, 106L, bundleSnapshot));

        // Case 1: Combined LED stock deduction fails (2 standalone + 2 from bundle = 4 required)
        when(productAvailabilityPort.inspectBundleForCheckout(customerId, 106L, 2))
                .thenReturn(BundleCheckoutInspection.valid(true, Map.of(101L, 2L)));
        when(userAddressLookupService.findByIdAndCustomerId(10L, customerId)).thenReturn(Optional.of(savedAddress));
        when(ledInventoryService.deductStocksIfAvailable(Map.of(101L, 4L))).thenReturn(false);

        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L, 3L), 10L, null, PaymentGateway.SEPAY)))
                .isInstanceOf(InsufficientStockException.class);

        // Case 2: Bundle contains character already owned by customer
        when(productAvailabilityPort.inspectBundleForCheckout(customerId, 106L, 2))
                .thenReturn(BundleCheckoutInspection.unavailable(AvailabilityCheckResult.REASON_ALREADY_OWNED));

        assertThatThrownBy(() -> checkoutOrderUseCase.execute(
                customerId,
                new CheckoutRequest(List.of(1L, 3L), 10L, null, PaymentGateway.SEPAY)))
                .isInstanceOf(ProductAlreadyOwnedException.class);

        verifyNoInteractions(orderRepository, orderItemRepository, paymentIntentPort);
    }
}
