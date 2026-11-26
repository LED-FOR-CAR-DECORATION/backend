package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.OrderPaymentLookupServiceImpl;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentLookupServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderPaymentLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentLookupServiceImpl(orderRepository);
    }

    @Test
    @DisplayName("PAY-01 [N]: Returns OrderPaymentSnapshot when order exists")
    void findByOrderId_existingOrder_returnsSnapshot() {
        Instant expiresAt = Instant.parse("2026-11-12T17:00:00Z");
        OrderEntity order = OrderEntity.of(
                102L,
                42L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.DIGITAL,
                OrderStatus.PENDING_PAYMENT,
                expiresAt
        );

        when(orderRepository.findById(102L)).thenReturn(Optional.of(order));

        Optional<OrderPaymentSnapshot> snapshot = service.findByOrderId(102L);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().orderId()).isEqualTo(102L);
        assertThat(snapshot.get().customerId()).isEqualTo(42L);
        assertThat(snapshot.get().totalAmount()).isEqualByComparingTo("250000.00");
        assertThat(snapshot.get().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(snapshot.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("PAY-01 [B]: Returns empty Optional when orderId is null")
    void findByOrderId_nullOrderId_returnsEmpty() {
        assertThat(service.findByOrderId(null)).isEmpty();
        verifyNoInteractions(orderRepository);
    }
}
