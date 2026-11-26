package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.application.GetStaffOrderDetailUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStaffOrderDetailUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private CustomerLookupService customerLookupService;

    private GetStaffOrderDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetStaffOrderDetailUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderItemRepository,
                orderDeliveryRepository,
                paymentTransactionRepository,
                productLookupService,
                characterLookupService,
                customerLookupService,
                new OrderMapper()
        );
    }

    @Test
    @DisplayName("COM-11 UTCID01 [N]: Active staff retrieves MIXED order detail with customer contact, snapshotted delivery, line items, and settled payment reconciliation")
    void execute_existingProcessingMixedOrder_returnsFullStaffOrderDetail() {
        Long orderId = 102L;
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant updatedAt = Instant.parse("2026-11-10T16:50:00Z");
        Instant paidAt = Instant.parse("2026-11-10T16:48:12Z");
        Instant deliveredAt = Instant.parse("2026-11-11T09:42:00Z");

        OrderEntity order = OrderEntity.of(
                orderId,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.MIXED,
                OrderStatus.PROCESSING,
                createdAt.plusSeconds(900)
        );
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(updatedAt);

        OrderItemEntity ledItem = OrderItemEntity.create(order, 5L, 1, new BigDecimal("200000.00"));
        ledItem.setOrderItemId(1L);
        OrderItemEntity characterItem = OrderItemEntity.create(order, 8L, 1, new BigDecimal("50000.00"));
        characterItem.setOrderItemId(2L);

        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L,
                        "Dang Phuoc Nguyen",
                        "0912345678",
                        "Saigon Hi-Tech Park, Thu Duc City, HCMC",
                        new BigDecimal("10.8411000"),
                        new BigDecimal("106.8099000")
                )
        );
        delivery.setDeliveryStatus(DeliveryStatus.DELIVERED);
        delivery.setTrackingNumber("AHV-2026-99881");
        delivery.setTrackingUrl("https://ahamove.com/track/AHV-2026-99881");
        delivery.setCarrierFee(new BigDecimal("32000.00"));
        delivery.setDeliveredAt(deliveredAt);

        PaymentTransactionEntity payment = PaymentTransactionEntity.of(
                5001L,
                orderId,
                PaymentGateway.SEPAY,
                "SEPAY-FT263149981",
                new BigDecimal("250000.00"),
                PaymentStatus.SUCCESS
        );
        payment.setPaidAt(paidAt);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(ledItem, characterItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(5L, 8L))))
                .thenReturn(Map.of(
                        5L, new ProductSnapshot(5L, "Lumintra LED Matrix v1", ProductType.LED,
                                "https://cdn.lumintra.com/products/led-v1.png", new BigDecimal("200000.00"), ProductStatus.PUBLISHED),
                        8L, new ProductSnapshot(8L, "Pixel Cat Character", ProductType.CHARACTER,
                                "https://cdn.lumintra.com/products/cat.png", new BigDecimal("50000.00"), ProductStatus.PUBLISHED)
                ));
        when(characterLookupService.findPreviewGifUrlsByProductIds(Set.of(8L)))
                .thenReturn(Map.of(8L, "https://cdn.lumintra.com/characters/cat-preview.gif"));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(delivery));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.of(payment));
        when(customerLookupService.findProfileByCustomerId(15L))
                .thenReturn(Optional.of(new CustomerProfileSnapshot(15L, "Nguyen Van A", "0909111222", "customer@example.com")));

        StaffOrderDetailResponse response = useCase.execute("7", orderId);

        assertThat(response.orderId()).isEqualTo(102L);
        assertThat(response.orderType()).isEqualTo(OrderType.MIXED);
        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.subtotal()).isEqualByComparingTo("250000.00");
        assertThat(response.shippingFee()).isEqualByComparingTo("0.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("250000.00");
        assertThat(response.expiresAt()).isNull();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);

        assertThat(response.customer()).isEqualTo(new StaffOrderDetailResponse.CustomerSummary(
                15L, "Nguyen Van A", "0909111222", "customer@example.com"
        ));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).productName()).isEqualTo("Lumintra LED Matrix v1");
        assertThat(response.items().get(0).previewGifUrl()).isNull();
        assertThat(response.items().get(1).productType()).isEqualTo(ProductType.CHARACTER);
        assertThat(response.items().get(1).previewGifUrl()).isEqualTo("https://cdn.lumintra.com/characters/cat-preview.gif");

        assertThat(response.delivery()).isNotNull();
        assertThat(response.delivery().carrier()).isEqualTo("AHAMOVE");
        assertThat(response.delivery().recipientName()).isEqualTo("Dang Phuoc Nguyen");
        assertThat(response.delivery().recipientPhone()).isEqualTo("0912345678");
        assertThat(response.delivery().fullAddress()).isEqualTo("Saigon Hi-Tech Park, Thu Duc City, HCMC");
        assertThat(response.delivery().deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(response.delivery().trackingNumber()).isEqualTo("AHV-2026-99881");
        assertThat(response.delivery().carrierFee()).isEqualByComparingTo("32000.00");
        assertThat(response.delivery().deliveredAt()).isEqualTo(deliveredAt);

        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().paymentGateway()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.payment().bankReferenceCode()).isEqualTo("SEPAY-FT263149981");
        assertThat(response.payment().paidAmount()).isEqualByComparingTo("250000.00");
        assertThat(response.payment().paidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("COM-11 UTCID02 [N]: PENDING_PAYMENT order preserves expiresAt while hiding unsettled payment reference fields")
    void execute_pendingPaymentOrder_exposesOrderExpiresAtAndWithholdsSettledPaymentFields() {
        Long orderId = 105L;
        Instant createdAt = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant expiresAt = createdAt.plus(15, ChronoUnit.MINUTES);

        OrderEntity order = OrderEntity.of(
                orderId,
                15L,
                null,
                new BigDecimal("350000.00"),
                new BigDecimal("25000.00"),
                new BigDecimal("375000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                expiresAt
        );
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);

        PaymentTransactionEntity pendingPayment = PaymentTransactionEntity.of(
                5002L,
                orderId,
                PaymentGateway.SEPAY,
                "SEPAY-ORD-105-TEMP1234",
                new BigDecimal("375000.00"),
                PaymentStatus.PENDING
        );

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(Collections.emptyList());
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.of(pendingPayment));
        when(customerLookupService.findProfileByCustomerId(15L))
                .thenReturn(Optional.of(new CustomerProfileSnapshot(15L, "Nguyen Van A", "0909111222", "customer@example.com")));

        StaffOrderDetailResponse response = useCase.execute("7", orderId);

        verifyNoInteractions(productLookupService, characterLookupService);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.delivery()).isNull();
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().paymentGateway()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.payment().bankReferenceCode()).isNull();
        assertThat(response.payment().paidAmount()).isNull();
        assertThat(response.payment().paidAt()).isNull();
    }

    @Test
    @DisplayName("COM-11 UTCID03 [B]: Missing IAM customer profile, missing product snapshot, and null delivery/payment degrade safely without errors")
    void execute_missingEnrichmentData_usesSafeFallbacks() {
        Long orderId = 109L;
        Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");

        OrderEntity order = OrderEntity.of(
                orderId,
                88L,
                null,
                new BigDecimal("490000.00"),
                BigDecimal.ZERO,
                new BigDecimal("490000.00"),
                OrderType.PHYSICAL,
                OrderStatus.CANCELED,
                createdAt.plusSeconds(900)
        );
        order.setCreatedAt(createdAt);

        OrderItemEntity orphanItem = OrderItemEntity.create(order, 999L, 2, new BigDecimal("245000.00"));
        orphanItem.setOrderItemId(77L);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(orphanItem));
        when(productLookupService.findSnapshotsByIds(Set.of(999L))).thenReturn(Collections.emptyMap());
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.empty());
        when(customerLookupService.findProfileByCustomerId(88L)).thenReturn(Optional.empty());

        StaffOrderDetailResponse response = useCase.execute("7", orderId);

        verifyNoInteractions(characterLookupService);
        assertThat(response.expiresAt()).isNull();
        assertThat(response.delivery()).isNull();
        assertThat(response.payment()).isNull();
        assertThat(response.customer()).isEqualTo(new StaffOrderDetailResponse.CustomerSummary(88L, null, null, null));
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Unavailable Product");
            assertThat(item.productType()).isNull();
            assertThat(item.thumbnailUrl()).isNull();
            assertThat(item.previewGifUrl()).isNull();
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("490000.00");
        });
    }

    @Test
    @DisplayName("COM-11 UTCID04 [A]: Throws OrderNotFoundException when missing and denies stale/unauthorized employees before querying order")
    void execute_unauthorizedOrNotFound_throwsExpectedExceptions() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute("7", 102L))
                .isInstanceOf(AccessDeniedException.class);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("bad", ALLOWED_ROLES))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        assertThatThrownBy(() -> useCase.execute("bad", 102L))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(orderRepository, orderItemRepository, orderDeliveryRepository,
                paymentTransactionRepository, productLookupService, characterLookupService, customerLookupService);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("8", ALLOWED_ROLES)).thenReturn(8L);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("8", 999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        verifyNoInteractions(orderItemRepository, orderDeliveryRepository,
                paymentTransactionRepository, productLookupService, characterLookupService, customerLookupService);
    }

    @Test
    @DisplayName("COM-11 UTCID05 [A]: Database failure propagates cleanly instead of swallowing into null or partial DTO")
    void execute_repositoryFailure_propagatesException() {
        Long orderId = 110L;
        OrderEntity order = OrderEntity.of(
                orderId,
                15L,
                null,
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                new BigDecimal("100000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                Instant.now()
        );

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenThrow(new IllegalStateException("PostgreSQL connection reset"));

        assertThatThrownBy(() -> useCase.execute("7", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL connection reset");
    }
}
