package com.lumintra.backend.monitoring.infrastructure.persistence.repository;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.model.MinuteMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Aggregating reads over {@code api_metric_bucket} for metrics history (MON-03). Summing happens in
 * the database, including each latency slot, so at most one row per minute (or per endpoint)
 * comes back however many instances and endpoints were stored.
 */
@Repository
@Transactional(readOnly = true)
public class ApiMetricBucketQueryRepository {

    private static final String AGGREGATES = """
            SUM(request_count), SUM(client_error_count), SUM(server_error_count),
            SUM(total_time_ms), MAX(max_time_ms), %s
            """.formatted(IntStream.rangeClosed(1, LatencyBuckets.SLOT_COUNT)
            .mapToObj(slot -> "SUM(latency_buckets[" + slot + "])")
            .collect(Collectors.joining(", ")));

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Totals per stored minute in {@code [from, to)}, oldest first, optionally limited to one HTTP
     * method and/or route template.
     */
    public List<MinuteMetrics> findMinuteTotals(Instant from, Instant to, String httpMethod, String uriTemplate) {
        String sql = "SELECT bucket_start, " + AGGREGATES
                + " FROM api_metric_bucket WHERE bucket_start >= :from AND bucket_start < :to"
                + filters(httpMethod, uriTemplate)
                + " GROUP BY bucket_start ORDER BY bucket_start";
        Query query = bind(entityManager.createNativeQuery(sql), from, to, httpMethod, uriTemplate);
        return rows(query).stream()
                .map(row -> new MinuteMetrics(toInstant(row[0]), toMetrics(null, null, row, 1)))
                .toList();
    }

    /** Totals per endpoint over {@code [from, to)}, optionally limited to one HTTP method. */
    public List<ApiEndpointMetrics> findEndpointTotals(Instant from, Instant to, String httpMethod) {
        String sql = "SELECT http_method, uri_template, " + AGGREGATES
                + " FROM api_metric_bucket WHERE bucket_start >= :from AND bucket_start < :to"
                + filters(httpMethod, null)
                + " GROUP BY http_method, uri_template";
        Query query = bind(entityManager.createNativeQuery(sql), from, to, httpMethod, null);
        return rows(query).stream()
                .map(row -> toMetrics((String) row[0], (String) row[1], row, 2))
                .toList();
    }

    private static String filters(String httpMethod, String uriTemplate) {
        return (httpMethod != null ? " AND http_method = :method" : "")
                + (uriTemplate != null ? " AND uri_template = :uri" : "");
    }

    private static Query bind(Query query, Instant from, Instant to, String httpMethod, String uriTemplate) {
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (httpMethod != null) {
            query.setParameter("method", httpMethod);
        }
        if (uriTemplate != null) {
            query.setParameter("uri", uriTemplate);
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query query) {
        return query.getResultList();
    }

    private static ApiEndpointMetrics toMetrics(String httpMethod, String uriTemplate, Object[] row, int offset) {
        long[] slots = new long[LatencyBuckets.SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = toLong(row[offset + 5 + i]);
        }
        return new ApiEndpointMetrics(
                httpMethod,
                uriTemplate,
                toLong(row[offset]),
                toLong(row[offset + 1]),
                toLong(row[offset + 2]),
                toDouble(row[offset + 3]),
                toDouble(row[offset + 4]),
                slots);
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    /** JDBC drivers and dialects differ in how they return {@code timestamptz}; normalise to Instant. */
    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case ZonedDateTime zonedDateTime -> zonedDateTime.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            case LocalDateTime localDateTime -> localDateTime.toInstant(ZoneOffset.UTC);
            default -> throw new IllegalStateException("Unexpected bucket_start type: " + value.getClass());
        };
    }
}
