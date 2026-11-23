package com.lumintra.backend.dashboard.infrastructure.persistence.mapper;

import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse.*;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.CategoryRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.LowStockLedRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.TopProductRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.WarrantyAlertRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManagerDashboardMapperTest {

    private ManagerDashboardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ManagerDashboardMapper();
    }

    @Test
    @DisplayName("MGT-01 Mapper UTCID14 [N]: Correctly maps all 5 categories with exact colors, revenues, and percentage shares")
    void toResponse_mapsCategoriesCorrectly() {
        BigDecimal totalRevenue = new BigDecimal("1000000000");
        List<CategoryRevenueRow> orderCategoryRows = List.of(
                new CategoryRevenueRow("LED", new BigDecimal("500000000"), 50L),
                new CategoryRevenueRow("BUNDLE", new BigDecimal("200000000"), 20L),
                new CategoryRevenueRow("CHARACTER", new BigDecimal("100000000"), 100L),
                new CategoryRevenueRow("SUBSCRIPTION", new BigDecimal("50000000"), 10L)
        );
        CategoryRevenueRow commissionRow = new CategoryRevenueRow("COMMISSION", new BigDecimal("150000000"), 15L);

        ManagerKpiOverviewResponse response = mapper.toResponse(
                totalRevenue,
                12.5,
                "vs. tháng 2026-10",
                5L,
                2L,
                7L,
                1L,
                3L,
                4L,
                6L,
                orderCategoryRows,
                commissionRow,
                List.of(),
                List.of(),
                List.of()
        );

        List<RevenueCategoryItem> categories = response.revenueByCategory();
        assertThat(categories).hasSize(5);

        // 1. HARDWARE (#3B82F6, 50.0%)
        RevenueCategoryItem hardware = categories.stream().filter(c -> c.category().equals("HARDWARE")).findFirst().orElseThrow();
        assertThat(hardware.revenue()).isEqualByComparingTo("500000000");
        assertThat(hardware.color()).isEqualTo("#3B82F6");
        assertThat(hardware.percentage()).isEqualTo(50.0);
        assertThat(hardware.unitCount()).isEqualTo(50L);

        // 2. COMMISSION (#10B981, 15.0%)
        RevenueCategoryItem commission = categories.stream().filter(c -> c.category().equals("COMMISSION")).findFirst().orElseThrow();
        assertThat(commission.revenue()).isEqualByComparingTo("150000000");
        assertThat(commission.color()).isEqualTo("#10B981");
        assertThat(commission.percentage()).isEqualTo(15.0);
        assertThat(commission.unitCount()).isEqualTo(15L);

        // 3. BUNDLE (#F59E0B, 20.0%)
        RevenueCategoryItem bundle = categories.stream().filter(c -> c.category().equals("BUNDLE")).findFirst().orElseThrow();
        assertThat(bundle.revenue()).isEqualByComparingTo("200000000");
        assertThat(bundle.color()).isEqualTo("#F59E0B");
        assertThat(bundle.percentage()).isEqualTo(20.0);
        assertThat(bundle.unitCount()).isEqualTo(20L);

        // 4. CHARACTER (#8B5CF6, 10.0%)
        RevenueCategoryItem character = categories.stream().filter(c -> c.category().equals("CHARACTER")).findFirst().orElseThrow();
        assertThat(character.revenue()).isEqualByComparingTo("100000000");
        assertThat(character.color()).isEqualTo("#8B5CF6");
        assertThat(character.percentage()).isEqualTo(10.0);
        assertThat(character.unitCount()).isEqualTo(100L);

        // 5. SUBSCRIPTION (#EC4899, 5.0%)
        RevenueCategoryItem subscription = categories.stream().filter(c -> c.category().equals("SUBSCRIPTION")).findFirst().orElseThrow();
        assertThat(subscription.revenue()).isEqualByComparingTo("50000000");
        assertThat(subscription.color()).isEqualTo("#EC4899");
        assertThat(subscription.percentage()).isEqualTo(5.0);
        assertThat(subscription.unitCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("MGT-01 Mapper UTCID15 [N]: Correctly maps top selling products ranked 1..5 with mapped category codes")
    void toResponse_mapsTopSellingProductsCorrectly() {
        List<TopProductRow> topRows = List.of(
                new TopProductRow(1L, "Màn hình LED P4", "LED", "https://cdn.com/p4.png", 50L, new BigDecimal("250000000")),
                new TopProductRow(2L, "Gói VIP Lumintra", "SUBSCRIPTION", "https://cdn.com/vip.png", 30L, new BigDecimal("60000000"))
        );

        ManagerKpiOverviewResponse response = mapper.toResponse(
                BigDecimal.ZERO, 0.0, "period", 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                List.of(), null, topRows, List.of(), List.of()
        );

        List<TopProductItem> items = response.topSellingProducts();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).rank()).isEqualTo(1);
        assertThat(items.get(0).category()).isEqualTo("HARDWARE");
        assertThat(items.get(0).unitsSold()).isEqualTo(50L);

        assertThat(items.get(1).rank()).isEqualTo(2);
        assertThat(items.get(1).category()).isEqualTo("SUBSCRIPTION");
        assertThat(items.get(1).unitsSold()).isEqualTo(30L);
    }

    @Test
    @DisplayName("MGT-01 Mapper UTCID16 [A]: Correctly formats LED dimensions and urgency thresholds (0 -> CRITICAL, < 5 -> LOW, >= 5 -> NORMAL)")
    void toResponse_mapsLowStockUrgencyCorrectly() {
        List<LowStockLedRow> lowStockRows = List.of(
                new LowStockLedRow(10L, 100L, "LED Matrix Alpha", 64, 32, 0L, new BigDecimal("3000000")),
                new LowStockLedRow(11L, 101L, "LED Matrix Beta", 128, 64, 4L, new BigDecimal("6000000")),
                new LowStockLedRow(12L, null, "LED Matrix Gamma", null, null, 9L, new BigDecimal("1000000"))
        );

        ManagerKpiOverviewResponse response = mapper.toResponse(
                BigDecimal.ZERO, 0.0, "period", 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                List.of(), null, List.of(), lowStockRows, List.of()
        );

        List<LowStockLedItem> leds = response.lowStockLeds();
        assertThat(leds).hasSize(3);

        assertThat(leds.get(0).urgency()).isEqualTo("CRITICAL");
        assertThat(leds.get(0).dimensions()).isEqualTo("64x32");
        assertThat(leds.get(0).productId()).isEqualTo("100");

        assertThat(leds.get(1).urgency()).isEqualTo("LOW");
        assertThat(leds.get(1).dimensions()).isEqualTo("128x64");

        assertThat(leds.get(2).urgency()).isEqualTo("NORMAL");
        assertThat(leds.get(2).dimensions()).isEqualTo("—");
        assertThat(leds.get(2).productId()).isEqualTo("12");
    }

    @Test
    @DisplayName("MGT-01 Mapper UTCID17 [N]: Maps warranty alert rows to response items and pending warranty count")
    void toResponse_mapsWarrantyAlertsCorrectly() {
        List<WarrantyAlertRow> rows = List.of(
                new WarrantyAlertRow(
                        15L, "Nguyen Van A", "a@lumintra.test", "LED 64x64", "SN-001", "Nhieu diem anh",
                        Instant.parse("2026-11-20T08:30:00Z"), "PENDING"),
                new WarrantyAlertRow(
                        16L, null, null, null, "SN-002", "Khong len nguon", null, "APPROVED")
        );

        ManagerKpiOverviewResponse response = mapper.toResponse(
                BigDecimal.ZERO, 0.0, "period", 0L, 0L, 4L, 0L, 0L, 0L, 0L,
                List.of(), null, List.of(), List.of(), rows
        );

        assertThat(response.kpiSummary().pendingWarrantyClaimsCount()).isEqualTo(4L);
        assertThat(response.warrantyAlerts()).hasSize(2);
        assertThat(response.warrantyAlerts().get(0).ticketId()).isEqualTo("15");
        assertThat(response.warrantyAlerts().get(0).reportedDate()).isEqualTo("2026-11-20T08:30:00Z");
        assertThat(response.warrantyAlerts().get(0).status()).isEqualTo("PENDING");
        assertThat(response.warrantyAlerts().get(1).customerName()).isNull();
        assertThat(response.warrantyAlerts().get(1).reportedDate()).isNull();
    }
}
