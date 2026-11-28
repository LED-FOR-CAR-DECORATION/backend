package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.api.request.MetricsHistoryRequest;
import com.lumintra.backend.monitoring.api.response.MetricsHistoryResponse;
import com.lumintra.backend.monitoring.application.GetMetricsHistoryUseCase;
import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.model.MinuteMetrics;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketQueryRepository;
import com.lumintra.backend.shared.exception.monitoring.InvalidMetricsRangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMetricsHistoryUseCaseTest {

    /** 2026-11-28 10:30:20 in Vietnam. */
    private static final Instant NOW = Instant.parse("2026-11-28T03:30:20Z");

    @Mock
    private ApiMetricBucketQueryRepository queryRepository;

    private GetMetricsHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetMetricsHistoryUseCase(queryRepository);
    }

    @Test
    @DisplayName("UTCID01 [N]: AC03/AC04 minutes are grouped into 5m periods; empty periods get zero counts and null latencies")
    void fiveMinutePeriods_groupedAndZeroFilled() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T02:15:00Z");
        when(queryRepository.findMinuteTotals(from, to, null, null)).thenReturn(List.of(
                minute("2026-11-28T02:01:00Z", 4, 0, 1, 400.0, 150.0, 3),
                minute("2026-11-28T02:03:00Z", 6, 2, 0, 300.0, 90.0, 2),
                minute("2026-11-28T02:12:00Z", 5, 0, 0, 50.0, 10.0, 0)));
        when(queryRepository.findEndpointTotals(from, to, null)).thenReturn(List.of());

        MetricsHistoryResponse response = useCase.executeAt(request(from, to, "5m", null, null), NOW);

        assertThat(response.points()).extracting(point -> point.periodStart().toInstant())
                .containsExactly(from, from.plusSeconds(300), from.plusSeconds(600));
        MetricsHistoryResponse.Point first = response.points().get(0);
        assertThat(first.requestCount()).isEqualTo(10);
        assertThat(first.requestsPerMinute()).isEqualTo(2.0);
        assertThat(first.avgLatencyMs()).isEqualTo(70.0);
        assertThat(first.p50LatencyMs()).isEqualTo(100.0);
        assertThat(first.p95LatencyMs()).isEqualTo(150.0);
        assertThat(first.maxLatencyMs()).isEqualTo(150.0);
        assertThat(first.clientErrors()).isEqualTo(2);
        assertThat(first.serverErrors()).isEqualTo(1);
        assertThat(first.errorRate()).isEqualTo(0.1);

        MetricsHistoryResponse.Point empty = response.points().get(1);
        assertThat(empty.requestCount()).isZero();
        assertThat(empty.requestsPerMinute()).isZero();
        assertThat(empty.avgLatencyMs()).isNull();
        assertThat(empty.p95LatencyMs()).isNull();
        assertThat(empty.errorRate()).isNull();

        assertThat(response.points().get(2).requestCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("UTCID02 [N]: AC05 the summary covers the whole range")
    void summary_coversWholeRange() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T02:10:00Z");
        when(queryRepository.findMinuteTotals(from, to, null, null)).thenReturn(List.of(
                minute("2026-11-28T02:01:00Z", 15, 0, 3, 1500.0, 900.0, 7),
                minute("2026-11-28T02:07:00Z", 5, 1, 0, 100.0, 20.0, 0)));
        when(queryRepository.findEndpointTotals(from, to, null)).thenReturn(List.of());

        MetricsHistoryResponse.Stats summary = useCase.executeAt(request(from, to, "5m", null, null), NOW).summary();

        assertThat(summary.requestCount()).isEqualTo(20);
        assertThat(summary.requestsPerMinute()).isEqualTo(2.0);
        assertThat(summary.avgLatencyMs()).isEqualTo(80.0);
        assertThat(summary.maxLatencyMs()).isEqualTo(900.0);
        assertThat(summary.p50LatencyMs()).isEqualTo(900.0);
        assertThat(summary.clientErrors()).isEqualTo(1);
        assertThat(summary.serverErrors()).isEqualTo(3);
        assertThat(summary.errorRate()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("UTCID03 [N]: AC03 1d periods follow calendar days in Asia/Ho_Chi_Minh and 'from' is rounded down to local midnight")
    void dailyPeriods_followVietnamDays() {
        // 2026-11-26 09:00 Vietnam -> range starts 2026-11-26 00:00 Vietnam (2026-11-25T17:00Z)
        Instant from = Instant.parse("2026-11-26T02:00:00Z");
        Instant to = Instant.parse("2026-11-27T17:00:00Z");
        Instant rangeStart = Instant.parse("2026-11-25T17:00:00Z");
        when(queryRepository.findMinuteTotals(rangeStart, to, null, null)).thenReturn(List.of(
                // 23:59 on the 26th in Vietnam
                minute("2026-11-26T16:59:00Z", 2, 0, 0, 20.0, 10.0, 0),
                // 00:00 on the 27th in Vietnam
                minute("2026-11-26T17:00:00Z", 3, 0, 0, 30.0, 10.0, 0)));
        when(queryRepository.findEndpointTotals(rangeStart, to, null)).thenReturn(List.of());

        MetricsHistoryResponse response = useCase.executeAt(request(from, to, "1d", null, null), NOW);

        assertThat(response.from()).isEqualTo(OffsetDateTime.parse("2026-11-26T00:00:00+07:00"));
        assertThat(response.points()).extracting(MetricsHistoryResponse.Point::periodStart)
                .containsExactly(OffsetDateTime.parse("2026-11-26T00:00:00+07:00"), OffsetDateTime.parse("2026-11-27T00:00:00+07:00"));
        assertThat(response.points()).extracting(MetricsHistoryResponse.Point::requestCount).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("UTCID04 [B]: AC06 the range stops at the start of the current minute; a partial last period uses only its covered minutes")
    void rangeEnd_cutAtCurrentMinute() {
        Instant from = Instant.parse("2026-11-28T03:00:00Z");
        Instant currentMinute = Instant.parse("2026-11-28T03:30:00Z");
        when(queryRepository.findMinuteTotals(from, currentMinute, null, null)).thenReturn(List.of(
                minute("2026-11-28T03:10:00Z", 60, 0, 0, 600.0, 10.0, 0)));
        when(queryRepository.findEndpointTotals(from, currentMinute, null)).thenReturn(List.of());

        MetricsHistoryResponse response = useCase.executeAt(
                request(from, Instant.parse("2026-11-28T05:00:00Z"), "1h", null, null), NOW);

        assertThat(response.to()).isEqualTo(OffsetDateTime.parse("2026-11-28T10:30:00+07:00"));
        assertThat(response.points()).singleElement().satisfies(point -> {
            assertThat(point.requestCount()).isEqualTo(60);
            assertThat(point.requestsPerMinute()).isEqualTo(2.0);
        });
    }

    @Test
    @DisplayName("UTCID05 [N]: AC05 top endpoints are the 10 highest by p95 latency")
    void topEndpoints_top10ByP95() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T03:00:00Z");
        List<ApiEndpointMetrics> totals = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            totals.add(endpoint("GET", "/api/e" + slot, 10, 0, 0, 100.0, 20_000.0, slot));
        }
        when(queryRepository.findMinuteTotals(from, to, null, null)).thenReturn(List.of());
        when(queryRepository.findEndpointTotals(from, to, null)).thenReturn(totals);

        List<MetricsHistoryResponse.EndpointItem> top = useCase.executeAt(request(from, to, "1h", null, null), NOW).topEndpoints();

        assertThat(top).hasSize(10);
        assertThat(top).extracting(MetricsHistoryResponse.EndpointItem::uri)
                .startsWith("/api/e11", "/api/e10", "/api/e9")
                .doesNotContain("/api/e0", "/api/e1");
        assertThat(top.getFirst().p95LatencyMs()).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("UTCID06 [N]: AC02/AC05 filtering by uri passes method and uri to the query and leaves topEndpoints empty")
    void uriFilter_noTopEndpoints() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T03:00:00Z");
        when(queryRepository.findMinuteTotals(from, to, "GET", "/api/orders/{id}")).thenReturn(List.of());

        MetricsHistoryResponse response = useCase.executeAt(request(from, to, "1h", "GET", "/api/orders/{id}"), NOW);

        assertThat(response.topEndpoints()).isEmpty();
        assertThat(response.filter()).isEqualTo(new MetricsHistoryResponse.Filter("GET", "/api/orders/{id}"));
        verify(queryRepository, never()).findEndpointTotals(any(), any(), any());
    }

    @Test
    @DisplayName("UTCID07 [N]: AC02 a method-only filter still returns top endpoints for that method")
    void methodFilter_topEndpointsForMethod() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T03:00:00Z");
        when(queryRepository.findMinuteTotals(from, to, "POST", null)).thenReturn(List.of());
        when(queryRepository.findEndpointTotals(from, to, "POST"))
                .thenReturn(List.of(endpoint("POST", "/api/cart/items", 3, 0, 0, 30.0, 10.0, 0)));

        MetricsHistoryResponse response = useCase.executeAt(request(from, to, "1h", "POST", null), NOW);

        assertThat(response.topEndpoints()).extracting(MetricsHistoryResponse.EndpointItem::uri).containsExactly("/api/cart/items");
    }

    @Test
    @DisplayName("UTCID08 [A]: AC02 from not before to is rejected with INVALID_METRICS_RANGE")
    void fromNotBeforeTo_rejected() {
        Instant at = Instant.parse("2026-11-28T02:00:00Z");

        assertThatThrownBy(() -> useCase.executeAt(request(at, at, "1h", null, null), NOW))
                .isInstanceOf(InvalidMetricsRangeException.class)
                .hasMessage("from must be before to");
        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("UTCID09 [A]: AC02 from older than 30 days is rejected; exactly 30 days is allowed")
    void fromOlderThan30Days_rejected() {
        Instant thirtyDaysAgo = NOW.minusSeconds(30L * 24 * 3600);

        assertThatThrownBy(() -> useCase.executeAt(request(thirtyDaysAgo.minusSeconds(1), NOW, "1d", null, null), NOW))
                .isInstanceOf(InvalidMetricsRangeException.class)
                .hasMessage("from must be within the last 30 days");

        when(queryRepository.findMinuteTotals(any(), any(), isNull(), isNull())).thenReturn(List.of());
        when(queryRepository.findEndpointTotals(any(), any(), isNull())).thenReturn(List.of());
        assertThat(useCase.executeAt(request(thirtyDaysAgo, NOW, "1d", null, null), NOW).points()).hasSize(31);
    }

    @Test
    @DisplayName("UTCID10 [A]: AC06 a range starting in the current minute or later is rejected")
    void fromInCurrentMinute_rejected() {
        assertThatThrownBy(() -> useCase.executeAt(
                request(Instant.parse("2026-11-28T03:30:00Z"), Instant.parse("2026-11-28T04:00:00Z"), "1m", null, null), NOW))
                .isInstanceOf(InvalidMetricsRangeException.class)
                .hasMessage("from must be before the current minute");
    }

    @Test
    @DisplayName("UTCID11 [B]: AC02 more than 1,500 points is rejected; exactly 1,500 is allowed")
    void maxPoints() {
        Instant to = Instant.parse("2026-11-28T03:00:00Z");

        assertThatThrownBy(() -> useCase.executeAt(request(to.minusSeconds(1501L * 60), to, "1m", null, null), NOW))
                .isInstanceOf(InvalidMetricsRangeException.class)
                .hasMessageContaining("more than 1500 points");
        verify(queryRepository, never()).findMinuteTotals(any(), any(), any(), any());

        when(queryRepository.findMinuteTotals(any(), eq(to), isNull(), isNull())).thenReturn(List.of());
        when(queryRepository.findEndpointTotals(any(), eq(to), isNull())).thenReturn(List.of());
        assertThat(useCase.executeAt(request(to.minusSeconds(1500L * 60), to, "1m", null, null), NOW).points()).hasSize(1500);
    }

    @Test
    @DisplayName("UTCID12 [N]: AC04 timestamps are returned in Asia/Ho_Chi_Minh and the interval code is echoed")
    void displayTimes() {
        Instant from = Instant.parse("2026-11-28T02:00:00Z");
        Instant to = Instant.parse("2026-11-28T03:00:00Z");
        when(queryRepository.findMinuteTotals(from, to, null, null)).thenReturn(List.of());
        when(queryRepository.findEndpointTotals(from, to, null)).thenReturn(List.of());

        MetricsHistoryResponse response = useCase.executeAt(request(from, to, "1h", null, null), NOW);

        assertThat(response.from()).isEqualTo(OffsetDateTime.parse("2026-11-28T09:00:00+07:00"));
        assertThat(response.from().getOffset().getId()).isEqualTo("+07:00");
        assertThat(response.interval()).isEqualTo("1h");
        assertThat(response.summary().requestCount()).isZero();
        assertThat(response.summary().p95LatencyMs()).isNull();
    }

    private static MetricsHistoryRequest request(Instant from, Instant to, String interval, String method, String uri) {
        return new MetricsHistoryRequest(
                from.atOffset(java.time.ZoneOffset.ofHours(7)), to.atOffset(java.time.ZoneOffset.ofHours(7)), interval, method, uri);
    }

    private static MinuteMetrics minute(String minute, long count, long client, long server, double totalMs, double maxMs, int slot) {
        return new MinuteMetrics(Instant.parse(minute), endpoint(null, null, count, client, server, totalMs, maxMs, slot));
    }

    private static ApiEndpointMetrics endpoint(String method, String uri, long count, long client, long server,
                                               double totalMs, double maxMs, int slot) {
        long[] slots = new long[LatencyBuckets.SLOT_COUNT];
        slots[slot] = count;
        return new ApiEndpointMetrics(method, uri, count, client, server, totalMs, maxMs, slots);
    }
}
