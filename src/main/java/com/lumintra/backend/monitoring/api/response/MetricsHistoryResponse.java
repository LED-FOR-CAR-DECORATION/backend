package com.lumintra.backend.monitoring.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "API performance over a time range, from stored per-minute summaries of all backend instances")
public record MetricsHistoryResponse(
        @Schema(description = "Start of the first period", example = "2026-11-27T00:00:00+07:00")
        OffsetDateTime from,
        @Schema(description = "End of the range (exclusive); never later than the start of the current minute", example = "2026-11-28T00:00:00+07:00")
        OffsetDateTime to,
        @Schema(allowableValues = {"1m", "5m", "1h", "1d"}, example = "1h")
        String interval,
        Filter filter,
        @Schema(description = "Figures over the whole range")
        Stats summary,
        @Schema(description = "One point per period, oldest first. Periods without traffic have zero counts and null latencies.")
        List<Point> points,
        @Schema(description = "Top 10 endpoints by p95 latency over the range. Empty when filtering by uri.")
        List<EndpointItem> topEndpoints
) {

    public record Filter(
            @Schema(example = "GET", nullable = true) String method,
            @Schema(example = "/api/orders/{id}", nullable = true) String uri
    ) {
    }

    @Schema(description = "Latency fields and errorRate are null when there were no requests")
    public record Stats(
            @Schema(example = "1240") long requestCount,
            @Schema(description = "Requests divided by the minutes covered", example = "0.9") double requestsPerMinute,
            @Schema(example = "180.4") Double avgLatencyMs,
            @Schema(example = "100.0") Double p50LatencyMs,
            @Schema(example = "750.0") Double p95LatencyMs,
            @Schema(example = "1500.0") Double p99LatencyMs,
            @Schema(example = "1889.3") Double maxLatencyMs,
            @Schema(example = "37") long clientErrors,
            @Schema(example = "5") long serverErrors,
            @Schema(description = "Server errors (5xx) divided by requests, from 0 to 1", example = "0.004") Double errorRate
    ) {
    }

    @Schema(description = "Latency fields and errorRate are null when the period had no requests")
    public record Point(
            @Schema(example = "2026-11-27T09:00:00+07:00") OffsetDateTime periodStart,
            @Schema(example = "52") long requestCount,
            @Schema(example = "0.9") double requestsPerMinute,
            @Schema(example = "180.4") Double avgLatencyMs,
            @Schema(example = "100.0") Double p50LatencyMs,
            @Schema(example = "750.0") Double p95LatencyMs,
            @Schema(example = "1500.0") Double p99LatencyMs,
            @Schema(example = "1889.3") Double maxLatencyMs,
            @Schema(example = "2") long clientErrors,
            @Schema(example = "0") long serverErrors,
            @Schema(example = "0.0") Double errorRate
    ) {
    }

    public record EndpointItem(
            @Schema(example = "GET") String method,
            @Schema(example = "/api/store/products") String uri,
            @Schema(example = "320") long count,
            @Schema(example = "240.6") Double avgLatencyMs,
            @Schema(example = "1000.0") Double p95LatencyMs,
            @Schema(example = "2000.0") Double p99LatencyMs,
            @Schema(example = "1889.3") Double maxLatencyMs,
            @Schema(example = "0") long clientErrors,
            @Schema(example = "0") long serverErrors,
            @Schema(example = "0.0") Double errorRate
    ) {
    }
}
