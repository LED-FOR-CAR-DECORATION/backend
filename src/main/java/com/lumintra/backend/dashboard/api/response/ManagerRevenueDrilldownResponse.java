package com.lumintra.backend.dashboard.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Traceable revenue drill-down listing exact orders and commissions backing a dashboard metric")
public record ManagerRevenueDrilldownResponse(
        @Schema(description = "Reporting month (YYYY-MM)", example = "2026-11")
        String month,

        @Schema(description = "Drill-down scope type: ALL, CATEGORY, or PRODUCT", example = "CATEGORY")
        String scopeType,

        @Schema(description = "Human-readable Vietnamese scope label", example = "Phần cứng")
        String scopeLabel,

        @Schema(description = "Category code when scoped by category or product", example = "HARDWARE")
        String category,

        @Schema(description = "Product ID when scoped by a specific product", example = "1")
        Long productId,

        @Schema(description = "Inclusive reporting period start timestamp (UTC)")
        Instant periodStart,

        @Schema(description = "Inclusive reporting period end timestamp (UTC)")
        Instant periodEnd,

        @Schema(description = "Exact total revenue summed across returned sources", example = "5000000.00")
        BigDecimal totalRevenue,

        @Schema(description = "Total units summed across returned sources", example = "22")
        long totalUnits,

        @Schema(description = "Count of unique contributing orders or commissions", example = "15")
        long distinctSourceCount,

        @Schema(description = "Chronological list of orders/commissions contributing to this metric")
        List<RevenueSourceItem> items
) {
    @Schema(description = "Single order, order line item, or commission contributing to dashboard revenue")
    public record RevenueSourceItem(
            @Schema(description = "Source entity type: ORDER or COMMISSION", example = "ORDER")
            String sourceType,

            @Schema(description = "Numeric orderId or commissionId", example = "38")
            Long referenceId,

            @Schema(description = "Display reference code", example = "#38")
            String referenceCode,

            @Schema(description = "Customer full name", example = "Nguyễn Minh Anh")
            String customerName,

            @Schema(description = "Customer email", example = "minhanh.nguyen@gmail.com")
            String customerEmail,

            @Schema(description = "Current order or commission status", example = "COMPLETED")
            String status,

            @Schema(description = "Order type (PHYSICAL, DIGITAL, MIXED, SUBSCRIPTION, COMMISSION)", example = "PHYSICAL")
            String orderType,

            @Schema(description = "Store product ID (null for commissions or multi-item ALL order summary)", example = "1")
            Long productId,

            @Schema(description = "Product name, commission title, or order item summary", example = "Lumintra Matrix 64x64 Pro")
            String itemName,

            @Schema(description = "Category code (HARDWARE, COMMISSION, BUNDLE, CHARACTER, SUBSCRIPTION, MIXED)", example = "HARDWARE")
            String category,

            @Schema(description = "Quantity contributed by this row", example = "2")
            long quantity,

            @Schema(description = "Unit price (VND)", example = "250000.00")
            BigDecimal unitPrice,

            @Schema(description = "Exact amount contributed to the drilled metric (VND)", example = "500000.00")
            BigDecimal contributionAmount,

            @Schema(description = "Full order/commission total amount (VND)", example = "525000.00")
            BigDecimal orderTotalAmount,

            @Schema(description = "Order/commission creation timestamp (UTC)")
            Instant createdAt
    ) {}
}
