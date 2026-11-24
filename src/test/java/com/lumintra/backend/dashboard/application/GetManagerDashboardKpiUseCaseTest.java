package com.lumintra.backend.dashboard.application;

import com.lumintra.backend.dashboard.api.request.ManagerDashboardFilterRequest;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.infrastructure.persistence.mapper.ManagerDashboardMapper;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.CategoryRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.LowStockLedRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.ManagerDashboardAnalyticsRepository;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.TopProductRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.WarrantyAlertRow;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetManagerDashboardKpiUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private ManagerDashboardAnalyticsRepository analyticsRepository;

    private ManagerDashboardMapper mapper;
    private GetManagerDashboardKpiUseCase useCase;

    @BeforeEach
    void setUp() {
        mapper = new ManagerDashboardMapper();
        useCase = new GetManagerDashboardKpiUseCase(internalEmployeeAccessService, analyticsRepository, mapper);
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID01 [N]: Manager requests current month KPI with like-for-like MTD period calculation")
    void execute_currentMonth_calculatesMtdSuccessfully() {
        String subject = "101";
        String currentMonth = YearMonth.now().toString();
        String compareMonth = YearMonth.now().minusMonths(1).toString();
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest(currentMonth, compareMonth);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(101L);

        when(analyticsRepository.calculateOrderRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("1000000000"), new BigDecimal("800000000"));
        when(analyticsRepository.calculateCommissionRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("250000000"), new BigDecimal("200000000"));

        when(analyticsRepository.countPendingCharacters()).thenReturn(8L);
        when(analyticsRepository.countPendingCommissions()).thenReturn(5L);
        when(analyticsRepository.countPendingWarrantyClaims()).thenReturn(3L);
        when(analyticsRepository.findOpenWarrantyAlerts(5)).thenReturn(List.of(
                new WarrantyAlertRow(
                        15L, "Nguyen Van A", "a@lumintra.test", "LED 64x64", "SN-001", "Nhieu diem anh", Instant.parse("2026-11-20T08:30:00Z"), "PENDING")));
        when(analyticsRepository.countActiveBundles()).thenReturn(4L);
        when(analyticsRepository.countActiveSubscriptions()).thenReturn(3L);
        when(analyticsRepository.countActivePriceConfigs()).thenReturn(6L);

        List<LowStockLedRow> lowStockRows = List.of(
                new LowStockLedRow(1L, 10L, "LED-P4-64X64", 64, 64, 2L, new BigDecimal("4500000")),
                new LowStockLedRow(2L, 11L, "LED-P5-32X32", 32, 32, 0L, new BigDecimal("2500000"))
        );
        when(analyticsRepository.findLowStockModels(10L)).thenReturn(lowStockRows);

        List<CategoryRevenueRow> categoryRows = List.of(
                new CategoryRevenueRow("LED", new BigDecimal("725000000"), 145L),
                new CategoryRevenueRow("BUNDLE", new BigDecimal("125000000"), 25L),
                new CategoryRevenueRow("CHARACTER", new BigDecimal("100000000"), 200L),
                new CategoryRevenueRow("SUBSCRIPTION", new BigDecimal("50000000"), 20L)
        );
        when(analyticsRepository.calculateOrderItemCategoryRevenues(any(Instant.class), any(Instant.class)))
                .thenReturn(categoryRows);
        when(analyticsRepository.calculateCommissionCategoryRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new CategoryRevenueRow("COMMISSION", new BigDecimal("250000000"), 45L));

        List<TopProductRow> topRows = List.of(
                new TopProductRow(1L, "LED P4", "LED", "https://cdn.com/led.png", 120L, new BigDecimal("600000000"))
        );
        when(analyticsRepository.findTopSellingProducts(any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(topRows);

        ManagerKpiOverviewResponse response = useCase.execute(subject, request);

        assertThat(response).isNotNull();
        assertThat(response.kpiSummary().totalRevenue()).isEqualByComparingTo("1250000000");
        assertThat(response.kpiSummary().revenueGrowthPercent()).isEqualTo(25.0);
        assertThat(response.kpiSummary().revenuePeriod()).contains("cùng kỳ tháng trước");
        assertThat(response.kpiSummary().pendingCharactersCount()).isEqualTo(8L);
        assertThat(response.kpiSummary().pendingCommissionsCount()).isEqualTo(5L);
        assertThat(response.kpiSummary().lowStockModelsCount()).isEqualTo(2L);
        assertThat(response.kpiSummary().pendingWarrantyClaimsCount()).isEqualTo(3L);
        assertThat(response.warrantyAlerts()).hasSize(1);
        assertThat(response.warrantyAlerts().get(0).deviceSerial()).isEqualTo("SN-001");
        assertThat(response.kpiSummary().activeBundlesCount()).isEqualTo(4L);
        assertThat(response.kpiSummary().activeSubscriptionsCount()).isEqualTo(3L);
        assertThat(response.kpiSummary().activePriceConfigsCount()).isEqualTo(6L);

        assertThat(response.revenueByCategory()).hasSize(5);
        assertThat(response.topSellingProducts()).hasSize(1);
        assertThat(response.lowStockLeds()).hasSize(2);
        assertThat(response.lowStockLeds().get(1).urgency()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID02 [N]: Manager requests past month KPI with full month comparison")
    void execute_pastMonths_calculatesFullMonthSuccessfully() {
        String subject = "102";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest("2026-05", "2026-04");

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(102L);

        when(analyticsRepository.calculateOrderRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("500000000"), new BigDecimal("400000000"));
        when(analyticsRepository.calculateCommissionRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);

        when(analyticsRepository.countPendingCharacters()).thenReturn(0L);
        when(analyticsRepository.countPendingCommissions()).thenReturn(0L);
        when(analyticsRepository.countActiveBundles()).thenReturn(1L);
        when(analyticsRepository.countActiveSubscriptions()).thenReturn(1L);
        when(analyticsRepository.countActivePriceConfigs()).thenReturn(1L);
        when(analyticsRepository.findLowStockModels(10L)).thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateOrderItemCategoryRevenues(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateCommissionCategoryRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new CategoryRevenueRow("COMMISSION", BigDecimal.ZERO, 0L));
        when(analyticsRepository.findTopSellingProducts(any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(Collections.emptyList());

        ManagerKpiOverviewResponse response = useCase.execute(subject, request);

        assertThat(response).isNotNull();
        assertThat(response.kpiSummary().totalRevenue()).isEqualByComparingTo("500000000");
        assertThat(response.kpiSummary().revenueGrowthPercent()).isEqualTo(25.0);
        assertThat(response.kpiSummary().revenuePeriod()).isEqualTo("vs. tháng 2026-04");
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID03 [N]: Empty reporting data returns zero totals and empty top product list")
    void execute_emptyData_returnsZeroTotals() {
        String subject = "103";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest("2026-01", "2025-12");

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(103L);

        when(analyticsRepository.calculateOrderRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(analyticsRepository.calculateCommissionRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        when(analyticsRepository.findLowStockModels(10L)).thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateOrderItemCategoryRevenues(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateCommissionCategoryRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new CategoryRevenueRow("COMMISSION", BigDecimal.ZERO, 0L));
        when(analyticsRepository.findTopSellingProducts(any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(Collections.emptyList());

        ManagerKpiOverviewResponse response = useCase.execute(subject, request);

        assertThat(response.kpiSummary().totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.kpiSummary().revenueGrowthPercent()).isEqualTo(0.0);
        assertThat(response.kpiSummary().pendingWarrantyClaimsCount()).isEqualTo(0L);
        assertThat(response.topSellingProducts()).isEmpty();
        assertThat(response.lowStockLeds()).isEmpty();
        assertThat(response.warrantyAlerts()).isEmpty();
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID04 [A]: Comparison period has zero revenue, returns 0.0 growth without arithmetic exception")
    void execute_comparePeriodZeroRevenue_returnsZeroGrowth() {
        String subject = "104";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest("2026-03", "2026-02");

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(104L);

        when(analyticsRepository.calculateOrderRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("100000000"), BigDecimal.ZERO);
        when(analyticsRepository.calculateCommissionRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);

        when(analyticsRepository.findLowStockModels(10L)).thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateOrderItemCategoryRevenues(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateCommissionCategoryRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new CategoryRevenueRow("COMMISSION", BigDecimal.ZERO, 0L));
        when(analyticsRepository.findTopSellingProducts(any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(Collections.emptyList());

        ManagerKpiOverviewResponse response = useCase.execute(subject, request);

        assertThat(response.kpiSummary().revenueGrowthPercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID05 [A]: Low stock models map correctly to CRITICAL, LOW, and NORMAL urgency levels")
    void execute_lowStockUrgency_mappedCorrectly() {
        String subject = "105";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest("2026-07", "2026-06");

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(105L);

        when(analyticsRepository.calculateOrderRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(analyticsRepository.calculateCommissionRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        List<LowStockLedRow> lowStockRows = List.of(
                new LowStockLedRow(1L, 10L, "Model A", 32, 32, 0L, BigDecimal.TEN),
                new LowStockLedRow(2L, 11L, "Model B", 64, 32, 3L, BigDecimal.TEN),
                new LowStockLedRow(3L, 12L, "Model C", 64, 64, 7L, BigDecimal.TEN)
        );
        when(analyticsRepository.findLowStockModels(10L)).thenReturn(lowStockRows);
        when(analyticsRepository.calculateOrderItemCategoryRevenues(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(analyticsRepository.calculateCommissionCategoryRevenue(any(Instant.class), any(Instant.class)))
                .thenReturn(new CategoryRevenueRow("COMMISSION", BigDecimal.ZERO, 0L));
        when(analyticsRepository.findTopSellingProducts(any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(Collections.emptyList());

        ManagerKpiOverviewResponse response = useCase.execute(subject, request);

        assertThat(response.lowStockLeds()).hasSize(3);
        assertThat(response.lowStockLeds().get(0).urgency()).isEqualTo("CRITICAL");
        assertThat(response.lowStockLeds().get(1).urgency()).isEqualTo("LOW");
        assertThat(response.lowStockLeds().get(2).urgency()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID06 [A]: Stale JWT token detected (employee account suspended in DB) throws AccessDeniedException")
    void execute_staleTokenSuspendedEmployee_throwsAccessDeniedException() {
        String subject = "999";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest(null, null);

        doThrow(new AccessDeniedException("Active internal account is required."))
                .when(internalEmployeeAccessService).requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN")));

        assertThatThrownBy(() -> useCase.execute(subject, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Active internal account is required.");

        verifyNoInteractions(analyticsRepository);
    }

    @Test
    @DisplayName("MGT-01 UseCase UTCID07 [B]: Caller lacking MANAGER or ADMIN role in DB throws AccessDeniedException")
    void execute_unauthorizedRole_throwsAccessDeniedException() {
        String subject = "888";
        ManagerDashboardFilterRequest request = new ManagerDashboardFilterRequest(null, null);

        doThrow(new AccessDeniedException("Authorized internal role is required."))
                .when(internalEmployeeAccessService).requireActiveEmployeeAccountId(eq(subject), eq(Set.of("MANAGER", "ADMIN")));

        assertThatThrownBy(() -> useCase.execute(subject, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorized internal role is required.");

        verifyNoInteractions(analyticsRepository);
    }
}
