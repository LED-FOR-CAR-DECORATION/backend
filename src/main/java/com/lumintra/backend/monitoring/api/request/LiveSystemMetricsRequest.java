package com.lumintra.backend.monitoring.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Query parameters for the live system metrics view")
public record LiveSystemMetricsRequest(

        @Schema(description = "Time range ending at the latest completed minute", allowableValues = {"5m", "15m", "60m"}, defaultValue = "15m")
        @Pattern(regexp = "^(5m|15m|60m)$", message = "window must be one of 5m, 15m, 60m")
        String window,

        @Schema(description = "Field the endpoint list is sorted by, highest first", allowableValues = {"count", "avgMs", "p95Ms", "errorRate"}, defaultValue = "p95Ms")
        @Pattern(regexp = "^(count|avgMs|p95Ms|errorRate)$", message = "sort must be one of count, avgMs, p95Ms, errorRate")
        String sort,

        @Schema(description = "Maximum number of endpoints returned", minimum = "1", maximum = "50", defaultValue = "10")
        @Min(value = 1, message = "limit must be between 1 and 50")
        @Max(value = 50, message = "limit must be between 1 and 50")
        Integer limit
) {
    public static final String DEFAULT_WINDOW = "15m";
    public static final String DEFAULT_SORT = "p95Ms";
    public static final int DEFAULT_LIMIT = 10;

    public LiveSystemMetricsRequest {
        if (window == null || window.isBlank()) {
            window = DEFAULT_WINDOW;
        }
        if (sort == null || sort.isBlank()) {
            sort = DEFAULT_SORT;
        }
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
