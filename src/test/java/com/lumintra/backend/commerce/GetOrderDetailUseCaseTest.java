package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.OrderDetailResponse;
import com.lumintra.backend.commerce.application.GetOrderDetailUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;
import com.lumintra.backend.commerce.domain.service.PaymentInstructionPort;
import com.lumintra.backend.commerce.infrastructure.integration.SepayPaymentInstructionAdapter;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderDetailUseCaseTest {

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
    private PaymentInstructionPort paymentInstructionPort;

    private GetOrderDetailUseCase getOrderDetailUseCase;

    @BeforeEach
    void setUp() {
        getOrderDetailUseCase = new GetOrderDetailUseCase(
                orderRepository,
                orderItemRepository,
                orderDeliveryRepository,
                paymentTransactionRepository,
                productLookupService,
                characterLookupService,
                paymentInstructionPort,
                new OrderMapper()
        );
    }

    @Test
    @DisplayName("UTCID05 [N]: AC01-AC04 retrieves active PENDING_PAYMENT MIXED order with Character previewGifUrl, AhaMove delivery, and SePay VietQR retry info")
    void execute_activePendingMixedOrder_returnsFullDetailWithVietQrAndPreviewGif() {
        Long customerId = 42L;
        Long orderId = 102L;
        Instant createdAt = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plus(12, ChronoUnit.MINUTES);

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                10L,
                new BigDecimal("250000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("250000.00"),
                OrderType.MIXED,
                OrderStatus.PENDING_PAYMENT,
                expiresAt
        );
        order.setCreatedAt(createdAt);

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
        delivery.setTrackingNumber("AHV-2026-99881");
        delivery.setTrackingUrl("https://ahamove.com/track/AHV-2026-99881");

        PaymentTransactionEntity payment = PaymentTransactionEntity.of(
                5001L,
                orderId,
                PaymentGateway.SEPAY,
                "LMTORD102TEMP1234",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING
        );

        SepayPaymentInstructions sepayInstructions = new SepayPaymentInstructions(
                "LMTORD102TEMP1234",
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LMTORD102TEMP1234&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "10006140844",
                "TPBank",
                expiresAt
        );

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId))
                .thenReturn(List.of(ledItem, characterItem));
        when(productLookupService.findSnapshotsByIds(new LinkedHashSet<>(List.of(5L, 8L))))
                .thenReturn(Map.of(
                        5L, new ProductSnapshot(5L, "Lumintra LED Matrix v1", ProductType.LED,
                                "https://cdn.lumintra.com/products/led.png", new BigDecimal("200000.00"), ProductStatus.PUBLISHED),
                        8L, new ProductSnapshot(8L, "Pixel Cat Character", ProductType.CHARACTER,
                                "https://cdn.lumintra.com/products/cat.png", new BigDecimal("50000.00"), ProductStatus.PUBLISHED)
                ));
        when(characterLookupService.findPreviewGifUrlsByProductIds(Set.of(8L)))
                .thenReturn(Map.of(8L, "https://cdn.lumintra.com/characters/cat-preview.gif"));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(delivery));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.of(payment));
        when(paymentInstructionPort.buildInstructions(orderId, "LMTORD102TEMP1234", new BigDecimal("250000.00"), expiresAt))
                .thenReturn(sepayInstructions);

        OrderDetailResponse response = getOrderDetailUseCase.execute(customerId, orderId);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderType()).isEqualTo(OrderType.MIXED);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).previewGifUrl()).isNull();
        assertThat(response.items().get(1).productType()).isEqualTo(ProductType.CHARACTER);
        assertThat(response.items().get(1).previewGifUrl()).isEqualTo("https://cdn.lumintra.com/characters/cat-preview.gif");

        assertThat(response.delivery()).isNotNull();
        assertThat(response.delivery().carrier()).isEqualTo("AHAMOVE");
        assertThat(response.delivery().deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(response.delivery().trackingNumber()).isEqualTo("AHV-2026-99881");

        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().paymentGateway()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.payment().paymentContent()).isEqualTo("LMTORD102TEMP1234");
        assertThat(response.payment().qrCodeUrl()).isEqualTo("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LMTORD102TEMP1234&showinfo=true&holder=NGUYEN%20QUANG%20KHANH");
        assertThat(response.payment().bankAccount()).isEqualTo("10006140844");
        assertThat(response.payment().bankName()).isEqualTo("TPBank");
        assertThat(response.payment().expiresAt()).isEqualTo(expiresAt);
        assertThat(response.payment().bankReferenceCode()).isNull();
        assertThat(response.payment().paidAmount()).isNull();
        assertThat(response.payment().paidAt()).isNull();
    }

    @Test
    @DisplayName("UTCID06 [N]: AC03 & AC05 COMPLETED DIGITAL order returns null delivery, null expiresAt, and settled payment confirmation without VietQR")
    void execute_completedDigitalOrder_returnsSettledPaymentWithoutDeliveryOrVietQr() {
        Long customerId = 42L;
        Long orderId = 101L;
        Instant createdAt = Instant.parse("2026-11-09T10:00:00Z");
        Instant originalExpiresAt = Instant.parse("2026-11-09T10:15:00Z");
        Instant paidAt = Instant.parse("2026-11-09T10:02:15Z");

        OrderEntity order = OrderEntity.of(
                orderId,
                customerId,
                null,
                new BigDecimal("59000.00"),
                BigDecimal.ZERO,
                new BigDecimal("59000.00"),
                OrderType.DIGITAL,
                OrderStatus.COMPLETED,
                originalExpiresAt
        );
        order.setCreatedAt(createdAt);

        OrderItemEntity characterItem = OrderItemEntity.create(order, 203L, 1, new BigDecimal("59000.00"));
        characterItem.setOrderItemId(10L);

        PaymentTransactionEntity settledPayment = PaymentTransactionEntity.of(
                5000L,
                orderId,
                PaymentGateway.SEPAY,
                "SEPAY-FT263149981",
                new BigDecimal("59000.00"),
                PaymentStatus.SUCCESS
        );
        settledPayment.setPaidAt(paidAt);

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(characterItem));
        when(productLookupService.findSnapshotsByIds(Set.of(203L)))
                .thenReturn(Map.of(203L, new ProductSnapshot(203L, "Cyber Dragon", ProductType.CHARACTER,
                        "https://cdn.lumintra.com/products/dragon.png", new BigDecimal("59000.00"), ProductStatus.PUBLISHED)));
        when(characterLookupService.findPreviewGifUrlsByProductIds(Set.of(203L)))
                .thenReturn(Map.of(203L, "https://cdn.lumintra.com/characters/dragon-preview.gif"));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.of(settledPayment));

        OrderDetailResponse response = getOrderDetailUseCase.execute(customerId, orderId);

        verifyNoInteractions(paymentInstructionPort);
        assertThat(response.expiresAt()).isNull();
        assertThat(response.delivery()).isNull();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Cyber Dragon");
            assertThat(item.previewGifUrl()).isEqualTo("https://cdn.lumintra.com/characters/dragon-preview.gif");
        });
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.payment().bankReferenceCode()).isEqualTo("SEPAY-FT263149981");
        assertThat(response.payment().paidAmount()).isEqualByComparingTo("59000.00");
        assertThat(response.payment().paidAt()).isEqualTo(paidAt);
        assertThat(response.payment().paymentContent()).isNull();
        assertThat(response.payment().qrCodeUrl()).isNull();
        assertThat(response.payment().bankAccount()).isNull();
        assertThat(response.payment().bankName()).isNull();
        assertThat(response.payment().expiresAt()).isNull();
    }

    @Test
    @DisplayName("UTCID07 [B]: Expired PENDING_PAYMENT order (expiresAt <= now) omits VietQR retry instructions")
    void execute_expiredPendingPaymentOrder_omitsVietQrRetryInstructions() {
        Long customerId = 42L;
        Long orderId = 103L;
        Instant expiredAt = Instant.now().minus(5, ChronoUnit.MINUTES);

        OrderEntity expiredOrder = OrderEntity.of(
                orderId,
                customerId,
                null,
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                new BigDecimal("100000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                expiredAt
        );
        expiredOrder.setCreatedAt(expiredAt.minus(15, ChronoUnit.MINUTES));

        PaymentTransactionEntity pendingPayment = PaymentTransactionEntity.of(
                5002L,
                orderId,
                PaymentGateway.SEPAY,
                "SEPAY-ORD-103-TEMP9999",
                new BigDecimal("100000.00"),
                PaymentStatus.PENDING
        );

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(expiredOrder));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(Collections.emptyList());
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.of(pendingPayment));

        OrderDetailResponse response = getOrderDetailUseCase.execute(customerId, orderId);

        verifyNoInteractions(productLookupService, characterLookupService, paymentInstructionPort);
        assertThat(response.expiresAt()).isEqualTo(expiredAt);
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.payment().expiresAt()).isEqualTo(expiredAt);
        assertThat(response.payment().paymentContent()).isNull();
        assertThat(response.payment().qrCodeUrl()).isNull();
        assertThat(response.payment().bankAccount()).isNull();
        assertThat(response.payment().bankName()).isNull();
        assertThat(response.payment().bankReferenceCode()).isNull();
    }

    @Test
    @DisplayName("UTCID08 [B]: Physical-only order or missing product snapshot skips character lookup and uses safe fallback values")
    void execute_missingProductSnapshotAndNoCharacters_skipsCharacterLookupAndFallsBack() {
        Long customerId = 42L;
        Long orderId = 104L;
        Instant createdAt = Instant.parse("2026-11-10T08:00:00Z");

        OrderEntity canceledOrder = OrderEntity.of(
                orderId,
                customerId,
                9L,
                new BigDecimal("490000.00"),
                BigDecimal.ZERO,
                new BigDecimal("490000.00"),
                OrderType.PHYSICAL,
                OrderStatus.CANCELED,
                createdAt.plusSeconds(900)
        );
        canceledOrder.setCreatedAt(createdAt);

        OrderItemEntity orphanItem = OrderItemEntity.create(canceledOrder, 999L, 2, new BigDecimal("245000.00"));
        orphanItem.setOrderItemId(77L);

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(canceledOrder));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(orphanItem));
        when(productLookupService.findSnapshotsByIds(Set.of(999L))).thenReturn(Collections.emptyMap());
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId))
                .thenReturn(Optional.empty());

        OrderDetailResponse response = getOrderDetailUseCase.execute(customerId, orderId);

        verifyNoInteractions(characterLookupService, paymentInstructionPort);
        assertThat(response.expiresAt()).isNull();
        assertThat(response.payment()).isNull();
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
    @DisplayName("UTCID09 [N]: SepayPaymentInstructionAdapter delegates to PaymentGatewayPort and constructs VietQR URL")
    void sepayAdapter_buildInstructions_constructsExpectedQrCodeUrl() {
        com.lumintra.backend.payment.infrastructure.integration.SePayProperties properties =
                new com.lumintra.backend.payment.infrastructure.integration.SePayProperties(
                        "https://vietqr.app/img",
                        "TPBank",
                        "10006140844",
                        "NGUYEN QUANG KHANH",
                        "LUM",
                        "secret"
                );
        SepayPaymentInstructionAdapter adapter = new SepayPaymentInstructionAdapter(
                new com.lumintra.backend.payment.infrastructure.integration.SePayGatewayAdapter(properties),
                properties
        );
        Instant expiresAt = Instant.parse("2026-11-10T17:00:00Z");

        SepayPaymentInstructions withTransactionCode =
                adapter.buildInstructions(102L, "LUMORD102A1B2C3D4", new BigDecimal("250000.00"), expiresAt);

        assertThat(withTransactionCode.paymentContent()).isEqualTo("LUMORD102A1B2C3D4");
        assertThat(withTransactionCode.bankAccount()).isEqualTo("10006140844");
        assertThat(withTransactionCode.bankName()).isEqualTo("TPBank");
        assertThat(withTransactionCode.expiresAt()).isEqualTo(expiresAt);
        assertThat(withTransactionCode.qrCodeUrl())
                .isEqualTo("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LUMORD102A1B2C3D4&showinfo=true&holder=NGUYEN%20QUANG%20KHANH");

        SepayPaymentInstructions fallbackResult = adapter.buildInstructions(102L, null, new BigDecimal("250000.00"), expiresAt);

        assertThat(fallbackResult.paymentContent()).isEqualTo("LUM102");
        assertThat(fallbackResult.qrCodeUrl())
                .isEqualTo("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LUM102&showinfo=true&holder=NGUYEN%20QUANG%20KHANH");
    }

    @Test
    @DisplayName("UTCID10 [A]: Throws OrderNotFoundException on missing/other-customer order and propagates unexpected DB errors")
    void execute_notFoundOrDbFailure_throwsWithoutSwallowing() {
        when(orderRepository.findByOrderIdAndCustomerId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getOrderDetailUseCase.execute(42L, 999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        verifyNoInteractions(
                orderItemRepository,
                orderDeliveryRepository,
                paymentTransactionRepository,
                productLookupService,
                characterLookupService,
                paymentInstructionPort
        );

        OrderEntity ownedOrder = OrderEntity.of(
                105L,
                42L,
                null,
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                new BigDecimal("100000.00"),
                OrderType.DIGITAL,
                OrderStatus.COMPLETED,
                Instant.now()
        );
        when(orderRepository.findByOrderIdAndCustomerId(105L, 42L)).thenReturn(Optional.of(ownedOrder));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(105L))
                .thenThrow(new IllegalStateException("Database connection failed"));

        assertThatThrownBy(() -> getOrderDetailUseCase.execute(42L, 105L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database connection failed");

        verify(orderRepository).findByOrderIdAndCustomerId(105L, 42L);
    }
}
