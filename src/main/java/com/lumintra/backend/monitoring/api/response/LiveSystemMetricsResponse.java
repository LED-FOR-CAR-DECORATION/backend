package com.lumintra.backend.monitoring.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Live API traffic, latency, errors and component health for the backend instance that served the request")
public record LiveSystemMetricsResponse(
        @Schema(description = "When this response was built", example = "2026-11-26T22:45:03+07:00")
        OffsetDateTime generatedAt,
        WindowInfo window,
        InstanceInfo instance,
        Overview overview,
        @Schema(description = "Endpoints with traffic in the window, sorted by the requested field and capped at limit")
        List<EndpointItem> endpoints,
        Components components
) {

    @Schema(description = "Time range the figures cover. 'from' is later than requested when the instance has not been running for the whole window.")
    public record WindowInfo(
            @Schema(example = "2026-11-26T22:30:00+07:00") OffsetDateTime from,
            @Schema(example = "2026-11-26T22:45:00+07:00") OffsetDateTime to
    ) {
    }

    public record InstanceInfo(
            @Schema(example = "api-host-25884") String instanceId,
            @Schema(example = "2026-11-26T22:29:41+07:00") OffsetDateTime startedAt,
            @Schema(example = "922") long uptimeSeconds
    ) {
    }

    @Schema(description = "Totals across all recorded endpoints. Latency fields and errorRate are null when there were no requests.")
    public record Overview(
            @Schema(example = "1240") long totalRequests,
            @Schema(example = "82.7") double requestsPerMinute,
            @Schema(example = "701.3") Double avgLatencyMs,
            @Schema(example = "300.0") Double p50LatencyMs,
            @Schema(example = "1500.0") Double p95LatencyMs,
            @Schema(example = "2000.0") Double p99LatencyMs,
            @Schema(example = "1426.2") Double maxLatencyMs,
            @Schema(example = "37") long clientErrorCount,
            @Schema(example = "5") long serverErrorCount,
            @Schema(description = "Server errors (5xx) divided by all requests, from 0 to 1", example = "0.004") Double errorRate
    ) {
    }

    public record EndpointItem(
            @Schema(example = "GET") String method,
            @Schema(example = "/api/management/designer/characters/my") String uri,
            @Schema(example = "64") long count,
            @Schema(example = "715.0") Double avgLatencyMs,
            @Schema(example = "1000.0") Double p95LatencyMs,
            @Schema(example = "1180.4") Double p99LatencyMs,
            @Schema(example = "1180.4") Double maxLatencyMs,
            @Schema(example = "0") long clientErrors,
            @Schema(example = "0") long serverErrors,
            @Schema(description = "Server errors (5xx) divided by requests, from 0 to 1", example = "0.0") Double errorRate
    ) {
    }

    public record Components(DbStatus db, RedisStatus redis, JvmStatus jvm) {
    }

    public record DbStatus(
            @Schema(allowableValues = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"}, example = "UP") String status,
            @Schema(example = "1") Integer activeConnections,
            @Schema(example = "2") Integer idleConnections,
            @Schema(example = "0") Integer pendingConnections
    ) {
    }

    public record RedisStatus(
            @Schema(allowableValues = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"}, example = "UP") String status
    ) {
    }

    public record JvmStatus(
            @Schema(example = "412.5") Double heapUsedMb,
            @Schema(example = "2048.0") Double heapMaxMb,
            @Schema(example = "58") Integer threadsLive,
            @Schema(description = "Recent CPU usage of the backend process, from 0 to 1", example = "0.12") Double cpuUsage
    ) {
    }
}
