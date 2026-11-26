package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.service.ApiMetricsAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMetricsAccumulatorTest {

    @Test
    @DisplayName("UTCID01 [N]: BR-GEN-26 counts, errors and total time are summed; max is the largest; avg = total / count")
    void add_twoMinutes_sumsAndDerives() {
        ApiMetricsAccumulator acc = new ApiMetricsAccumulator()
                .add(metrics(4, 1, 0, 400.0, 150.0, slots(0, 4)))
                .add(metrics(6, 0, 3, 1600.0, 900.0, slots(7, 6)));

        assertThat(acc.requestCount()).isEqualTo(10);
        assertThat(acc.clientErrorCount()).isEqualTo(1);
        assertThat(acc.serverErrorCount()).isEqualTo(3);
        assertThat(acc.avgLatencyMs()).isEqualTo(200.0);
        assertThat(acc.maxLatencyMs()).isEqualTo(900.0);
        assertThat(acc.errorRate()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("UTCID02 [N]: BR-GEN-26 percentile is the upper bound of the slot holding its rank")
    void percentile_usesSlotUpperBound() {
        // 90 requests <= 25 ms, 9 in (500, 750], 1 in (1500, 2000]
        long[] counts = new long[LatencyBuckets.SLOT_COUNT];
        counts[0] = 90;
        counts[6] = 9;
        counts[9] = 1;
        ApiMetricsAccumulator acc = new ApiMetricsAccumulator().add(metrics(100, 0, 0, 9000.0, 1800.0, counts));

        assertThat(acc.percentileLatencyMs(0.50)).isEqualTo(25.0);
        assertThat(acc.percentileLatencyMs(0.95)).isEqualTo(750.0);
        assertThat(acc.percentileLatencyMs(0.99)).isEqualTo(750.0);
        // the top slot's bound (2000) is above the observed max, so the max is reported
        assertThat(acc.percentileLatencyMs(1.0)).isEqualTo(1800.0);
    }

    @Test
    @DisplayName("UTCID03 [B]: BR-GEN-26 a percentile in the overflow slot (> 10 s) reports the observed max")
    void percentile_overflowSlot_reportsMax() {
        ApiMetricsAccumulator acc = new ApiMetricsAccumulator()
                .add(metrics(1, 0, 1, 12_345.6, 12_345.6, slots(LatencyBuckets.SLOT_COUNT - 1, 1)));

        assertThat(acc.percentileLatencyMs(0.95)).isEqualTo(12_345.6);
    }

    @Test
    @DisplayName("UTCID04 [B]: AC05 without requests, latency figures and error rate are null")
    void empty_returnsNulls() {
        ApiMetricsAccumulator acc = new ApiMetricsAccumulator();

        assertThat(acc.requestCount()).isZero();
        assertThat(acc.avgLatencyMs()).isNull();
        assertThat(acc.maxLatencyMs()).isNull();
        assertThat(acc.percentileLatencyMs(0.95)).isNull();
        assertThat(acc.errorRate()).isNull();
    }

    private static long[] slots(int slot, long count) {
        long[] counts = new long[LatencyBuckets.SLOT_COUNT];
        counts[slot] = count;
        return counts;
    }

    private static ApiEndpointMetrics metrics(long count, long client, long server, double totalMs, double maxMs, long[] slots) {
        return new ApiEndpointMetrics("GET", "/api/orders", count, client, server, totalMs, maxMs, slots);
    }
}
