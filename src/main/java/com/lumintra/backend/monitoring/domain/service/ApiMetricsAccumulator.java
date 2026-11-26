package com.lumintra.backend.monitoring.domain.service;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;

/**
 * Adds up per-minute endpoint metrics and derives the figures defined in BR-GEN-26.
 * Counts, total time and latency slots are summed; max is the largest max seen.
 * Not thread-safe; create one per calculation.
 */
public class ApiMetricsAccumulator {

    private long requestCount;
    private long clientErrorCount;
    private long serverErrorCount;
    private double totalTimeMs;
    private double maxTimeMs;
    private final long[] latencySlots = new long[LatencyBuckets.SLOT_COUNT];

    public ApiMetricsAccumulator add(ApiEndpointMetrics metrics) {
        requestCount += metrics.requestCount();
        clientErrorCount += metrics.clientErrorCount();
        serverErrorCount += metrics.serverErrorCount();
        totalTimeMs += metrics.totalTimeMs();
        maxTimeMs = Math.max(maxTimeMs, metrics.maxTimeMs());
        long[] slots = metrics.latencyBucketCounts();
        for (int i = 0; i < Math.min(slots.length, latencySlots.length); i++) {
            latencySlots[i] += slots[i];
        }
        return this;
    }

    public long requestCount() {
        return requestCount;
    }

    public long clientErrorCount() {
        return clientErrorCount;
    }

    public long serverErrorCount() {
        return serverErrorCount;
    }

    /** Average latency in ms, or {@code null} without requests. */
    public Double avgLatencyMs() {
        return requestCount == 0 ? null : totalTimeMs / requestCount;
    }

    /** Largest latency in ms, or {@code null} without requests. */
    public Double maxLatencyMs() {
        return requestCount == 0 ? null : maxTimeMs;
    }

    /** Estimated latency percentile in ms (see {@link LatencyBuckets#percentileMs}), or {@code null} without requests. */
    public Double percentileLatencyMs(double percentile) {
        return LatencyBuckets.percentileMs(latencySlots, percentile, maxTimeMs);
    }

    /** Server errors (5xx) divided by all requests, or {@code null} without requests. */
    public Double errorRate() {
        return requestCount == 0 ? null : (double) serverErrorCount / requestCount;
    }
}
