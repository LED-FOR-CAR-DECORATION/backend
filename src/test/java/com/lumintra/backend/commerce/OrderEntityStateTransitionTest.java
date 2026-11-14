package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.UpdateStaffOrderStatusRequest;
import com.lumintra.backend.commerce.api.request.validation.StaffTargetOrderStatusValidator;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEntityStateTransitionTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_STAFF_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.CANCELED),
            OrderStatus.DELIVERY_FAILED, Set.of(OrderStatus.PROCESSING)
    );

    private OrderEntity orderWithStatus(OrderStatus status) {
        return OrderEntity.of(
                102L,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                com.lumintra.backend.commerce.domain.model.OrderType.PHYSICAL,
                status,
                Instant.parse("2026-11-12T12:00:00Z")
        );
    }

    @Test
    @DisplayName("COM-12 UTCID01 [N]: OrderEntity permits exact staff state diagram transitions and updates status cleanly")
    void transitionStatusByStaff_allowedDiagramPairs_succeed() {
        OrderEntity pending = orderWithStatus(OrderStatus.PENDING_PAYMENT);
        pending.transitionStatusByStaff(OrderStatus.CANCELED);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELED);

        OrderEntity failed = orderWithStatus(OrderStatus.DELIVERY_FAILED);
        failed.transitionStatusByStaff(OrderStatus.PROCESSING);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("COM-12 UTCID02 [A]: OrderEntity rejects all disallowed staff state transitions including DELIVERED->COMPLETED without mutating current status")
    void validateStaffTransition_disallowedTransitions_throwsWithoutMutatingStatus() {
        for (OrderStatus current : OrderStatus.values()) {
            Set<OrderStatus> allowedTargets = ALLOWED_STAFF_TRANSITIONS.getOrDefault(current, Set.of());
            for (OrderStatus target : OrderStatus.values()) {
                OrderEntity entity = orderWithStatus(current);
                if (allowedTargets.contains(target)) {
                    assertThat(entity.canStaffTransitionTo(target)).isTrue();
                    entity.validateStaffTransition(target);
                    assertThat(entity.getStatus()).isEqualTo(current);
                } else {
                    assertThat(entity.canStaffTransitionTo(target)).isFalse();
                    assertThatThrownBy(() -> entity.validateStaffTransition(target))
                            .isInstanceOf(OrderStatusTransitionException.class)
                            .hasMessageContaining(current.name())
                            .hasMessageContaining(target.name());
                    assertThat(entity.getStatus()).isEqualTo(current);
                }
            }
        }

        OrderEntity nullCheck = orderWithStatus(OrderStatus.PENDING_PAYMENT);
        assertThat(nullCheck.canStaffTransitionTo(null)).isFalse();
        assertThatThrownBy(() -> nullCheck.validateStaffTransition(null))
                .isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("COM-12 UTCID03 [N]: Request validator accepts PROCESSING, CANCELED, and normalizes CANCELLED alias")
    void requestNormalization_validTargetsAndCancelledAlias_normalizeCleanly() {
        StaffTargetOrderStatusValidator validator = new StaffTargetOrderStatusValidator();

        assertThat(validator.isValid("PROCESSING", null)).isTrue();
        assertThat(validator.isValid("CANCELED", null)).isTrue();
        assertThat(validator.isValid("CANCELLED", null)).isTrue();
        assertThat(validator.isValid(" PROCESSING ", null)).isTrue();

        assertThat(new UpdateStaffOrderStatusRequest("PROCESSING").toOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(new UpdateStaffOrderStatusRequest("CANCELED").toOrderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(new UpdateStaffOrderStatusRequest("CANCELLED").toOrderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(new UpdateStaffOrderStatusRequest(" CANCELLED ").toOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("COM-12 UTCID04 [B]: Request validator delegates blank/null to @NotBlank and rejects disallowed status strings including COMPLETED")
    void requestValidator_nullBlankOrDisallowedValues_behavesCorrectly() {
        StaffTargetOrderStatusValidator validator = new StaffTargetOrderStatusValidator();

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();

        assertThat(validator.isValid("COMPLETED", null)).isFalse();
        assertThat(validator.isValid(" COMPLETED ", null)).isFalse();
        assertThat(validator.isValid("SHIPPING", null)).isFalse();
        assertThat(validator.isValid("DELIVERED", null)).isFalse();
        assertThat(validator.isValid("PENDING_PAYMENT", null)).isFalse();
        assertThat(validator.isValid("processing", null)).isFalse();
        assertThat(validator.isValid("UNKNOWN", null)).isFalse();
    }

    @Test
    @DisplayName("COM-15 UTCID01 [N]: OrderEntity permits customer delivery confirmation only from DELIVERED")
    void validateCustomerConfirmDelivery_whenDelivered_succeedsWithoutMutatingEntity() {
        OrderEntity delivered = orderWithStatus(OrderStatus.DELIVERED);
        assertThat(delivered.canCustomerConfirmDelivery()).isTrue();
        delivered.validateCustomerConfirmDelivery();
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("COM-15 UTCID02 [A]: OrderEntity rejects customer delivery confirmation for all non-DELIVERED statuses")
    void validateCustomerConfirmDelivery_nonDeliveredStatuses_throwsWithoutMutatingStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.DELIVERED) {
                continue;
            }
            OrderEntity entity = orderWithStatus(status);
            assertThat(entity.canCustomerConfirmDelivery()).isFalse();
            assertThatThrownBy(entity::validateCustomerConfirmDelivery)
                    .isInstanceOf(OrderStatusTransitionException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("Only DELIVERED orders can be confirmed.");
            assertThat(entity.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("COM-09 UTCID01 [N]: OrderEntity permits customer cancel for PENDING_PAYMENT (resolves CANCELED) and PROCESSING (resolves REFUNDED)")
    void validateCustomerCancel_allowedStatuses_succeedsAndResolvesCorrectTarget() {
        OrderEntity pending = orderWithStatus(OrderStatus.PENDING_PAYMENT);
        assertThat(pending.canCustomerCancel()).isTrue();
        pending.validateCustomerCancel();
        assertThat(pending.resolveCustomerCancelTargetStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        OrderEntity processing = orderWithStatus(OrderStatus.PROCESSING);
        assertThat(processing.canCustomerCancel()).isTrue();
        processing.validateCustomerCancel();
        assertThat(processing.resolveCustomerCancelTargetStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(processing.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("COM-09 UTCID02 [A]: OrderEntity rejects customer cancel for all non-cancellable statuses")
    void validateCustomerCancel_disallowedStatuses_throwsWithoutMutatingStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.PROCESSING) {
                continue;
            }
            OrderEntity entity = orderWithStatus(status);
            assertThat(entity.canCustomerCancel()).isFalse();
            assertThatThrownBy(entity::validateCustomerCancel)
                    .isInstanceOf(OrderStatusTransitionException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("Only PENDING_PAYMENT or PROCESSING orders can be cancelled.");
            assertThatThrownBy(entity::resolveCustomerCancelTargetStatus)
                    .isInstanceOf(OrderStatusTransitionException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("Only PENDING_PAYMENT or PROCESSING orders can be cancelled.");
            assertThat(entity.getStatus()).isEqualTo(status);
        }
    }
}
