package com.lumintra.backend.monitoring.domain.model;

import java.util.Arrays;

/**
 * Aggregated request metrics for one endpoint (HTTP method + route template) over one interval.
 *
 * @param latencyBucketCounts per-slot request counts, laid out as described in {@link LatencyBuckets}
 */
public record ApiEndpointMetrics(
        String httpMethod,
        String uriTemplate,
        long requestCount,
        long clientErrorCount,
        long serverErrorCount,
        double totalTimeMs,
        double maxTimeMs,
        long[] latencyBucketCounts
) {

    public ApiEndpointMetrics {
        latencyBucketCounts = Arrays.copyOf(latencyBucketCounts, latencyBucketCounts.length);
    }

    @Override
    public long[] latencyBucketCounts() {
        return Arrays.copyOf(latencyBucketCounts, latencyBucketCounts.length);
    }
}
