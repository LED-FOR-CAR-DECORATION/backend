package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.api.request.LiveSystemMetricsRequest;
import com.lumintra.backend.monitoring.api.response.LiveSystemMetricsResponse;
import com.lumintra.backend.monitoring.application.GetLiveSystemMetricsUseCase;
import com.lumintra.backend.monitoring.application.MetricSnapshotBuffer;
import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.model.MetricSnapshot;
import com.lumintra.backend.monitoring.domain.model.SystemComponentsStatus;
import com.lumintra.backend.monitoring.domain.service.SystemComponentsProbe;
import com.lumintra.backend.monitoring.infrastructure.metrics.MonitoringInstanceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetLiveSystemMetricsUseCaseTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-26T15:00:00Z");
    /** Latest snapshot covers 15:59; now is a few seconds after it was taken. */
    private static final Instant NOW = Instant.parse("2026-11-26T16:00:05Z");

    @Mock
    private SystemComponentsProbe componentsProbe;

    private MetricSnapshotBuffer buffer;
    private GetLiveSystemMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        buffer = new MetricSnapshotBuffer();
        useCase = new GetLiveSystemMetricsUseCase(
                buffer, new MonitoringInstanceId("api-1"), componentsProbe, STARTED_AT);
        lenient().when(componentsProbe.probe()).thenReturn(healthyComponents());
    }

    @Test
    @DisplayName("UTCID01 [N]: AC03 the window covers the latest N completed minutes, ending at the latest snapshot")
    void window_lastFiveMinutes_onlyThoseSnapshotsCounted() {
        for (int minute = 0; minute < 60; minute++) {
            addSnapshot(minute, endpoint("GET", "/api/cart", 1, 0, 0, 10.0, 10.0, 0));
        }

        LiveSystemMetricsResponse response = useCase.executeAt(request("5m", null, null), NOW);

        assertThat(response.window().from()).isEqualTo(OffsetDateTime.parse("2026-11-26T22:55:00+07:00"));
        assertThat(response.window().to()).isEqualTo(OffsetDateTime.parse("2026-11-26T23:00:00+07:00"));
        assertThat(response.overview().totalRequests()).isEqualTo(5);
        assertThat(response.overview().requestsPerMinute()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("UTCID02 [B]: AC03 when the instance has run for less than the window, 'from' is the earliest snapshot")
    void window_shorterUptime_fromIsEarliestSnapshot() {
        addSnapshot(57, endpoint("GET", "/api/cart", 6, 0, 0, 60.0, 10.0, 0));
        addSnapshot(58);
        addSnapshot(59, endpoint("GET", "/api/cart", 3, 0, 0, 30.0, 10.0, 0));

        LiveSystemMetricsResponse response = useCase.executeAt(request("60m", null, null), NOW);

        assertThat(response.window().from()).isEqualTo(OffsetDateTime.parse("2026-11-26T22:57:00+07:00"));
        assertThat(response.window().to()).isEqualTo(OffsetDateTime.parse("2026-11-26T23:00:00+07:00"));
        assertThat(response.overview().totalRequests()).isEqualTo(9);
        assertThat(response.overview().requestsPerMinute()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("UTCID03 [B]: AC03 with no snapshots yet, totals are zero, latencies null and the window is empty")
    void noSnapshots_returnsEmptyFigures() {
        LiveSystemMetricsResponse response = useCase.executeAt(request(null, null, null), NOW);

        assertThat(response.window().from()).isEqualTo(response.window().to());
        assertThat(response.window().to()).isEqualTo(OffsetDateTime.parse("2026-11-26T23:00:00+07:00"));
        LiveSystemMetricsResponse.Overview overview = response.overview();
        assertThat(overview.totalRequests()).isZero();
        assertThat(overview.requestsPerMinute()).isZero();
        assertThat(overview.avgLatencyMs()).isNull();
        assertThat(overview.p95LatencyMs()).isNull();
        assertThat(overview.maxLatencyMs()).isNull();
        assertThat(overview.errorRate()).isNull();
        assertThat(response.endpoints()).isEmpty();
    }

    @Test
    @DisplayName("UTCID04 [N]: AC04 generatedAt, instance id, start time and uptime are returned in Asia/Ho_Chi_Minh")
    void instanceInfo_returned() {
        LiveSystemMetricsResponse response = useCase.executeAt(request(null, null, null), NOW);

        assertThat(response.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-26T23:00:05+07:00"));
        assertThat(response.generatedAt().getOffset().getId()).isEqualTo("+07:00");
        assertThat(response.instance().instanceId()).isEqualTo("api-1");
        assertThat(response.instance().startedAt()).isEqualTo(OffsetDateTime.parse("2026-11-26T22:00:00+07:00"));
        assertThat(response.instance().uptimeSeconds()).isEqualTo(3605);
    }

    @Test
    @DisplayName("UTCID05 [N]: AC05 overview adds all endpoints; error rate counts only 5xx")
    void overview_aggregatesAllEndpoints() {
        addSnapshot(58,
                endpoint("GET", "/api/orders", 8, 2, 1, 800.0, 300.0, 3),
                endpoint("POST", "/api/cart/items", 2, 0, 0, 1200.0, 1100.0, 8));
        addSnapshot(59, endpoint("GET", "/api/orders", 10, 0, 1, 500.0, 90.0, 2));

        LiveSystemMetricsResponse.Overview overview = useCase.executeAt(request("15m", null, null), NOW).overview();

        assertThat(overview.totalRequests()).isEqualTo(20);
        assertThat(overview.clientErrorCount()).isEqualTo(2);
        assertThat(overview.serverErrorCount()).isEqualTo(2);
        assertThat(overview.errorRate()).isEqualTo(0.1);
        assertThat(overview.avgLatencyMs()).isEqualTo(125.0);
        assertThat(overview.maxLatencyMs()).isEqualTo(1100.0);
        // 10 requests in slot 2 (<=100), 8 in slot 3 (<=200), 2 in slot 8 (<=1500, capped at max 1100)
        assertThat(overview.p50LatencyMs()).isEqualTo(100.0);
        assertThat(overview.p95LatencyMs()).isEqualTo(1100.0);
        assertThat(overview.p99LatencyMs()).isEqualTo(1100.0);
    }

    @Test
    @DisplayName("UTCID06 [N]: AC06 the same endpoint across minutes is merged into one entry")
    void endpoints_mergedAcrossMinutes() {
        addSnapshot(58, endpoint("GET", "/api/orders", 4, 1, 0, 400.0, 150.0, 3));
        addSnapshot(59, endpoint("GET", "/api/orders", 6, 0, 3, 600.0, 180.0, 3));

        List<LiveSystemMetricsResponse.EndpointItem> endpoints = useCase.executeAt(request(null, null, null), NOW).endpoints();

        assertThat(endpoints).singleElement().satisfies(item -> {
            assertThat(item.method()).isEqualTo("GET");
            assertThat(item.uri()).isEqualTo("/api/orders");
            assertThat(item.count()).isEqualTo(10);
            assertThat(item.avgLatencyMs()).isEqualTo(100.0);
            assertThat(item.p95LatencyMs()).isEqualTo(180.0);
            assertThat(item.maxLatencyMs()).isEqualTo(180.0);
            assertThat(item.clientErrors()).isEqualTo(1);
            assertThat(item.serverErrors()).isEqualTo(3);
            assertThat(item.errorRate()).isEqualTo(0.3);
        });
    }

    @Test
    @DisplayName("UTCID07 [N]: AC06 sort=count orders by request count, highest first")
    void sortByCount() {
        addSnapshot(59,
                endpoint("GET", "/api/a", 1, 0, 0, 900.0, 900.0, 7),
                endpoint("GET", "/api/b", 9, 0, 0, 90.0, 10.0, 0),
                endpoint("GET", "/api/c", 5, 0, 0, 50.0, 10.0, 0));

        assertThat(uris(useCase.executeAt(request(null, "count", null), NOW)))
                .containsExactly("/api/b", "/api/c", "/api/a");
    }

    @Test
    @DisplayName("UTCID08 [N]: AC06 the default sort is p95 latency, highest first")
    void defaultSortIsP95() {
        addSnapshot(59,
                endpoint("GET", "/api/fast", 9, 0, 0, 90.0, 10.0, 0),
                endpoint("GET", "/api/slow", 1, 0, 0, 900.0, 900.0, 7),
                endpoint("GET", "/api/medium", 5, 0, 0, 500.0, 150.0, 3));

        assertThat(uris(useCase.executeAt(request(null, null, null), NOW)))
                .containsExactly("/api/slow", "/api/medium", "/api/fast");
    }

    @Test
    @DisplayName("UTCID09 [N]: AC06 sort=avgMs and sort=errorRate order by those values; ties go to the busier endpoint")
    void sortByAvgAndErrorRate() {
        addSnapshot(59,
                endpoint("GET", "/api/a", 10, 0, 5, 1000.0, 200.0, 3),
                endpoint("GET", "/api/b", 2, 0, 1, 1000.0, 600.0, 6),
                endpoint("GET", "/api/c", 4, 0, 0, 40.0, 10.0, 0));

        assertThat(uris(useCase.executeAt(request(null, "avgMs", null), NOW)))
                .containsExactly("/api/b", "/api/a", "/api/c");
        // a and b both have errorRate 0.5; a has more requests
        assertThat(uris(useCase.executeAt(request(null, "errorRate", null), NOW)))
                .containsExactly("/api/a", "/api/b", "/api/c");
    }

    @Test
    @DisplayName("UTCID10 [B]: AC06 the endpoint list is capped at limit")
    void limitCapsEndpoints() {
        addSnapshot(59,
                endpoint("GET", "/api/a", 3, 0, 0, 30.0, 10.0, 0),
                endpoint("GET", "/api/b", 2, 0, 0, 20.0, 10.0, 0),
                endpoint("GET", "/api/c", 1, 0, 0, 10.0, 10.0, 0));

        assertThat(uris(useCase.executeAt(request(null, "count", 2), NOW))).containsExactly("/api/a", "/api/b");
    }

    @Test
    @DisplayName("UTCID11 [N]: AC08 latencies are rounded to 1 decimal place and the error rate to 4")
    void rounding() {
        addSnapshot(59, endpoint("GET", "/api/orders", 3, 0, 1, 100.0, 50.06, 1));

        LiveSystemMetricsResponse response = useCase.executeAt(request(null, null, null), NOW);

        assertThat(response.overview().avgLatencyMs()).isEqualTo(33.3);
        assertThat(response.overview().maxLatencyMs()).isEqualTo(50.1);
        assertThat(response.overview().errorRate()).isEqualTo(0.3333);
    }

    @Test
    @DisplayName("UTCID12 [N]: AC07 component health from the probe is returned, with JVM figures rounded")
    void components_mapped() {
        when(componentsProbe.probe()).thenReturn(new SystemComponentsStatus(
                new SystemComponentsStatus.Database("DOWN", 0, 0, 2),
                new SystemComponentsStatus.Redis("UP"),
                new SystemComponentsStatus.Jvm(412.456, null, 58, 0.123456)));

        LiveSystemMetricsResponse.Components components = useCase.executeAt(request(null, null, null), NOW).components();

        assertThat(components.db().status()).isEqualTo("DOWN");
        assertThat(components.db().pendingConnections()).isEqualTo(2);
        assertThat(components.redis().status()).isEqualTo("UP");
        assertThat(components.jvm().heapUsedMb()).isEqualTo(412.5);
        assertThat(components.jvm().heapMaxMb()).isNull();
        assertThat(components.jvm().threadsLive()).isEqualTo(58);
        assertThat(components.jvm().cpuUsage()).isEqualTo(0.1235);
    }

    @Test
    @DisplayName("UTCID13 [B]: AC03 a request made before the scheduler records the current minute uses the latest snapshot as the window end")
    void windowEnd_anchoredOnLatestSnapshot() {
        addSnapshot(58, endpoint("GET", "/api/cart", 2, 0, 0, 20.0, 10.0, 0));

        // two minutes after the latest snapshot's minute, e.g. the 16:00 run has not happened yet
        LiveSystemMetricsResponse response = useCase.executeAt(request("5m", null, null), Instant.parse("2026-11-26T16:00:00.200Z"));

        assertThat(response.window().to()).isEqualTo(OffsetDateTime.parse("2026-11-26T22:59:00+07:00"));
        assertThat(response.overview().requestsPerMinute()).isEqualTo(2.0);
    }

    private void addSnapshot(int minuteOfHour, ApiEndpointMetrics... endpoints) {
        Instant bucketStart = Instant.parse("2026-11-26T15:00:00Z").plusSeconds(60L * minuteOfHour);
        buffer.add(new MetricSnapshot(bucketStart, bucketStart.plusSeconds(60), List.of(endpoints)));
    }

    private static ApiEndpointMetrics endpoint(String method, String uri, long count, long client, long server,
                                               double totalMs, double maxMs, int slot) {
        long[] slots = new long[LatencyBuckets.SLOT_COUNT];
        slots[slot] = count;
        return new ApiEndpointMetrics(method, uri, count, client, server, totalMs, maxMs, slots);
    }

    private static LiveSystemMetricsRequest request(String window, String sort, Integer limit) {
        return new LiveSystemMetricsRequest(window, sort, limit);
    }

    private static List<String> uris(LiveSystemMetricsResponse response) {
        return response.endpoints().stream().map(LiveSystemMetricsResponse.EndpointItem::uri).toList();
    }

    private static SystemComponentsStatus healthyComponents() {
        return new SystemComponentsStatus(
                new SystemComponentsStatus.Database("UP", 1, 2, 0),
                new SystemComponentsStatus.Redis("UP"),
                new SystemComponentsStatus.Jvm(400.0, 2048.0, 50, 0.1));
    }
}
