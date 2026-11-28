package com.lumintra.backend.monitoring.application;

import com.lumintra.backend.monitoring.api.request.MetricsHistoryRequest;
import com.lumintra.backend.monitoring.api.response.MetricsHistoryResponse;
import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.MetricsInterval;
import com.lumintra.backend.monitoring.domain.model.MinuteMetrics;
import com.lumintra.backend.monitoring.domain.service.ApiMetricsAccumulator;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketQueryRepository;
import com.lumintra.backend.shared.exception.monitoring.InvalidMetricsRangeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lumintra.backend.monitoring.application.MetricsFormat.DISPLAY_ZONE;
import static com.lumintra.backend.monitoring.application.MetricsFormat.round;
import static com.lumintra.backend.monitoring.application.MetricsFormat.roundOrZero;
import static com.lumintra.backend.monitoring.application.MetricsFormat.toDisplayTime;

/**
 * MON-03: API performance trends over up to 30 days, built from the stored per-minute buckets of
 * all instances. The database adds rows up per minute; this class groups the minutes into periods.
 */
@Service
@RequiredArgsConstructor
public class GetMetricsHistoryUseCase {

    static final Duration RETENTION = Duration.ofDays(30);
    static final int MAX_POINTS = 1_500;
    static final int TOP_ENDPOINTS = 10;

    private final ApiMetricBucketQueryRepository queryRepository;

    public MetricsHistoryResponse execute(MetricsHistoryRequest request) {
        return executeAt(request, Instant.now());
    }

    public MetricsHistoryResponse executeAt(MetricsHistoryRequest request, Instant now) {
        Instant evaluatedAt = now != null ? now : Instant.now();
        MetricsInterval interval = MetricsInterval.fromCode(request.interval())
                .orElseThrow(() -> new InvalidMetricsRangeException("interval must be one of 1m, 5m, 1h, 1d"));

        Instant from = request.from().toInstant();
        Instant to = request.to().toInstant();
        if (!from.isBefore(to)) {
            throw new InvalidMetricsRangeException("from must be before to");
        }
        if (from.isBefore(evaluatedAt.minus(RETENTION))) {
            throw new InvalidMetricsRangeException("from must be within the last 30 days");
        }
        // The current minute is still being recorded, so the range stops at its start (BR-GEN-25).
        Instant rangeEnd = min(to, evaluatedAt.truncatedTo(ChronoUnit.MINUTES));
        if (!from.isBefore(rangeEnd)) {
            throw new InvalidMetricsRangeException("from must be before the current minute");
        }
        Instant rangeStart = interval.floor(from, DISPLAY_ZONE);
        List<Instant> periodStarts = periodStarts(interval, rangeStart, rangeEnd);

        Map<Instant, ApiMetricsAccumulator> perPeriod = new LinkedHashMap<>();
        periodStarts.forEach(start -> perPeriod.put(start, new ApiMetricsAccumulator()));
        ApiMetricsAccumulator overall = new ApiMetricsAccumulator();
        for (MinuteMetrics minute : queryRepository.findMinuteTotals(rangeStart, rangeEnd, request.method(), request.uri())) {
            overall.add(minute.metrics());
            ApiMetricsAccumulator period = perPeriod.get(interval.floor(minute.bucketStart(), DISPLAY_ZONE));
            if (period != null) {
                period.add(minute.metrics());
            }
        }

        List<MetricsHistoryResponse.Point> points = new ArrayList<>(periodStarts.size());
        for (Instant start : periodStarts) {
            Instant end = min(interval.next(start, DISPLAY_ZONE), rangeEnd);
            points.add(toPoint(start, perPeriod.get(start), minutesBetween(start, end)));
        }

        List<MetricsHistoryResponse.EndpointItem> topEndpoints = request.uri() != null
                ? List.of()
                : topEndpoints(queryRepository.findEndpointTotals(rangeStart, rangeEnd, request.method()));

        return new MetricsHistoryResponse(
                toDisplayTime(rangeStart),
                toDisplayTime(rangeEnd),
                interval.code(),
                new MetricsHistoryResponse.Filter(request.method(), request.uri()),
                toStats(overall, minutesBetween(rangeStart, rangeEnd)),
                points,
                topEndpoints);
    }

    private static List<Instant> periodStarts(MetricsInterval interval, Instant rangeStart, Instant rangeEnd) {
        List<Instant> starts = new ArrayList<>();
        for (Instant start = rangeStart; start.isBefore(rangeEnd); start = interval.next(start, DISPLAY_ZONE)) {
            if (starts.size() == MAX_POINTS) {
                throw new InvalidMetricsRangeException(
                        "The range would produce more than " + MAX_POINTS + " points; use a larger interval or a shorter range");
            }
            starts.add(start);
        }
        return starts;
    }

    private static List<MetricsHistoryResponse.EndpointItem> topEndpoints(List<ApiEndpointMetrics> endpointTotals) {
        record Ranked(ApiEndpointMetrics totals, ApiMetricsAccumulator acc) {
        }
        return endpointTotals.stream()
                .map(totals -> new Ranked(totals, new ApiMetricsAccumulator().add(totals)))
                .sorted(Comparator
                        .comparing((Ranked ranked) -> ranked.acc().percentileLatencyMs(0.95),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ranked -> ranked.acc().requestCount(), Comparator.reverseOrder())
                        .thenComparing(ranked -> ranked.totals().uriTemplate())
                        .thenComparing(ranked -> ranked.totals().httpMethod()))
                .limit(TOP_ENDPOINTS)
                .map(ranked -> new MetricsHistoryResponse.EndpointItem(
                        ranked.totals().httpMethod(),
                        ranked.totals().uriTemplate(),
                        ranked.acc().requestCount(),
                        round(ranked.acc().avgLatencyMs(), 1),
                        round(ranked.acc().percentileLatencyMs(0.95), 1),
                        round(ranked.acc().percentileLatencyMs(0.99), 1),
                        round(ranked.acc().maxLatencyMs(), 1),
                        ranked.acc().clientErrorCount(),
                        ranked.acc().serverErrorCount(),
                        round(ranked.acc().errorRate(), 4)))
                .toList();
    }

    private static MetricsHistoryResponse.Point toPoint(Instant periodStart, ApiMetricsAccumulator acc, long minutes) {
        MetricsHistoryResponse.Stats stats = toStats(acc, minutes);
        return new MetricsHistoryResponse.Point(
                toDisplayTime(periodStart),
                stats.requestCount(),
                stats.requestsPerMinute(),
                stats.avgLatencyMs(),
                stats.p50LatencyMs(),
                stats.p95LatencyMs(),
                stats.p99LatencyMs(),
                stats.maxLatencyMs(),
                stats.clientErrors(),
                stats.serverErrors(),
                stats.errorRate());
    }

    private static MetricsHistoryResponse.Stats toStats(ApiMetricsAccumulator acc, long minutes) {
        double requestsPerMinute = minutes > 0 ? (double) acc.requestCount() / minutes : 0.0;
        return new MetricsHistoryResponse.Stats(
                acc.requestCount(),
                roundOrZero(requestsPerMinute, 1),
                round(acc.avgLatencyMs(), 1),
                round(acc.percentileLatencyMs(0.50), 1),
                round(acc.percentileLatencyMs(0.95), 1),
                round(acc.percentileLatencyMs(0.99), 1),
                round(acc.maxLatencyMs(), 1),
                acc.clientErrorCount(),
                acc.serverErrorCount(),
                round(acc.errorRate(), 4));
    }

    private static long minutesBetween(Instant start, Instant end) {
        return Duration.between(start, end).toMinutes();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
