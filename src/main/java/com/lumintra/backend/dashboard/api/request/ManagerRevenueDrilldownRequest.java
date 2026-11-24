package com.lumintra.backend.dashboard.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Locale;

@Schema(description = "Query parameters for drilling down into orders and commissions backing manager dashboard revenue metrics")
public record ManagerRevenueDrilldownRequest(

        @Schema(description = "Reporting target month in YYYY-MM format. Defaults to current month if omitted.", example = "2026-11")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be in YYYY-MM format")
        String month,

        @Schema(description = "Optional revenue category filter: ALL, HARDWARE, COMMISSION, BUNDLE, CHARACTER, SUBSCRIPTION", example = "HARDWARE")
        @Pattern(
                regexp = "^(?i)(ALL|HARDWARE|COMMISSION|BUNDLE|CHARACTER|SUBSCRIPTION)$",
                message = "category must be one of ALL, HARDWARE, COMMISSION, BUNDLE, CHARACTER, SUBSCRIPTION"
        )
        String category,

        @Schema(description = "Optional store product ID to inspect exact orders for a top-selling product", example = "1")
        Long productId
) {
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ManagerRevenueDrilldownRequest {
        if (month == null || month.isBlank()) {
            month = YearMonth.now(REPORTING_ZONE).toString();
        }
        if (category != null && !category.isBlank()) {
            category = category.trim().toUpperCase(Locale.ROOT);
        } else {
            category = null;
        }
    }
}
