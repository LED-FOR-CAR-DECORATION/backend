package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.application.LinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.commerce.application.UnlinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnlinkOrderFulfillmentDeviceUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemFulfillmentRepository orderItemFulfillmentRepository;

    @Mock
    private LedInstanceFulfillmentPort ledInstanceFulfillmentPort;

    @Mock
    private LinkOrderFulfillmentDeviceUseCase linkOrderFulfillmentDeviceUseCase;

    private UnlinkOrderFulfillmentDeviceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UnlinkOrderFulfillmentDeviceUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderItemFulfillmentRepository,
                ledInstanceFulfillmentPort,
                linkOrderFulfillmentDeviceUseCase
        );
    }

    private OrderEntity sampleOrder(Long orderId, OrderStatus status) {
        return OrderEntity.of(
                orderId,
                15L,
                10L,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                OrderType.PHYSICAL,
                status,
                Instant.parse("2026-11-20T12:00:00Z")
        );
    }

    @Test
    @DisplayName("COM-16 Unlink UTCID01 [P]: Successfully unlinks device and releases back to stock")
    void execute_validLink_unlinksAndReleasesToStock() {
        Long orderId = 100L;
        Long instanceId = 15L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING);
        OrderItemEntity item = OrderItemEntity.create(order, 5L, 2, new BigDecimal("250000.00"));
        OrderItemFulfillmentEntity fulfillment = OrderItemFulfillmentEntity.create(item, instanceId);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemFulfillmentRepository.findByOrderItem_Order_OrderIdAndInstanceId(orderId, instanceId))
                .thenReturn(Optional.of(fulfillment));
        when(orderItemFulfillmentRepository.countByOrderItem_Order_OrderId(orderId)).thenReturn(0L);
        when(linkOrderFulfillmentDeviceUseCase.calculateTotalRequiredLedUnits(orderId)).thenReturn(2L);

        useCase.execute("staff-1", orderId, instanceId);

        verify(orderItemFulfillmentRepository).delete(fulfillment);
        verify(ledInstanceFulfillmentPort).releaseToStock(instanceId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("COM-16 Unlink UTCID02 [A]: Throws OrderNotFoundException when order does not exist")
    void execute_orderNotFound_throwsOrderNotFoundException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("staff-1", 999L, 15L))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(orderItemFulfillmentRepository);
    }

    @Test
    @DisplayName("COM-16 Unlink UTCID03 [A]: Throws OrderStatusTransitionException when order is not in PROCESSING or PARTIALLY_FULFILLED")
    void execute_invalidStatus_throwsOrderStatusTransitionException() {
        OrderEntity order = sampleOrder(100L, OrderStatus.SHIPPING);
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute("staff-1", 100L, 15L))
                .isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("COM-16 Unlink UTCID04 [A]: Throws OrderFulfillmentException when link record not found")
    void execute_linkRecordNotFound_throwsOrderFulfillmentException() {
        OrderEntity order = sampleOrder(100L, OrderStatus.PROCESSING);
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderItemFulfillmentRepository.findByOrderItem_Order_OrderIdAndInstanceId(100L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("staff-1", 100L, 99L))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ofe.getErrorCode()).isEqualTo("FULFILLMENT_RECORD_NOT_FOUND");
                });
    }
}
