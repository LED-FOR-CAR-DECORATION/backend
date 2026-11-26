package com.lumintra.backend.monitoring.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * API metrics for one minute.
 *
 * @param bucketStart start of the minute the metrics cover (UTC, minute-aligned)
 * @param capturedAt  when the snapshot was taken
 * @param endpoints   one entry per endpoint that received at least one request in that minute
 */
public record MetricSnapshot(Instant bucketStart, Instant capturedAt, List<ApiEndpointMetrics> endpoints) {

    public MetricSnapshot {
        endpoints = List.copyOf(endpoints);
    }
}
