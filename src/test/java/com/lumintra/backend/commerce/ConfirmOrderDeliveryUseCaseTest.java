package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.ConfirmOrderDeliveryResponse;
import com.lumintra.backend.commerce.application.ConfirmOrderDeliveryUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderDeliveryUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    private ConfirmOrderDeliveryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmOrderDeliveryUseCase(orderRepository, new OrderMapper());
    }

    private OrderEntity sampleOrder(Long orderId, Long customerId, OrderStatus status, OrderType orderType) {
        return OrderEntity.of(
                orderId,
                customerId,
                10L,
                new BigDecimal("250000.00"),
                new BigDecimal("33000.00"),
                new BigDecimal("283000.00"),
                orderType,
                status,
                Instant.parse("2026-11-12T12:00:00Z")
        );
    }

    @Test
    @DisplayName("COM-15 UTCID01 [N]: Owning customer confirms DELIVERED physical/mixed order and transitions it to COMPLETED")
    void execute_deliveredOrderOwnedByCustomer_transitionsToCompleted() {
        OrderEntity deliveredPhysical = sampleOrder(102L, 42L, OrderStatus.DELIVERED, OrderType.PHYSICAL);
        when(orderRepository.findByOrderIdAndCustomerId(102L, 42L)).thenReturn(Optional.of(deliveredPhysical));
        when(orderRepository.updateStatusIfCurrent(
                eq(102L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);

        ConfirmOrderDeliveryResponse response = useCase.execute(42L, 102L);

        assertThat(response.orderId()).isEqualTo(102L);
        assertThat(response.previousStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.updatedAt()).isNotNull();

        OrderEntity deliveredMixed = sampleOrder(103L, 42L, OrderStatus.DELIVERED, OrderType.MIXED);
        when(orderRepository.findByOrderIdAndCustomerId(103L, 42L)).thenReturn(Optional.of(deliveredMixed));
        when(orderRepository.updateStatusIfCurrent(
                eq(103L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);

        ConfirmOrderDeliveryResponse mixedResponse = useCase.execute(42L, 103L);
        assertThat(mixedResponse.orderId()).isEqualTo(103L);
        assertThat(mixedResponse.previousStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(mixedResponse.status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("COM-15 UTCID02 [A]: Throws OrderNotFoundException when order is missing or belongs to another customer (IDOR)")
    void execute_missingOrOtherCustomersOrder_throwsOrderNotFound() {
        when(orderRepository.findByOrderIdAndCustomerId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(42L, 999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        when(orderRepository.findByOrderIdAndCustomerId(102L, 77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(77L, 102L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found.");

        verify(orderRepository).findByOrderIdAndCustomerId(999L, 42L);
        verify(orderRepository).findByOrderIdAndCustomerId(102L, 77L);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("COM-15 UTCID03 [A]: Rejects all non-DELIVERED order statuses with OrderStatusTransitionException before DB update")
    void execute_nonDeliveredStatuses_throwsTransitionExceptionWithoutUpdating() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.DELIVERED) {
                continue;
            }
            Long orderId = 200L + status.ordinal();
            OrderEntity order = sampleOrder(orderId, 42L, status, OrderType.PHYSICAL);
            when(orderRepository.findByOrderIdAndCustomerId(orderId, 42L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> useCase.execute(42L, orderId))
                    .isInstanceOf(OrderStatusTransitionException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("Only DELIVERED orders can be confirmed.");
        }

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("COM-15 UTCID04 [A]: Concurrent confirmation conflict throws OrderStatusTransitionException and storage failure propagates")
    void execute_concurrentUpdateConflictOrStorageError_throwsExpectedException() {
        OrderEntity delivered = sampleOrder(102L, 42L, OrderStatus.DELIVERED, OrderType.PHYSICAL);
        when(orderRepository.findByOrderIdAndCustomerId(102L, 42L)).thenReturn(Optional.of(delivered));
        when(orderRepository.updateStatusIfCurrent(
                eq(102L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(42L, 102L))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("concurrently");

        when(orderRepository.findByOrderIdAndCustomerId(109L, 42L))
                .thenThrow(new IllegalStateException("PostgreSQL timeout"));

        assertThatThrownBy(() -> useCase.execute(42L, 109L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL timeout");
    }
}
