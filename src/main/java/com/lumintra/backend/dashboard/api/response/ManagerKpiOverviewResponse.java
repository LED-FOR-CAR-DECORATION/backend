package com.lumintra.backend.dashboard.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Aggregated operational metrics and business performance overview for managers and administrators")
public record ManagerKpiOverviewResponse(

        @Schema(description = "Summary of revenue performance, stock status, and pending approval queues")
        KpiSummary kpiSummary,

        @Schema(description = "Breakdown of revenue and units sold across the 5 core business categories")
        List<RevenueCategoryItem> revenueByCategory,

        @Schema(description = "Top 5 best-selling products by quantity sold in the selected period")
        List<TopProductItem> topSellingProducts,

        @Schema(description = "Detailed list of LED hardware models with critically low inventory levels")
        List<LowStockLedItem> lowStockLeds,

        @Schema(description = "Up to 5 open warranty claims (PENDING first, then APPROVED) awaiting manager action")
        List<WarrantyAlertItem> warrantyAlerts
) {

    @Schema(description = "Open warranty claim alert")
    public record WarrantyAlertItem(
            @Schema(description = "Warranty ticket identifier", example = "15")
            String ticketId,

            @Schema(description = "Customer full name", example = "Nguyễn Văn A")
            String customerName,

            @Schema(description = "Customer email", example = "customer.demo@lumintra.test")
            String customerEmail,

            @Schema(description = "LED hardware model name of the device", example = "LED Ma Trận 64x64")
            String deviceModel,

            @Schema(description = "Device serial number", example = "SN-LED-0001")
            String deviceSerial,

            @Schema(description = "Customer issue description", example = "Màn hình bị nhiễu điểm ảnh")
            String issueDescription,

            @Schema(description = "Claim submission timestamp (ISO-8601 UTC)", example = "2026-11-20T08:30:00Z")
            String reportedDate,

            @Schema(description = "Claim status (PENDING, APPROVED)", example = "PENDING")
            String status
    ) {}

    @Schema(description = "Key performance indicators summary")
    public record KpiSummary(
            @Schema(description = "Total revenue achieved in the reporting period in VND", example = "1250000000")
            BigDecimal totalRevenue,

            @Schema(description = "Percentage growth compared with reference period", example = "15.4")
            double revenueGrowthPercent,

            @Schema(description = "Human-readable label for the comparison timeframe", example = "vs. cùng kỳ tháng trước (19 ngày)")
            String revenuePeriod,

            @Schema(description = "Total store characters submitted by designers awaiting manager approval", example = "8")
            long pendingCharactersCount,

            @Schema(description = "Total customer custom artwork commission requests awaiting initial review", example = "5")
            long pendingCommissionsCount,

            @Schema(description = "Total warranty claims awaiting initial manager review (PENDING)", example = "3")
            long pendingWarrantyClaimsCount,

            @Schema(description = "Total LED models with inventory strictly below minimum threshold", example = "2")
            long lowStockModelsCount,

            @Schema(description = "Total promotional bundles currently active", example = "4")
            long activeBundlesCount,

            @Schema(description = "Total subscription membership tiers currently published on the store", example = "3")
            long activeSubscriptionsCount,

            @Schema(description = "Total active commission pricing configuration rules", example = "6")
            long activePriceConfigsCount
    ) {}

    @Schema(description = "Revenue and unit volume by commercial category")
    public record RevenueCategoryItem(
            @Schema(description = "Business category code (HARDWARE, COMMISSION, BUNDLE, CHARACTER, SUBSCRIPTION)", example = "HARDWARE")
            String category,

            @Schema(description = "Revenue collected from this category in VND", example = "725000000")
            BigDecimal revenue,

            @Schema(description = "Percentage share of total period revenue", example = "58.0")
            double percentage,

            @Schema(description = "Hex color code for direct charting in UI", example = "#3B82F6")
            String color,

            @Schema(description = "Total volume or units sold in this category", example = "145")
            long unitCount
    ) {}

    @Schema(description = "Top-performing product item")
    public record TopProductItem(
            @Schema(description = "Unique product identifier", example = "1")
            String id,

            @Schema(description = "Commercial product display name", example = "Màn hình LED Ma Trận P4 Ô tô 64x32")
            String name,

            @Schema(description = "Product category (HARDWARE, CHARACTER, BUNDLE, SUBSCRIPTION)", example = "HARDWARE")
            String category,

            @Schema(description = "Quantity sold during the selected period", example = "120")
            long unitsSold,

            @Schema(description = "Total revenue generated by this product in VND", example = "600000000")
            BigDecimal revenue,

            @Schema(description = "Sales rank from 1 to 5", example = "1")
            int rank,

            @Schema(description = "Thumbnail image URL", example = "https://cdn.lumintra.com/products/led-p4.png")
            String thumbnail,

            @Schema(description = "Sales velocity trend (up, down, stable)", example = "up")
            String trend,

            @Schema(description = "Stock availability status (In stock, Out of stock)", example = "In stock")
            String stockStatus
    ) {}

    @Schema(description = "LED model inventory alert item")
    public record LowStockLedItem(
            @Schema(description = "Linked product identifier", example = "10")
            String productId,

            @Schema(description = "LED model identifier / code", example = "LED-P4-64X64")
            String modelCode,

            @Schema(description = "Full LED hardware model name", example = "LED Ma Trận Vuông Cao Cấp 64x64")
            String name,

            @Schema(description = "Matrix resolution dimensions", example = "64x64")
            String dimensions,

            @Schema(description = "Current in-stock inventory count", example = "2")
            long currentStock,

            @Schema(description = "Configured minimum safety stock threshold", example = "10")
            long minimumStock,

            @Schema(description = "Units currently reserved in pending processing orders", example = "0")
            long reserved,

            @Schema(description = "Catalog unit price in VND", example = "4500000")
            BigDecimal unitPrice,

            @Schema(description = "Restock urgency level (CRITICAL if 0, LOW if < 5, NORMAL otherwise)", example = "LOW")
            String urgency
    ) {}
}
