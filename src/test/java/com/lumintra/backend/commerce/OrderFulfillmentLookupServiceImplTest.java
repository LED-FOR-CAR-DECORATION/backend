package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.OrderFulfillmentLookupServiceImpl;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderFulfillmentLookupServiceImpl.class)
@ActiveProfiles("test")
class OrderFulfillmentLookupServiceImplTest {

    @Autowired
    private OrderFulfillmentLookupServiceImpl fulfillmentLookupService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderItemFulfillmentRepository orderItemFulfillmentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private LedInstanceRepository ledInstanceRepository;

    @Test
    @DisplayName("DEV-93 JPA-01 [N]: Looks up fulfillment snapshot with orderId, orderItemId, orderCustomerId, and orderStatus")
    void findFulfillment_singleAndBatchLookup_returnsSnapshotsWithOrderOwnershipAndStatus() {
        OrderEntity deliveredOrder = OrderEntity.createPendingPayment(
                42L,
                10L,
                new BigDecimal("1890000.00"),
                new BigDecimal("30000.00"),
                new BigDecimal("1920000.00"),
                OrderType.PHYSICAL,
                Instant.now().plus(1, ChronoUnit.DAYS)
        );
        deliveredOrder.setStatus(OrderStatus.DELIVERED);
        deliveredOrder = orderRepository.save(deliveredOrder);

        OrderItemEntity item = orderItemRepository.save(
                OrderItemEntity.create(deliveredOrder, 101L, 1, new BigDecimal("1890000.00"))
        );

        orderItemFulfillmentRepository.save(
                OrderItemFulfillmentEntity.create(item, 5001L)
        );

        Optional<InstanceFulfillmentSnapshot> single = fulfillmentLookupService.findFulfillmentByInstanceId(5001L);
        assertThat(single).isPresent();
        assertThat(single.get().instanceId()).isEqualTo(5001L);
        assertThat(single.get().orderId()).isEqualTo(deliveredOrder.getOrderId());
        assertThat(single.get().orderItemId()).isEqualTo(item.getOrderItemId());
        assertThat(single.get().orderCustomerId()).isEqualTo(42L);
        assertThat(single.get().orderStatus()).isEqualTo("DELIVERED");
        assertThat(single.get().isOrderDeliveredOrCompleted()).isTrue();

        Map<Long, InstanceFulfillmentSnapshot> batch =
                fulfillmentLookupService.findFulfillmentsByInstanceIds(List.of(5001L, 9999L));
        assertThat(batch).containsOnlyKeys(5001L);
        assertThat(batch.get(5001L).orderCustomerId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("DEV-93 JPA-02 [B]: seedOrderFulfillmentIfPresent links instance to physical order item by payment transaction suffix idempotently")
    void seedOrderFulfillmentIfPresent_linksAndIsIdempotent() {
        OrderEntity order = OrderEntity.createPendingPayment(
                77L,
                11L,
                new BigDecimal("1890000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1890000.00"),
                OrderType.PHYSICAL,
                Instant.now().plus(1, ChronoUnit.DAYS)
        );
        order.setStatus(OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        OrderItemEntity item = orderItemRepository.save(
                OrderItemEntity.create(order, 101L, 1, new BigDecimal("1890000.00"))
        );

        PaymentTransactionEntity payment = PaymentTransactionEntity.createPending(
                order.getOrderId(),
                PaymentGateway.SEPAY,
                "TXN-TEST-SEED0005",
                new BigDecimal("1890000.00")
        );
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentTransactionRepository.save(payment);

        fulfillmentLookupService.seedOrderFulfillmentIfPresent("SEED0005", 6001L);
        fulfillmentLookupService.seedOrderFulfillmentIfPresent("SEED0005", 6001L);

        Optional<InstanceFulfillmentSnapshot> snapshot = fulfillmentLookupService.findFulfillmentByInstanceId(6001L);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().orderId()).isEqualTo(order.getOrderId());
        assertThat(snapshot.get().orderItemId()).isEqualTo(item.getOrderItemId());
        assertThat(snapshot.get().orderCustomerId()).isEqualTo(77L);
        assertThat(snapshot.get().orderStatus()).isEqualTo("COMPLETED");
        assertThat(snapshot.get().isOrderDeliveredOrCompleted()).isTrue();
    }

    @Test
    @DisplayName("DEV-93 JPA-03 [B]: LedInstanceRepository.bindIfUnbound atomically transitions unbound instance to BOUND and returns 0 if already BOUND")
    void bindIfUnbound_atomicTransition_preventsDoubleBinding() {
        LedInstanceEntity instance = ledInstanceRepository.save(LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-7777",
                "AA:BB:CC:77:77:01",
                "REV_1_0",
                null,
                null,
                LedInstanceStatus.IN_TRANSIT,
                Instant.parse("2026-10-15T08:00:00Z"),
                null,
                null
        ));

        Instant boundAt = Instant.parse("2026-11-14T08:00:00Z");
        Instant warrantyExpiresAt = boundAt.plus(180, ChronoUnit.DAYS);

        int firstBind = ledInstanceRepository.bindIfUnbound(
                instance.getInstanceId(),
                42L,
                "My Car Matrix",
                boundAt,
                warrantyExpiresAt
        );
        assertThat(firstBind).isEqualTo(1);

        int secondBind = ledInstanceRepository.bindIfUnbound(
                instance.getInstanceId(),
                99L,
                "Another Name",
                boundAt.plusSeconds(60),
                warrantyExpiresAt.plusSeconds(60)
        );
        assertThat(secondBind).isZero();

        LedInstanceEntity reloaded = ledInstanceRepository.findById(instance.getInstanceId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(reloaded.getOwnerCustomerId()).isEqualTo(42L);
        assertThat(reloaded.getDeviceName()).isEqualTo("My Car Matrix");
    }
}
