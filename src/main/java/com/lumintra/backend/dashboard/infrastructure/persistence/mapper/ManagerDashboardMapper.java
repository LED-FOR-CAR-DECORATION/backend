package com.lumintra.backend.dashboard.infrastructure.persistence.mapper;

import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse.*;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.CategoryRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.LowStockLedRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.TopProductRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.WarrantyAlertRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class ManagerDashboardMapper {

    private static final String COLOR_HARDWARE = "#3B82F6";
    private static final String COLOR_COMMISSION = "#10B981";
    private static final String COLOR_BUNDLE = "#F59E0B";
    private static final String COLOR_CHARACTER = "#8B5CF6";
    private static final String COLOR_SUBSCRIPTION = "#EC4899";

    public ManagerKpiOverviewResponse toResponse(
            BigDecimal totalRevenue,
            double growthPercent,
            String revenuePeriod,
            long pendingCharactersCount,
            long pendingCommissionsCount,
            long pendingWarrantyClaimsCount,
            long lowStockModelsCount,
            long activeBundlesCount,
            long activeSubscriptionsCount,
            long activePriceConfigsCount,
            List<CategoryRevenueRow> orderCategoryRows,
            CategoryRevenueRow commissionRow,
            List<TopProductRow> topProductRows,
            List<LowStockLedRow> lowStockLedRows,
            List<WarrantyAlertRow> warrantyAlertRows) {

        KpiSummary summary = new KpiSummary(
                totalRevenue,
                roundToOneDecimal(growthPercent),
                revenuePeriod,
                pendingCharactersCount,
                pendingCommissionsCount,
                pendingWarrantyClaimsCount,
                lowStockModelsCount,
                activeBundlesCount,
                activeSubscriptionsCount,
                activePriceConfigsCount
        );

        List<RevenueCategoryItem> categories = buildCategoryBreakdown(totalRevenue, orderCategoryRows, commissionRow);
        List<TopProductItem> topProducts = buildTopProducts(topProductRows);
        List<LowStockLedItem> lowStockLeds = buildLowStockLeds(lowStockLedRows);
        List<WarrantyAlertItem> warrantyAlerts = buildWarrantyAlerts(warrantyAlertRows);

        return new ManagerKpiOverviewResponse(summary, categories, topProducts, lowStockLeds, warrantyAlerts);
    }

    private List<WarrantyAlertItem> buildWarrantyAlerts(List<WarrantyAlertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<WarrantyAlertItem> items = new ArrayList<>();
        for (WarrantyAlertRow row : rows) {
            items.add(new WarrantyAlertItem(
                    row.ticketId() != null ? row.ticketId().toString() : null,
                    row.customerName(),
                    row.customerEmail(),
                    row.deviceModel(),
                    row.deviceSerial(),
                    row.issueDescription(),
                    row.reportedAt() != null ? row.reportedAt().toString() : null,
                    row.status()
            ));
        }
        return items;
    }

    private List<RevenueCategoryItem> buildCategoryBreakdown(
            BigDecimal totalRevenue,
            List<CategoryRevenueRow> orderCategoryRows,
            CategoryRevenueRow commissionRow) {

        Map<String, CategoryData> map = new LinkedHashMap<>();
        map.put("HARDWARE", new CategoryData(COLOR_HARDWARE));
        map.put("COMMISSION", new CategoryData(COLOR_COMMISSION));
        map.put("BUNDLE", new CategoryData(COLOR_BUNDLE));
        map.put("CHARACTER", new CategoryData(COLOR_CHARACTER));
        map.put("SUBSCRIPTION", new CategoryData(COLOR_SUBSCRIPTION));

        if (orderCategoryRows != null) {
            for (CategoryRevenueRow row : orderCategoryRows) {
                String targetCat = switch (row.productType() != null ? row.productType().toUpperCase(Locale.ROOT) : "") {
                    case "LED" -> "HARDWARE";
                    case "BUNDLE" -> "BUNDLE";
                    case "CHARACTER" -> "CHARACTER";
                    case "SUBSCRIPTION" -> "SUBSCRIPTION";
                    default -> null;
                };
                if (targetCat != null && map.containsKey(targetCat)) {
                    CategoryData data = map.get(targetCat);
                    data.revenue = data.revenue.add(row.revenue());
                    data.unitCount += row.unitsSold();
                }
            }
        }

        if (commissionRow != null) {
            CategoryData commData = map.get("COMMISSION");
            commData.revenue = commData.revenue.add(commissionRow.revenue());
            commData.unitCount += commissionRow.unitsSold();
        }

        List<RevenueCategoryItem> result = new ArrayList<>();
        double totalRevDouble = totalRevenue != null ? totalRevenue.doubleValue() : 0.0;

        for (Map.Entry<String, CategoryData> entry : map.entrySet()) {
            String cat = entry.getKey();
            CategoryData data = entry.getValue();
            double pct = totalRevDouble > 0
                    ? roundToOneDecimal((data.revenue.doubleValue() / totalRevDouble) * 100.0)
                    : 0.0;

            result.add(new RevenueCategoryItem(
                    cat,
                    data.revenue,
                    pct,
                    data.color,
                    data.unitCount
            ));
        }

        return result;
    }

    private List<TopProductItem> buildTopProducts(List<TopProductRow> topProductRows) {
        if (topProductRows == null || topProductRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<TopProductItem> items = new ArrayList<>();
        int rank = 1;
        for (TopProductRow row : topProductRows) {
            String category = switch (row.productType() != null ? row.productType().toUpperCase(Locale.ROOT) : "") {
                case "LED" -> "HARDWARE";
                case "BUNDLE" -> "BUNDLE";
                case "CHARACTER" -> "CHARACTER";
                case "SUBSCRIPTION" -> "SUBSCRIPTION";
                default -> "HARDWARE";
            };

            items.add(new TopProductItem(
                    row.productId() != null ? row.productId().toString() : String.valueOf(rank),
                    row.productName() != null ? row.productName() : "Sản phẩm #" + rank,
                    category,
                    row.totalUnits(),
                    row.totalRevenue() != null ? row.totalRevenue() : BigDecimal.ZERO,
                    rank++,
                    row.thumbnailUrl() != null ? row.thumbnailUrl() : "https://cdn.lumintra.com/placeholder.png",
                    "up",
                    "In stock"
            ));
        }
        return items;
    }

    private List<LowStockLedItem> buildLowStockLeds(List<LowStockLedRow> lowStockLedRows) {
        if (lowStockLedRows == null || lowStockLedRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<LowStockLedItem> items = new ArrayList<>();
        for (LowStockLedRow row : lowStockLedRows) {
            long currentStock = row.stockQuantity() != null ? row.stockQuantity() : 0L;
            String urgency;
            if (currentStock == 0) {
                urgency = "CRITICAL";
            } else if (currentStock < 5) {
                urgency = "LOW";
            } else {
                urgency = "NORMAL";
            }

            String dimensions = (row.widthPx() != null && row.heightPx() != null)
                    ? (row.widthPx() + "x" + row.heightPx())
                    : "—";

            String productId = row.productId() != null ? row.productId().toString()
                    : (row.modelId() != null ? row.modelId().toString() : "0");

            items.add(new LowStockLedItem(
                    productId,
                    row.modelName() != null ? row.modelName() : "LED-" + dimensions,
                    row.modelName() != null ? row.modelName() : "LED Ma Trận " + dimensions,
                    dimensions,
                    currentStock,
                    10L,
                    0L,
                    row.basePrice() != null ? row.basePrice() : BigDecimal.ZERO,
                    urgency
            ));
        }
        return items;
    }

    private static double roundToOneDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static class CategoryData {
        private final String color;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long unitCount = 0;

        CategoryData(String color) {
            this.color = color;
        }
    }
}
