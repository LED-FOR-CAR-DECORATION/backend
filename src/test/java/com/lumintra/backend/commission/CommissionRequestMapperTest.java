package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionRequestMapperTest {

    private final CommissionRequestMapper mapper = new CommissionRequestMapper();

    @Test
    @DisplayName("CMS-04 Mapper UTCID01 [N]: Exposes activePaymentCode only when status is payment-pending")
    void toCustomerSummaryItem_exposesActivePaymentCodeOnlyWhenPaymentPending() {
        CommissionRequestEntity pendingPayment = CommissionRequestEntity.createPendingReview(
                10L, "Custom Cat", "Neon cat", "BASIC", "32x32", "{}", new BigDecimal("79000.00"), 1, 20L
        );
        pendingPayment.setCommissionId(100L);
        pendingPayment.setStatus(CommissionStatus.PENDING_PAYMENT);
        pendingPayment.setActivePaymentCode("LMT-CMS-100");

        CustomerCommissionSummaryItem pendingItem = mapper.toCustomerSummaryItem(pendingPayment);
        assertThat(pendingItem.activePaymentCode()).isEqualTo("LMT-CMS-100");
        assertThat(pendingPayment.isOwnedBy(10L)).isTrue();
        assertThat(pendingPayment.isOwnedBy(99L)).isFalse();

        pendingPayment.setStatus(CommissionStatus.PAID);
        CustomerCommissionSummaryItem paidItem = mapper.toCustomerSummaryItem(pendingPayment);
        assertThat(paidItem.activePaymentCode()).isNull();
        assertThat(CommissionStatus.PAID.isChatActive()).isTrue();
        assertThat(CommissionStatus.PENDING_REVIEW.isChatActive()).isFalse();
    }

    @Test
    @DisplayName("CMS-04 Mapper UTCID02 [B]: Handles null entity and null page safely")
    void toCustomerListResponse_handlesNullInputs() {
        assertThat(mapper.toCustomerSummaryItem(null)).isNull();

        CustomerCommissionListResponse response = mapper.toCustomerListResponse(null);
        assertThat(response.items()).isEmpty();
        assertThat(response.meta()).isNull();
    }
}
