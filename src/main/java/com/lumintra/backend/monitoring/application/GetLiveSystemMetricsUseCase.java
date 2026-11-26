package com.lumintra.backend.monitoring.application;

import com.lumintra.backend.monitoring.api.request.LiveSystemMetricsRequest;
import com.lumintra.backend.monitoring.api.response.LiveSystemMetricsResponse;
import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.MetricSnapshot;
import com.lumintra.backend.monitoring.domain.model.SystemComponentsStatus;
import com.lumintra.backend.monitoring.domain.service.ApiMetricsAccumulator;
import com.lumintra.backend.monitoring.domain.service.SystemComponentsProbe;
import com.lumintra.backend.monitoring.infrastructure.metrics.MonitoringInstanceId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MON-02: builds the live metrics view from the in-memory snapshots of MON-01 (no database queries)
 * plus the current component health.
 */
@Service
public class GetLiveSystemMetricsUseCase {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MetricSnapshotBuffer buffer;
    private final MonitoringInstanceId instanceId;
    private final SystemComponentsProbe componentsProbe;
    private final Instant instanceStartedAt;

    @Autowired
    public GetLiveSystemMetricsUseCase(
            MetricSnapshotBuffer buffer,
            MonitoringInstanceId instanceId,
            SystemComponentsProbe componentsProbe) {
        this(buffer, instanceId, componentsProbe,
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    public GetLiveSystemMetricsUseCase(
            MetricSnapshotBuffer buffer,
            MonitoringInstanceId instanceId,
            SystemComponentsProbe componentsProbe,
            Instant instanceStartedAt) {
        this.buffer = buffer;
        this.instanceId = instanceId;
        this.componentsProbe = componentsProbe;
        this.instanceStartedAt = instanceStartedAt;
    }

    public LiveSystemMetricsResponse execute(LiveSystemMetricsRequest request) {
        return executeAt(request, Instant.now());
    }

    public LiveSystemMetricsResponse executeAt(LiveSystemMetricsRequest request, Instant now) {
        Instant generatedAt = now != null ? now : Instant.now();
        List<MetricSnapshot> snapshots = buffer.getAll();

        // The window ends at the latest completed minute that has a snapshot, so a request made just
        // before the scheduler runs does not count an unrecorded minute as zero traffic.
        Instant windowEnd = snapshots.isEmpty()
                ? generatedAt.truncatedTo(ChronoUnit.MINUTES)
                : snapshots.getLast().bucketStart().plus(1, ChronoUnit.MINUTES);
        Instant requestedStart = windowEnd.minus(parseWindow(request.window()));
        List<MetricSnapshot> inWindow = snapshots.stream()
                .filter(snapshot -> !snapshot.bucketStart().isBefore(requestedStart))
                .toList();
        Instant windowStart = inWindow.isEmpty() ? windowEnd : inWindow.getFirst().bucketStart();

        ApiMetricsAccumulator overall = new ApiMetricsAccumulator();
        Map<String, EndpointTotals> perEndpoint = new LinkedHashMap<>();
        for (MetricSnapshot snapshot : inWindow) {
            for (ApiEndpointMetrics metrics : snapshot.endpoints()) {
                overall.add(metrics);
                perEndpoint.computeIfAbsent(metrics.httpMethod() + " " + metrics.uriTemplate(),
                                ignored -> new EndpointTotals(metrics.httpMethod(), metrics.uriTemplate()))
                        .accumulator().add(metrics);
            }
        }

        long minutes = Duration.between(windowStart, windowEnd).toMinutes();
        double requestsPerMinute = minutes > 0 ? (double) overall.requestCount() / minutes : 0.0;

        List<LiveSystemMetricsResponse.EndpointItem> endpoints = perEndpoint.values().stream()
                .sorted(comparatorFor(request.sort()))
                .limit(request.limit())
                .map(GetLiveSystemMetricsUseCase::toEndpointItem)
                .toList();

        return new LiveSystemMetricsResponse(
                toDisplayTime(generatedAt),
                new LiveSystemMetricsResponse.WindowInfo(toDisplayTime(windowStart), toDisplayTime(windowEnd)),
                new LiveSystemMetricsResponse.InstanceInfo(
                        instanceId.value(),
                        toDisplayTime(instanceStartedAt),
                        Math.max(0, Duration.between(instanceStartedAt, generatedAt).toSeconds())),
                new LiveSystemMetricsResponse.Overview(
                        overall.requestCount(),
                        round(requestsPerMinute, 1),
                        round(overall.avgLatencyMs(), 1),
                        round(overall.percentileLatencyMs(0.50), 1),
                        round(overall.percentileLatencyMs(0.95), 1),
                        round(overall.percentileLatencyMs(0.99), 1),
                        round(overall.maxLatencyMs(), 1),
                        overall.clientErrorCount(),
                        overall.serverErrorCount(),
                        round(overall.errorRate(), 4)),
                endpoints,
                toComponents(componentsProbe.probe())
        );
    }

    static Duration parseWindow(String window) {
        return switch (window) {
            case "5m" -> Duration.ofMinutes(5);
            case "60m" -> Duration.ofMinutes(60);
            default -> Duration.ofMinutes(15);
        };
    }

    /** Highest value first; ties go to the busier endpoint, then alphabetical uri and method. */
    private static Comparator<EndpointTotals> comparatorFor(String sort) {
        Function<EndpointTotals, Double> key = switch (sort) {
            case "count" -> totals -> (double) totals.accumulator().requestCount();
            case "avgMs" -> totals -> totals.accumulator().avgLatencyMs();
            case "errorRate" -> totals -> totals.accumulator().errorRate();
            default -> totals -> totals.accumulator().percentileLatencyMs(0.95);
        };
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(totals -> totals.accumulator().requestCount(), Comparator.reverseOrder())
                .thenComparing(EndpointTotals::uri)
                .thenComparing(EndpointTotals::method);
    }

    private static LiveSystemMetricsResponse.EndpointItem toEndpointItem(EndpointTotals totals) {
        ApiMetricsAccumulator acc = totals.accumulator();
        return new LiveSystemMetricsResponse.EndpointItem(
                totals.method(),
                totals.uri(),
                acc.requestCount(),
                round(acc.avgLatencyMs(), 1),
                round(acc.percentileLatencyMs(0.95), 1),
                round(acc.percentileLatencyMs(0.99), 1),
                round(acc.maxLatencyMs(), 1),
                acc.clientErrorCount(),
                acc.serverErrorCount(),
                round(acc.errorRate(), 4));
    }

    private static LiveSystemMetricsResponse.Components toComponents(SystemComponentsStatus status) {
        SystemComponentsStatus.Database db = status.db();
        SystemComponentsStatus.Jvm jvm = status.jvm();
        return new LiveSystemMetricsResponse.Components(
                new LiveSystemMetricsResponse.DbStatus(
                        db.status(), db.activeConnections(), db.idleConnections(), db.pendingConnections()),
                new LiveSystemMetricsResponse.RedisStatus(status.redis().status()),
                new LiveSystemMetricsResponse.JvmStatus(
                        round(jvm.heapUsedMb(), 1), round(jvm.heapMaxMb(), 1), jvm.threadsLive(), round(jvm.cpuUsage(), 4)));
    }

    private static OffsetDateTime toDisplayTime(Instant instant) {
        return instant.atZone(DISPLAY_ZONE).toOffsetDateTime();
    }

    private static Double round(Double value, int decimals) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round(double value, int decimals) {
        Double rounded = round(Double.valueOf(value), decimals);
        return rounded == null ? 0.0 : rounded;
    }

    private record EndpointTotals(String method, String uri, ApiMetricsAccumulator accumulator) {
        EndpointTotals(String method, String uri) {
            this(method, uri, new ApiMetricsAccumulator());
        }
    }
}
