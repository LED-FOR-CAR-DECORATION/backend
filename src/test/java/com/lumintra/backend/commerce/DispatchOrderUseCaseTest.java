package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.DispatchOrderRequest;
import com.lumintra.backend.commerce.api.response.DispatchOrderResponse;
import com.lumintra.backend.commerce.application.DispatchOrderUseCase;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchCommand;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchResult;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.domain.service.CarrierDispatchPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.CarrierDispatchException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchOrderUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CarrierDispatchPort carrierDispatchPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private DispatchOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        useCase = new DispatchOrderUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderDeliveryRepository,
                orderItemRepository,
                productLookupService,
                carrierDispatchPort,
                new OrderMapper(),
                transactionTemplate
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

    private OrderDeliveryEntity sampleDelivery(OrderEntity order, DeliveryStatus deliveryStatus) {
        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L,
                        "Dang Phuoc Nguyen",
                        "0912345678",
                        "475A Dien Bien Phu, Binh Thanh, HCMC",
                        new BigDecimal("10.8018493"),
                        new BigDecimal("106.7144660")
                )
        );
        delivery.setDeliveryStatus(deliveryStatus);
        return delivery;
    }

    @Test
    @DisplayName("COM-13 UTCID01 [N]: Dispatches PROCESSING physical order to AhaMove and atomically updates order_delivery and order status")
    void execute_eligibleOrder_dispatchesToCarrierAndUpdatesOrderAndDelivery() {
        Long orderId = 102L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING, OrderType.MIXED);
        OrderDeliveryEntity delivery = sampleDelivery(order, DeliveryStatus.PENDING);
        OrderItemEntity item = OrderItemEntity.create(order, 5L, 2, new BigDecimal("200000.00"));

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(delivery));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(item));
        when(productLookupService.findSnapshotsByIds(Set.of(5L))).thenReturn(Map.of(
                5L, new ProductSnapshot(5L, "Lumintra LED Matrix v1", ProductType.LED, null,
                        new BigDecimal("200000.00"), ProductStatus.PUBLISHED)
        ));
        when(carrierDispatchPort.createDeliveryOrder(any(CarrierDispatchCommand.class)))
                .thenReturn(new CarrierDispatchResult(
                        "24ABCD",
                        "https://expressstg.ahamove.com/s/24ABCD",
                        new BigDecimal("33000.00"),
                        "ASSIGNING"
                ));
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PROCESSING), eq(OrderStatus.SHIPPING), any(Instant.class)))
                .thenReturn(1);
        when(orderDeliveryRepository.updateDispatchInfoIfEligible(
                eq(orderId),
                anyCollection(),
                eq(DeliveryStatus.ASSIGNED),
                eq("24ABCD"),
                eq("https://expressstg.ahamove.com/s/24ABCD"),
                eq(new BigDecimal("33000.00")),
                any(Instant.class)))
                .thenReturn(1);

        DispatchOrderResponse response = useCase.execute(
                "7",
                orderId,
                new DispatchOrderRequest("sgn-bike", "Call before arrival", new BigDecimal("1.5"))
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(response.trackingNumber()).isEqualTo("24ABCD");
        assertThat(response.trackingUrl()).isEqualTo("https://expressstg.ahamove.com/s/24ABCD");
        assertThat(response.carrierFee()).isEqualByComparingTo("33000.00");

        ArgumentCaptor<CarrierDispatchCommand> captor = ArgumentCaptor.forClass(CarrierDispatchCommand.class);
        verify(carrierDispatchPort).createDeliveryOrder(captor.capture());
        CarrierDispatchCommand command = captor.getValue();
        assertThat(command.serviceId()).isEqualTo("SGN-BIKE");
        assertThat(command.remarks()).isEqualTo("Call before arrival");
        assertThat(command.weightKg()).isEqualByComparingTo("1.5");
        assertThat(command.internalTrackingNumber()).startsWith("LUM-102-");
        assertThat(command.items()).hasSize(1);
        assertThat(command.items().getFirst().name()).isEqualTo("Lumintra LED Matrix v1");
    }

    @Test
    @DisplayName("COM-13 UTCID02 [A]: Rejects missing order, missing delivery, ineligible order status, and active shipment before calling carrier")
    void execute_ineligibleOrderOrDelivery_throwsBeforeCallingCarrier() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);

        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("7", 999L, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(OrderNotFoundException.class);

        OrderEntity completedOrder = sampleOrder(103L, OrderStatus.COMPLETED, OrderType.PHYSICAL);
        when(orderRepository.findById(103L)).thenReturn(Optional.of(completedOrder));
        assertThatThrownBy(() -> useCase.execute("7", 103L, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("COMPLETED");

        OrderEntity digitalOrder = sampleOrder(104L, OrderStatus.PROCESSING, OrderType.DIGITAL);
        when(orderRepository.findById(104L)).thenReturn(Optional.of(digitalOrder));
        when(orderDeliveryRepository.findByOrder_OrderId(104L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("7", 104L, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("delivery record");

        OrderEntity activeShipmentOrder = sampleOrder(105L, OrderStatus.PARTIALLY_FULFILLED, OrderType.MIXED);
        when(orderRepository.findById(105L)).thenReturn(Optional.of(activeShipmentOrder));
        when(orderDeliveryRepository.findByOrder_OrderId(105L))
                .thenReturn(Optional.of(sampleDelivery(activeShipmentOrder, DeliveryStatus.ASSIGNED)));
        assertThatThrownBy(() -> useCase.execute("7", 105L, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("ASSIGNED");

        verifyNoInteractions(carrierDispatchPort);
    }

    @Test
    @DisplayName("COM-13 UTCID03 [A]: Propagates CarrierDispatchException without mutating order or delivery state")
    void execute_carrierRejectsDispatch_propagatesErrorWithoutDbMutation() {
        Long orderId = 106L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderDeliveryEntity delivery = sampleDelivery(order, DeliveryStatus.PENDING);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(delivery));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of());
        when(carrierDispatchPort.createDeliveryOrder(any()))
                .thenThrow(new CarrierDispatchException(
                        HttpStatus.NOT_ACCEPTABLE,
                        "NOT_ENOUGH_CREDIT",
                        "AhaMove rejected dispatch (NOT_ENOUGH_CREDIT): Insufficient wallet balance"
                ));

        assertThatThrownBy(() -> useCase.execute("7", orderId, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .hasMessageContaining("NOT_ENOUGH_CREDIT");

        verifyNoInteractions(transactionTemplate);
    }

    @Test
    @DisplayName("COM-13 UTCID04 [A]: Guards employee access and throws OrderStatusTransitionException on concurrent order/delivery update conflicts")
    void execute_accessDeniedOrConcurrentConflict_throwsExpectedException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("bad", ALLOWED_ROLES))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));
        assertThatThrownBy(() -> useCase.execute("bad", 102L, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(AccessDeniedException.class);

        Long orderId = 107L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderDeliveryEntity delivery = sampleDelivery(order, DeliveryStatus.PENDING);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(orderId)).thenReturn(Optional.of(delivery));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of());
        when(carrierDispatchPort.createDeliveryOrder(any()))
                .thenReturn(new CarrierDispatchResult("24CONC", "https://expressstg.ahamove.com/s/24CONC", new BigDecimal("30000"), "ASSIGNING"));
        when(orderRepository.updateStatusIfCurrent(
                eq(orderId), eq(OrderStatus.PROCESSING), eq(OrderStatus.SHIPPING), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute("7", orderId, new DispatchOrderRequest("SGN-BIKE", null, null)))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("concurrently");
    }
}
