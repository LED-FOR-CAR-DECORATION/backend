package com.lumintra.backend.dashboard.api.controller;

import com.lumintra.backend.dashboard.api.request.ManagerDashboardFilterRequest;
import com.lumintra.backend.dashboard.api.request.ManagerRevenueDrilldownRequest;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse.*;
import com.lumintra.backend.dashboard.api.response.ManagerOrderAuditDetailResponse;
import com.lumintra.backend.dashboard.api.response.ManagerRevenueDrilldownResponse;
import com.lumintra.backend.dashboard.application.GetManagerDashboardKpiUseCase;
import com.lumintra.backend.dashboard.application.GetManagerRevenueDrilldownUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagerDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagerDashboardControllerTest {

    private static final String BASE_PATH = "/api/management/admin/dashboard/kpi";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetManagerDashboardKpiUseCase getManagerDashboardKpiUseCase;

    @MockitoBean
    private GetManagerRevenueDrilldownUseCase getManagerRevenueDrilldownUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private ManagerKpiOverviewResponse sampleResponse() {
        KpiSummary summary = new KpiSummary(
                new BigDecimal("1250000000"),
                15.4,
                "vs. cùng kỳ tháng trước (19 ngày)",
                8L,
                5L,
                3L,
                2L,
                4L,
                3L,
                6L
        );
        List<RevenueCategoryItem> categories = List.of(
                new RevenueCategoryItem("HARDWARE", new BigDecimal("725000000"), 58.0, "#3B82F6", 145L),
                new RevenueCategoryItem("COMMISSION", new BigDecimal("250000000"), 20.0, "#10B981", 45L)
        );
        List<TopProductItem> topProducts = List.of(
                new TopProductItem("1", "Màn hình LED P4", "HARDWARE", 120L, new BigDecimal("600000000"), 1, "https://cdn.com/led.png", "up", "In stock")
        );
        List<LowStockLedItem> lowStock = List.of(
                new LowStockLedItem("10", "LED-P4-64X64", "LED Ma Trận", "64x64", 2L, 10L, 0L, new BigDecimal("4500000"), "LOW")
        );
        List<WarrantyAlertItem> warranty = List.of(
                new WarrantyAlertItem("15", "Nguyen Van A", "a@lumintra.test", "LED 64x64", "SN-001", "Nhieu diem anh", "2026-11-20T08:30:00Z", "PENDING")
        );
        return new ManagerKpiOverviewResponse(summary, categories, topProducts, lowStock, warranty);
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID08 [N]: Authenticated MANAGER receives 200 OK with formatted JSON envelope")
    void getKpiOverview_managerRole_returns200() throws Exception {
        when(getManagerDashboardKpiUseCase.execute(eq("42"), any(ManagerDashboardFilterRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(BASE_PATH)
                        .param("month", "2026-11")
                        .param("compareWith", "2026-10")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpiSummary.totalRevenue").value(1250000000))
                .andExpect(jsonPath("$.data.kpiSummary.revenueGrowthPercent").value(15.4))
                .andExpect(jsonPath("$.data.kpiSummary.pendingCharactersCount").value(8))
                .andExpect(jsonPath("$.data.kpiSummary.pendingCommissionsCount").value(5))
                .andExpect(jsonPath("$.data.revenueByCategory[0].category").value("HARDWARE"))
                .andExpect(jsonPath("$.data.topSellingProducts[0].name").value("Màn hình LED P4"))
                .andExpect(jsonPath("$.data.lowStockLeds[0].modelCode").value("LED-P4-64X64"))
                .andExpect(jsonPath("$.data.kpiSummary.pendingWarrantyClaimsCount").value(3))
                .andExpect(jsonPath("$.data.warrantyAlerts[0].ticketId").value("15"))
                .andExpect(jsonPath("$.data.warrantyAlerts[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID09 [N]: Authenticated ADMIN receives 200 OK")
    void getKpiOverview_adminRole_returns200() throws Exception {
        when(getManagerDashboardKpiUseCase.execute(eq("1"), any(ManagerDashboardFilterRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpiSummary.totalRevenue").value(1250000000));
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID10 [A]: Invalid month pattern receives 400 VALIDATION_FAILED")
    void getKpiOverview_invalidMonthFormat_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("month", "2026-130")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getManagerDashboardKpiUseCase);
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID11 [A]: Unauthenticated request receives 401 UNAUTHORIZED")
    void getKpiOverview_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(getManagerDashboardKpiUseCase);
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID12 [B]: Caller with DESIGNER role receives 403 FORBIDDEN")
    void getKpiOverview_designerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagerDashboardKpiUseCase);
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID13 [B]: Caller with STAFF role receives 403 FORBIDDEN")
    void getKpiOverview_staffRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("20").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagerDashboardKpiUseCase);
    }

    @Test
    @DisplayName("MGT-01 Controller UTCID14 [N]: refresh=true bypasses cache via execute(subject, request, true)")
    void getKpiOverview_refreshTrue_invokesFreshExecute() throws Exception {
        when(getManagerDashboardKpiUseCase.execute(eq("42"), any(ManagerDashboardFilterRequest.class), eq(true)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(BASE_PATH)
                        .param("month", "2026-11")
                        .param("compareWith", "2026-10")
                        .param("refresh", "true")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpiSummary.totalRevenue").value(1250000000));
    }

    @Test
    @DisplayName("Drill-down Controller [N]: MANAGER receives 200 OK with contributing orders for category")
    void getRevenueSources_managerRole_returns200() throws Exception {
        java.time.Instant now = java.time.Instant.parse("2026-11-24T08:00:00Z");
        ManagerRevenueDrilldownResponse resp = new ManagerRevenueDrilldownResponse(
                "2026-11",
                "CATEGORY",
                "Phần cứng",
                "HARDWARE",
                null,
                now.minusSeconds(3600),
                now,
                new BigDecimal("500000.00"),
                2L,
                1L,
                List.of(new ManagerRevenueDrilldownResponse.RevenueSourceItem(
                        "ORDER", 38L, "#38", "Nguyễn Minh Anh", "minhanh@gmail.com",
                        "COMPLETED", "PHYSICAL", 1L, "Lumintra Matrix 64x64 Pro", "HARDWARE",
                        2L, new BigDecimal("250000.00"), new BigDecimal("500000.00"),
                        new BigDecimal("525000.00"), now
                ))
        );
        when(getManagerRevenueDrilldownUseCase.execute(eq("42"), any(ManagerRevenueDrilldownRequest.class)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/management/admin/dashboard/revenue-sources")
                        .param("month", "2026-11")
                        .param("category", "HARDWARE")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeType").value("CATEGORY"))
                .andExpect(jsonPath("$.data.items[0].referenceCode").value("#38"))
                .andExpect(jsonPath("$.data.items[0].contributionAmount").value(500000.00));
    }

    @Test
    @DisplayName("Drill-down Controller [A]: Invalid category returns 400 VALIDATION_FAILED")
    void getRevenueSources_invalidCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/management/admin/dashboard/revenue-sources")
                        .param("month", "2026-11")
                        .param("category", "INVALID_CAT")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getManagerRevenueDrilldownUseCase);
    }

    @Test
    @DisplayName("Drill-down Order Audit [N]: MANAGER receives 200 OK with full order line items")
    void getOrderAuditDetail_managerRole_returns200() throws Exception {
        java.time.Instant now = java.time.Instant.parse("2026-11-24T08:00:00Z");
        ManagerOrderAuditDetailResponse detail = new ManagerOrderAuditDetailResponse(
                38L, "#38", "Nguyễn Minh Anh", "minhanh@gmail.com",
                "COMPLETED", "MIXED",
                new BigDecimal("300000.00"), new BigDecimal("25000.00"), new BigDecimal("325000.00"),
                now, "LMTORD38SALES001", "SEPAY", "SUCCESS", now,
                List.of(new ManagerOrderAuditDetailResponse.OrderLineItem(
                        10L, 1L, "Lumintra Matrix 64x64 Pro", "HARDWARE",
                        1L, new BigDecimal("250000.00"), new BigDecimal("250000.00")
                ))
        );
        when(getManagerRevenueDrilldownUseCase.getOrderAuditDetail("42", 38L)).thenReturn(detail);

        mockMvc.perform(get("/api/management/admin/dashboard/revenue-sources/orders/38")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceCode").value("#38"))
                .andExpect(jsonPath("$.data.paymentCode").value("LMTORD38SALES001"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Lumintra Matrix 64x64 Pro"));
    }
}
