package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.application.MetricSnapshotBuffer;
import com.lumintra.backend.monitoring.application.SystemMetricsSnapshotScheduler;
import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.model.MetricSnapshot;
import com.lumintra.backend.monitoring.infrastructure.metrics.ApiRequestMetricsRecorder;
import com.lumintra.backend.monitoring.infrastructure.metrics.MonitoringInstanceId;
import com.lumintra.backend.monitoring.infrastructure.persistence.entity.ApiMetricBucketEntity;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMetricsSnapshotSchedulerTest {

    @Mock
    private ApiRequestMetricsRecorder recorder;
    @Mock
    private ApiMetricBucketRepository bucketRepository;

    private MetricSnapshotBuffer buffer;
    private SystemMetricsSnapshotScheduler scheduler;

    @BeforeEach
    void setUp() {
        buffer = new MetricSnapshotBuffer();
        scheduler = new SystemMetricsSnapshotScheduler(
                recorder, buffer, bucketRepository, new MonitoringInstanceId("test-instance"));
    }

    @Test
    @DisplayName("UTCID01 [N]: AC07 each endpoint becomes one stored row labelled with the minute that just ended")
    void captureSnapshotAt_withTraffic_persistsOneRowPerEndpoint() {
        ApiEndpointMetrics orders = metrics("GET", "/api/orders", 10);
        ApiEndpointMetrics cart = metrics("POST", "/api/cart/items", 3);
        when(recorder.drain()).thenReturn(List.of(orders, cart));

        MetricSnapshot snapshot = scheduler.captureSnapshotAt(Instant.parse("2026-11-26T12:01:00.004Z"));

        assertThat(snapshot.bucketStart()).isEqualTo(Instant.parse("2026-11-26T12:00:00Z"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiMetricBucketEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ApiMetricBucketEntity::getBucketStart, ApiMetricBucketEntity::getInstanceId,
                        ApiMetricBucketEntity::getHttpMethod, ApiMetricBucketEntity::getUriTemplate,
                        ApiMetricBucketEntity::getRequestCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-11-26T12:00:00Z"), "test-instance",
                                "GET", "/api/orders", 10L),
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-11-26T12:00:00Z"), "test-instance",
                                "POST", "/api/cart/items", 3L));
        assertThat(captor.getValue().getFirst().getLatencyBuckets()).hasSize(LatencyBuckets.SLOT_COUNT);
    }

    @Test
    @DisplayName("UTCID02 [N]: AC06 every snapshot, including one with traffic, is added to the in-memory buffer")
    void captureSnapshotAt_addsSnapshotToBuffer() {
        when(recorder.drain()).thenReturn(List.of(metrics("GET", "/api/orders", 1)));

        MetricSnapshot snapshot = scheduler.captureSnapshotAt(Instant.parse("2026-11-26T12:01:00Z"));

        assertThat(buffer.getAll()).containsExactly(snapshot);
    }

    @Test
    @DisplayName("UTCID03 [B]: AC07 a minute without traffic is kept in memory but stores no rows")
    void captureSnapshotAt_noTraffic_skipsPersistence() {
        when(recorder.drain()).thenReturn(List.of());

        MetricSnapshot snapshot = scheduler.captureSnapshotAt(Instant.parse("2026-11-26T12:01:00Z"));

        assertThat(snapshot.endpoints()).isEmpty();
        assertThat(buffer.getAll()).containsExactly(snapshot);
        verify(bucketRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("UTCID04 [A]: AC08 a database failure is logged, does not throw, and the snapshot stays in memory")
    void captureSnapshotAt_saveFails_keepsSnapshotInMemory() {
        when(recorder.drain()).thenReturn(List.of(metrics("GET", "/api/orders", 1)));
        when(bucketRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> scheduler.captureSnapshotAt(Instant.parse("2026-11-26T12:01:00Z")))
                .doesNotThrowAnyException();

        assertThat(buffer.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("UTCID05 [B]: AC06 the buffer keeps only the latest 60 snapshots")
    void buffer_overCapacity_dropsOldest() {
        when(recorder.drain()).thenReturn(List.of());
        Instant start = Instant.parse("2026-11-26T12:00:00Z");

        for (int minute = 1; minute <= 61; minute++) {
            scheduler.captureSnapshotAt(start.plusSeconds(60L * minute));
        }

        List<MetricSnapshot> snapshots = buffer.getAll();
        assertThat(snapshots).hasSize(60);
        assertThat(snapshots.getFirst().bucketStart()).isEqualTo(start.plusSeconds(60));
        assertThat(snapshots.getLast().bucketStart()).isEqualTo(start.plusSeconds(60L * 60));
    }

    @Test
    @DisplayName("UTCID06 [N]: BR-GEN-25 a configured instance id is used, otherwise hostname-pid")
    void monitoringInstanceId_resolution() {
        assertThat(new MonitoringInstanceId("  api-1  ").value()).isEqualTo("api-1");
        assertThat(new MonitoringInstanceId("").value()).endsWith("-" + ProcessHandle.current().pid());
        assertThat(new MonitoringInstanceId("x".repeat(150)).value()).hasSize(100);
    }

    private static ApiEndpointMetrics metrics(String method, String uri, long count) {
        long[] buckets = new long[LatencyBuckets.SLOT_COUNT];
        buckets[0] = count;
        return new ApiEndpointMetrics(method, uri, count, 0, 0, count * 10.0, 10.0, buckets);
    }
}
