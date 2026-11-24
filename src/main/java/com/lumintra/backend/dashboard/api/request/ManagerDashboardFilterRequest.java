package com.lumintra.backend.dashboard.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Schema(description = "Query parameters for retrieving manager operations KPI and analytics overview")
public record ManagerDashboardFilterRequest(

        @Schema(description = "Reporting target month in YYYY-MM format. Defaults to current month if omitted.", example = "2026-11")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be in YYYY-MM format")
        String month,

        @Schema(description = "Comparison reference month in YYYY-MM format. Defaults to previous month if omitted.", example = "2026-10")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "compareWith must be in YYYY-MM format")
        String compareWith
) {
    public ManagerDashboardFilterRequest {
        if (month == null || month.isBlank()) {
            month = YearMonth.now().toString();
        }
        if (compareWith == null || compareWith.isBlank()) {
            try {
                YearMonth current = YearMonth.parse(month);
                compareWith = current.minusMonths(1).toString();
            } catch (DateTimeParseException ex) {
                compareWith = YearMonth.now().minusMonths(1).toString();
            }
        }
    }
}
