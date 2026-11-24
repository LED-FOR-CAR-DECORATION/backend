package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.UpdateStaffOrderDeliveryAddressRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.application.UpdateStaffOrderDeliveryAddressUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateStaffOrderDeliveryAddressUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    private UpdateStaffOrderDeliveryAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateStaffOrderDeliveryAddressUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderDeliveryRepository,
                new OrderMapper()
        );
    }

    @Test
    @DisplayName("N: Staff updates recipient phone and address when order is DELIVERY_FAILED")
    void execute_deliveryFailedOrder_updatesRecipientAndPreservesCoordinatesWhenOmitted() {
        OrderEntity order = OrderEntity.of(
                105L, 15L, 10L,
                new BigDecimal("250000.00"), BigDecimal.ZERO, new BigDecimal("250000.00"),
                OrderType.PHYSICAL, OrderStatus.DELIVERY_FAILED, Instant.now());
        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L, "Old Name", "0900000000", "Old Address",
                        new BigDecimal("10.7768890"), new BigDecimal("106.7008060")));
        delivery.setDeliveryStatus(DeliveryStatus.FAILED);
        delivery.setDeliveryAttemptCount(1);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(105L)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(105L)).thenReturn(Optional.of(delivery));
        when(orderDeliveryRepository.save(any(OrderDeliveryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffOrderDetailResponse.DeliveryDetail updated = useCase.execute(
                "7",
                105L,
                new UpdateStaffOrderDeliveryAddressRequest(
                        "Nguyen Van Moi",
                        "0912345678",
                        "456 Nguyen Trai, District 5, HCMC",
                        null,
                        null
                )
        );

        assertThat(updated.recipientName()).isEqualTo("Nguyen Van Moi");
        assertThat(updated.recipientPhone()).isEqualTo("0912345678");
        assertThat(updated.fullAddress()).isEqualTo("456 Nguyen Trai, District 5, HCMC");
        assertThat(updated.latitude()).isEqualByComparingTo("10.7768890");
        assertThat(updated.longitude()).isEqualByComparingTo("106.7008060");
        assertThat(updated.deliveryAttemptCount()).isEqualTo(1);
        assertThat(updated.maxDeliveryAttempts()).isEqualTo(OrderDeliveryEntity.MAX_DELIVERY_ATTEMPTS);
    }

    @Test
    @DisplayName("A: Rejects address update when order is actively SHIPPING")
    void execute_shippingOrder_throwsOrderStatusTransitionException() {
        OrderEntity order = OrderEntity.of(
                106L, 15L, 10L,
                new BigDecimal("250000.00"), BigDecimal.ZERO, new BigDecimal("250000.00"),
                OrderType.PHYSICAL, OrderStatus.SHIPPING, Instant.now());

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(106L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(
                "7",
                106L,
                new UpdateStaffOrderDeliveryAddressRequest("Name", "0912345678", "Address", null, null)
        )).isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("N: Staff updates delivery address and coordinates on PENDING_PAYMENT order")
    void execute_pendingPaymentOrderWithCoordinates_updatesAndRoundsCoordinates() {
        OrderEntity order = OrderEntity.of(
                107L, 15L, 10L,
                new BigDecimal("250000.00"), BigDecimal.ZERO, new BigDecimal("250000.00"),
                OrderType.PHYSICAL, OrderStatus.PENDING_PAYMENT, Instant.now());
        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L, "Old Name", "0900000000", "Unreasonable Address",
                        new BigDecimal("10.7000000"), new BigDecimal("106.7000000")));

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(107L)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.findByOrder_OrderId(107L)).thenReturn(Optional.of(delivery));
        when(orderDeliveryRepository.save(any(OrderDeliveryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffOrderDetailResponse.DeliveryDetail updated = useCase.execute(
                "7",
                107L,
                new UpdateStaffOrderDeliveryAddressRequest(
                        "Tran Thi B",
                        "0988777666",
                        "123 Le Loi, Ben Nghe, District 1, HCMC",
                        new BigDecimal("10.776889123"),
                        new BigDecimal("106.700806456")
                )
        );

        assertThat(updated.recipientName()).isEqualTo("Tran Thi B");
        assertThat(updated.recipientPhone()).isEqualTo("0988777666");
        assertThat(updated.fullAddress()).isEqualTo("123 Le Loi, Ben Nghe, District 1, HCMC");
        assertThat(updated.latitude()).isEqualByComparingTo("10.7768891");
        assertThat(updated.longitude()).isEqualByComparingTo("106.7008065");
    }
}
