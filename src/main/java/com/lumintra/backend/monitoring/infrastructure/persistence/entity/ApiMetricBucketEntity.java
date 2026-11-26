package com.lumintra.backend.monitoring.infrastructure.persistence.entity;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One minute of request metrics for one endpoint on one backend instance (BR-GEN-25).
 */
@Entity
@Table(
        name = "api_metric_bucket",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_api_metric_bucket_minute_endpoint",
                columnNames = {"bucket_start", "instance_id", "http_method", "uri_template"}
        ),
        indexes = @Index(name = "idx_api_metric_bucket_start", columnList = "bucket_start")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiMetricBucketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bucket_id")
    private Long bucketId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "uri_template", nullable = false, length = 255)
    private String uriTemplate;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "client_error_count", nullable = false)
    private long clientErrorCount;

    @Column(name = "server_error_count", nullable = false)
    private long serverErrorCount;

    @Column(name = "total_time_ms", nullable = false)
    private double totalTimeMs;

    @Column(name = "max_time_ms", nullable = false)
    private double maxTimeMs;

    /** Per-slot request counts, laid out as described in {@code LatencyBuckets}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "latency_buckets", nullable = false)
    private long[] latencyBuckets;

    public static ApiMetricBucketEntity of(Instant bucketStart, String instanceId, ApiEndpointMetrics metrics) {
        ApiMetricBucketEntity entity = new ApiMetricBucketEntity();
        entity.bucketStart = bucketStart;
        entity.instanceId = instanceId;
        entity.httpMethod = metrics.httpMethod();
        entity.uriTemplate = metrics.uriTemplate();
        entity.requestCount = metrics.requestCount();
        entity.clientErrorCount = metrics.clientErrorCount();
        entity.serverErrorCount = metrics.serverErrorCount();
        entity.totalTimeMs = metrics.totalTimeMs();
        entity.maxTimeMs = metrics.maxTimeMs();
        entity.latencyBuckets = metrics.latencyBucketCounts();
        return entity;
    }
}
