package com.lumintra.backend.monitoring.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Schema(description = "Query parameters for the metrics history view")
public record MetricsHistoryRequest(

        @Schema(description = "Start of the range (ISO-8601 with offset), within the last 30 days. Rounded down to the start of its period.",
                example = "2026-11-27T00:00:00+07:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "from is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,

        @Schema(description = "End of the range (ISO-8601 with offset), exclusive. Times after the current minute are cut off.",
                example = "2026-11-28T00:00:00+07:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "to is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to,

        @Schema(description = "Period size of each point. 1d periods follow calendar days in Asia/Ho_Chi_Minh.",
                allowableValues = {"1m", "5m", "15m", "1h", "1d"}, defaultValue = "1h")
        @Pattern(regexp = "^(1m|5m|15m|1h|1d)$", message = "interval must be one of 1m, 5m, 15m, 1h, 1d")
        String interval,

        @Schema(description = "Only include this HTTP method", example = "GET")
        @Pattern(regexp = "^(GET|HEAD|POST|PUT|PATCH|DELETE)$", message = "method must be one of GET, HEAD, POST, PUT, PATCH, DELETE")
        String method,

        @Schema(description = "Only include this route template", example = "/api/orders/{id}")
        @Size(max = 255, message = "uri must be at most 255 characters")
        String uri
) {
    public static final String DEFAULT_INTERVAL = "1h";

    public MetricsHistoryRequest {
        if (interval == null || interval.isBlank()) {
            interval = DEFAULT_INTERVAL;
        }
        if (method != null && method.isBlank()) {
            method = null;
        }
        if (uri != null && uri.isBlank()) {
            uri = null;
        }
    }
}
