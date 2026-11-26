package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.infrastructure.metrics.ApiRequestMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestMetricsRecorderTest {

    private static final long MS = 1_000_000L;

    private ApiRequestMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ApiRequestMetricsRecorder();
    }

    @Test
    @DisplayName("UTCID01 [N]: AC04 requests to one endpoint are aggregated into count, errors, total, max and latency buckets")
    void record_sameEndpoint_aggregatesMetrics() {
        recorder.record("GET", "/api/orders/{id}", 200, false, 20 * MS);
        recorder.record("GET", "/api/orders/{id}", 404, false, 80 * MS);
        recorder.record("GET", "/api/orders/{id}", 500, false, 1200 * MS);

        List<ApiEndpointMetrics> drained = recorder.drain();

        assertThat(drained).hasSize(1);
        ApiEndpointMetrics metrics = drained.getFirst();
        assertThat(metrics.httpMethod()).isEqualTo("GET");
        assertThat(metrics.uriTemplate()).isEqualTo("/api/orders/{id}");
        assertThat(metrics.requestCount()).isEqualTo(3);
        assertThat(metrics.clientErrorCount()).isEqualTo(1);
        assertThat(metrics.serverErrorCount()).isEqualTo(1);
        assertThat(metrics.totalTimeMs()).isEqualTo(1300.0);
        assertThat(metrics.maxTimeMs()).isEqualTo(1200.0);

        long[] buckets = metrics.latencyBucketCounts();
        assertThat(buckets).hasSize(LatencyBuckets.SLOT_COUNT);
        assertThat(buckets[0]).isEqualTo(1); // <= 25 ms
        assertThat(buckets[2]).isEqualTo(1); // (50, 100] ms
        assertThat(buckets[8]).isEqualTo(1); // (1000, 1500] ms
        assertThat(sum(buckets)).isEqualTo(3);
    }

    @Test
    @DisplayName("UTCID02 [N]: AC04 the same route with different methods is reported as separate endpoints, sorted by uri then method")
    void record_differentMethods_separateEntriesSorted() {
        recorder.record("POST", "/api/cart/items", 201, false, 10 * MS);
        recorder.record("GET", "/api/cart", 200, false, 10 * MS);
        recorder.record("DELETE", "/api/cart/items", 204, false, 10 * MS);

        List<ApiEndpointMetrics> drained = recorder.drain();

        assertThat(drained)
                .extracting(ApiEndpointMetrics::httpMethod, ApiEndpointMetrics::uriTemplate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("GET", "/api/cart"),
                        org.assertj.core.groups.Tuple.tuple("DELETE", "/api/cart/items"),
                        org.assertj.core.groups.Tuple.tuple("POST", "/api/cart/items"));
    }

    @Test
    @DisplayName("UTCID03 [N]: AC06 drain starts a new interval, so a second drain only returns newer requests")
    void drain_twice_secondReturnsOnlyNewRequests() {
        recorder.record("GET", "/api/cart", 200, false, 10 * MS);
        assertThat(recorder.drain()).hasSize(1);

        assertThat(recorder.drain()).isEmpty();

        recorder.record("GET", "/api/cart", 200, false, 10 * MS);
        assertThat(recorder.drain()).singleElement()
                .extracting(ApiEndpointMetrics::requestCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("UTCID04 [A]: AC04 an unhandled exception with a non-error status counts as a server error")
    void record_failedWithSuccessStatus_countsAsServerError() {
        recorder.record("POST", "/api/orders/checkout", 200, true, 10 * MS);

        ApiEndpointMetrics metrics = recorder.drain().getFirst();

        assertThat(metrics.serverErrorCount()).isEqualTo(1);
        assertThat(metrics.clientErrorCount()).isZero();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(value = {
            "GET, /actuator/health",
            "GET, /actuator",
            "GET, /swagger-ui/**",
            "GET, /v3/api-docs",
            "GET, /v3/api-docs/swagger-config",
            "GET, /ws",
            "OPTIONS, /api/cart",
            "GET, NULL",
            "GET, ''"
    }, nullValues = "NULL")
    @DisplayName("UTCID05 [B]: AC03 infrastructure endpoints, CORS preflight and unmatched routes are not recorded")
    void record_excludedRequest_isIgnored(String method, String uri) {
        recorder.record(method, uri, 200, false, 10 * MS);

        assertThat(recorder.drain()).isEmpty();
    }

    @Test
    @DisplayName("UTCID06 [B]: AC03 routes that only share a prefix with an excluded path are still recorded")
    void record_prefixLookalike_isRecorded() {
        recorder.record("GET", "/actuatorx/status", 200, false, 10 * MS);
        recorder.record("GET", "/wsx", 200, false, 10 * MS);

        assertThat(recorder.drain()).hasSize(2);
    }

    @ParameterizedTest(name = "{0} ms -> slot {1}")
    @CsvSource({"0, 0", "25, 0", "26, 1", "100, 2", "101, 3", "10000, 12", "10001, 13"})
    @DisplayName("UTCID07 [B]: BR-GEN-26 durations on a bucket boundary fall into that bucket; above 10 s goes to the overflow slot")
    void slotFor_boundaries(long durationMs, int expectedSlot) {
        assertThat(LatencyBuckets.slotFor(durationMs * MS)).isEqualTo(expectedSlot);
    }

    @Test
    @DisplayName("UTCID08 [N]: AC09 concurrent requests are all counted")
    void record_concurrently_countsEveryRequest() throws InterruptedException {
        int threads = 8;
        int perThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    recorder.record("GET", "/api/store/products", 200, false, 5 * MS);
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        ApiEndpointMetrics metrics = recorder.drain().getFirst();

        assertThat(metrics.requestCount()).isEqualTo((long) threads * perThread);
        assertThat(sum(metrics.latencyBucketCounts())).isEqualTo((long) threads * perThread);
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total;
    }
}
