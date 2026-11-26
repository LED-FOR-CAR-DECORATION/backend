package com.lumintra.backend.monitoring.application;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.MetricSnapshot;
import com.lumintra.backend.monitoring.infrastructure.metrics.ApiRequestMetricsRecorder;
import com.lumintra.backend.monitoring.infrastructure.metrics.MonitoringInstanceId;
import com.lumintra.backend.monitoring.infrastructure.persistence.entity.ApiMetricBucketEntity;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * MON-01: at the start of every minute, closes the previous minute's request metrics, keeps them
 * in memory for the live view and stores one summary row per endpoint (BR-GEN-25).
 */
@Component
@RequiredArgsConstructor
public class SystemMetricsSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsSnapshotScheduler.class);

    private final ApiRequestMetricsRecorder recorder;
    private final MetricSnapshotBuffer buffer;
    private final ApiMetricBucketRepository bucketRepository;
    private final MonitoringInstanceId instanceId;

    @Scheduled(cron = "${app.monitoring.snapshot-cron:0 * * * * *}")
    public void captureSnapshot() {
        captureSnapshotAt(Instant.now());
    }

    /**
     * Drains the recorder and labels the result with the minute before {@code now}.
     * Runs scheduled at second 0, so that minute is the one that just ended.
     */
    public MetricSnapshot captureSnapshotAt(Instant now) {
        Instant capturedAt = now != null ? now : Instant.now();
        Instant bucketStart = capturedAt.truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES);

        MetricSnapshot snapshot = new MetricSnapshot(bucketStart, capturedAt, recorder.drain());
        buffer.add(snapshot);
        persist(snapshot);
        return snapshot;
    }

    private void persist(MetricSnapshot snapshot) {
        if (snapshot.endpoints().isEmpty()) {
            return;
        }
        List<ApiMetricBucketEntity> rows = snapshot.endpoints().stream()
                .map(metrics -> ApiMetricBucketEntity.of(snapshot.bucketStart(), instanceId.value(), metrics))
                .toList();
        try {
            bucketRepository.saveAll(rows);
            log.debug("Stored API metric buckets [bucketStart={}] [rows={}] [requests={}]",
                    snapshot.bucketStart(), rows.size(), totalRequests(snapshot));
        } catch (RuntimeException ex) {
            log.warn("Failed to store API metric buckets; snapshot kept in memory only [bucketStart={}] [rows={}]",
                    snapshot.bucketStart(), rows.size(), ex);
        }
    }

    private static long totalRequests(MetricSnapshot snapshot) {
        return snapshot.endpoints().stream().mapToLong(ApiEndpointMetrics::requestCount).sum();
    }
}
