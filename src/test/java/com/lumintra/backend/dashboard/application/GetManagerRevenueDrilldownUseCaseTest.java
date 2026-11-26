package com.lumintra.backend.dashboard.application;

import com.lumintra.backend.dashboard.api.request.ManagerRevenueDrilldownRequest;
import com.lumintra.backend.dashboard.api.response.ManagerOrderAuditDetailResponse;
import com.lumintra.backend.dashboard.api.response.ManagerRevenueDrilldownResponse;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.ManagerDashboardAnalyticsRepository;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.OrderAuditHeaderRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.OrderAuditLineRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.RevenueSourceRow;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetManagerRevenueDrilldownUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private ManagerDashboardAnalyticsRepository analyticsRepository;

    @InjectMocks
    private GetManagerRevenueDrilldownUseCase useCase;

    @Test
    @DisplayName("ALL scope merges order summaries and commissions in reverse chronological order")
    void execute_allScope_mergesOrdersAndCommissions() {
        Instant t1 = Instant.parse("2026-10-10T08:00:00Z");
        Instant t2 = Instant.parse("2026-10-15T10:00:00Z");

        when(analyticsRepository.findOrderSummaryRevenueSources(any(), any())).thenReturn(List.of(
                new RevenueSourceRow(
                        "ORDER", 38L, "Nguyen A", "a@gmail.com", "COMPLETED", "PHYSICAL",
                        null, "Lumintra Matrix 64x64 Pro", "LED",
                        2L, new BigDecimal("500000.00"), new BigDecimal("500000.00"), new BigDecimal("500000.00"), t1
                )
        ));
        when(analyticsRepository.findCommissionRevenueSources(any(), any())).thenReturn(List.of(
                new RevenueSourceRow(
                        "COMMISSION", 12L, "Tran B", "b@gmail.com", "COMPLETED", "COMMISSION",
                        null, "Custom Cat Pixel", "COMMISSION",
                        1L, new BigDecimal("350000.00"), new BigDecimal("350000.00"), new BigDecimal("350000.00"), t2
                )
        ));

        ManagerRevenueDrilldownResponse response = useCase.execute(
                "42", new ManagerRevenueDrilldownRequest("2026-10", "ALL", null));

        verify(internalEmployeeAccessService).requireActiveEmployeeAccountId("42", Set.of("MANAGER", "ADMIN"));
        assertThat(response.scopeType()).isEqualTo("ALL");
        assertThat(response.totalRevenue()).isEqualByComparingTo("850000.00");
        assertThat(response.totalUnits()).isEqualTo(3L);
        assertThat(response.distinctSourceCount()).isEqualTo(2L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).referenceCode()).isEqualTo("COM-12");
        assertThat(response.items().get(1).referenceCode()).isEqualTo("#38");
        assertThat(response.items().get(1).category()).isEqualTo("HARDWARE");
    }

    @Test
    @DisplayName("CATEGORY HARDWARE queries LED order items and maps category label")
    void execute_hardwareCategory_queriesLedProductType() {
        Instant t1 = Instant.parse("2026-10-12T08:00:00Z");
        when(analyticsRepository.findOrderItemRevenueSources(any(), any(), eq("LED"), isNull())).thenReturn(List.of(
                new RevenueSourceRow(
                        "ORDER", 40L, "Le C", "c@gmail.com", "SHIPPING", "MIXED",
                        1L, "Lumintra Matrix 64x64 Pro", "LED",
                        1L, new BigDecimal("250000.00"), new BigDecimal("250000.00"), new BigDecimal("320000.00"), t1
                )
        ));

        ManagerRevenueDrilldownResponse response = useCase.execute(
                "42", new ManagerRevenueDrilldownRequest("2026-10", "HARDWARE", null));

        assertThat(response.scopeType()).isEqualTo("CATEGORY");
        assertThat(response.scopeLabel()).isEqualTo("Phần cứng");
        assertThat(response.totalRevenue()).isEqualByComparingTo("250000.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).orderTotalAmount()).isEqualByComparingTo("320000.00");
    }

    @Test
    @DisplayName("PRODUCT scope filters by productId and resolves product label")
    void execute_productScope_filtersByProductId() {
        Instant t1 = Instant.parse("2026-10-20T09:00:00Z");
        when(analyticsRepository.findOrderItemRevenueSources(any(), any(), isNull(), eq(16L))).thenReturn(List.of(
                new RevenueSourceRow(
                        "ORDER", 51L, "Pham D", "d@gmail.com", "COMPLETED", "SUBSCRIPTION",
                        16L, "Lumintra All-Access VIP Pass", "SUBSCRIPTION",
                        1L, new BigDecimal("999999.00"), new BigDecimal("999999.00"), new BigDecimal("999999.00"), t1
                )
        ));

        ManagerRevenueDrilldownResponse response = useCase.execute(
                "42", new ManagerRevenueDrilldownRequest("2026-10", null, 16L));

        assertThat(response.scopeType()).isEqualTo("PRODUCT");
        assertThat(response.scopeLabel()).isEqualTo("Lumintra All-Access VIP Pass");
        assertThat(response.category()).isEqualTo("SUBSCRIPTION");
        assertThat(response.totalRevenue()).isEqualByComparingTo("999999.00");
    }

    @Test
    @DisplayName("getOrderAuditDetail returns header, payment, and lines or throws OrderNotFoundException")
    void getOrderAuditDetail_returnsDetailOrThrowsNotFound() {
        Instant now = Instant.parse("2026-10-20T09:00:00Z");
        when(analyticsRepository.findOrderAuditHeader(38L)).thenReturn(new OrderAuditHeaderRow(
                38L, "Nguyen A", "a@gmail.com", "COMPLETED", "MIXED",
                new BigDecimal("300000.00"), new BigDecimal("25000.00"), new BigDecimal("325000.00"),
                now, "LMTORD38SALES001", "SEPAY", "SUCCESS", now
        ));
        when(analyticsRepository.findOrderAuditLines(38L)).thenReturn(List.of(
                new OrderAuditLineRow(100L, 1L, "Lumintra Matrix 64x64 Pro", "LED",
                        1L, new BigDecimal("250000.00"), new BigDecimal("250000.00")),
                new OrderAuditLineRow(101L, 7L, "Cô Nàng Tinh Nghịch", "CHARACTER",
                        1L, new BigDecimal("50000.00"), new BigDecimal("50000.00"))
        ));

        ManagerOrderAuditDetailResponse detail = useCase.getOrderAuditDetail("42", 38L);
        assertThat(detail.referenceCode()).isEqualTo("#38");
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().get(0).category()).isEqualTo("HARDWARE");
        assertThat(detail.items().get(1).category()).isEqualTo("CHARACTER");

        when(analyticsRepository.findOrderAuditHeader(999L)).thenReturn(null);
        assertThatThrownBy(() -> useCase.getOrderAuditDetail("42", 999L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
