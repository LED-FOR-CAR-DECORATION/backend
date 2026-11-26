package com.lumintra.backend.monitoring.infrastructure.metrics;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Accumulates per-endpoint request metrics in memory until the next {@link #drain()}.
 * <p>
 * Requests are keyed by HTTP method + route template only (BR-GEN-23). Infrastructure endpoints,
 * CORS preflight requests and requests that matched no route are ignored (BR-GEN-24).
 * Recording only touches in-memory counters, so it adds no database work per request.
 */
@Component
public class ApiRequestMetricsRecorder {

    private static final List<String> EXCLUDED_URI_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs");
    private static final Set<String> EXCLUDED_URIS = Set.of("/ws");
    private static final String EXCLUDED_METHOD = "OPTIONS";

    private final AtomicReference<Interval> current = new AtomicReference<>(new Interval());

    /**
     * Records one completed request.
     *
     * @param httpMethod    request method
     * @param uriTemplate   matched route template, or {@code null} when no route matched
     * @param status        response status code
     * @param failed        whether the request ended with an unhandled exception
     * @param durationNanos server processing time
     */
    public void record(String httpMethod, String uriTemplate, int status, boolean failed, long durationNanos) {
        if (isExcluded(httpMethod, uriTemplate)) {
            return;
        }
        boolean serverError = status >= 500 || (failed && status < 400);
        boolean clientError = !serverError && status >= 400;
        current.get()
                .endpoint(new EndpointKey(httpMethod, uriTemplate))
                .record(clientError, serverError, Math.max(durationNanos, 0));
    }

    /**
     * Returns everything recorded since the previous call and starts a new interval.
     * A request finishing at the exact moment of the swap may land in either interval.
     */
    public List<ApiEndpointMetrics> drain() {
        Interval finished = current.getAndSet(new Interval());
        List<ApiEndpointMetrics> result = new ArrayList<>(finished.endpoints.size());
        finished.endpoints.forEach((key, counters) -> {
            ApiEndpointMetrics metrics = counters.toMetrics(key);
            if (metrics.requestCount() > 0) {
                result.add(metrics);
            }
        });
        result.sort(Comparator.comparing(ApiEndpointMetrics::uriTemplate).thenComparing(ApiEndpointMetrics::httpMethod));
        return result;
    }

    static boolean isExcluded(String httpMethod, String uriTemplate) {
        if (httpMethod == null || EXCLUDED_METHOD.equals(httpMethod)) {
            return true;
        }
        if (uriTemplate == null || uriTemplate.isEmpty()) {
            return true;
        }
        if (EXCLUDED_URIS.contains(uriTemplate) || uriTemplate.startsWith("/ws/")) {
            return true;
        }
        return EXCLUDED_URI_PREFIXES.stream()
                .anyMatch(prefix -> uriTemplate.equals(prefix) || uriTemplate.startsWith(prefix + "/"));
    }

    private record EndpointKey(String httpMethod, String uriTemplate) {
    }

    private static final class Interval {
        private final Map<EndpointKey, EndpointCounters> endpoints = new ConcurrentHashMap<>();

        EndpointCounters endpoint(EndpointKey key) {
            return endpoints.computeIfAbsent(key, ignored -> new EndpointCounters());
        }
    }

    private static final class EndpointCounters {
        private final LongAdder requests = new LongAdder();
        private final LongAdder clientErrors = new LongAdder();
        private final LongAdder serverErrors = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAccumulator maxNanos = new LongAccumulator(Math::max, 0);
        private final LongAdder[] buckets = new LongAdder[LatencyBuckets.SLOT_COUNT];

        EndpointCounters() {
            for (int i = 0; i < buckets.length; i++) {
                buckets[i] = new LongAdder();
            }
        }

        void record(boolean clientError, boolean serverError, long durationNanos) {
            requests.increment();
            if (clientError) {
                clientErrors.increment();
            }
            if (serverError) {
                serverErrors.increment();
            }
            totalNanos.add(durationNanos);
            maxNanos.accumulate(durationNanos);
            buckets[LatencyBuckets.slotFor(durationNanos)].increment();
        }

        ApiEndpointMetrics toMetrics(EndpointKey key) {
            long[] bucketCounts = new long[buckets.length];
            for (int i = 0; i < buckets.length; i++) {
                bucketCounts[i] = buckets[i].sum();
            }
            return new ApiEndpointMetrics(
                    key.httpMethod(),
                    key.uriTemplate(),
                    requests.sum(),
                    clientErrors.sum(),
                    serverErrors.sum(),
                    totalNanos.sum() / 1_000_000.0,
                    maxNanos.get() / 1_000_000.0,
                    bucketCounts
            );
        }
    }
}
