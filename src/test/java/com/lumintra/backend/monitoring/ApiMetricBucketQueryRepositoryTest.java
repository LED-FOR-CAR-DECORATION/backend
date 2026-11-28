package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.domain.model.MinuteMetrics;
import com.lumintra.backend.monitoring.infrastructure.persistence.entity.ApiMetricBucketEntity;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketQueryRepository;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(classes = ApiMetricBucketQueryRepositoryTest.Config.class)
class ApiMetricBucketQueryRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ApiMetricBucketEntity.class)
    @EnableJpaRepositories(basePackageClasses = ApiMetricBucketRepository.class)
    @Import(ApiMetricBucketQueryRepository.class)
    static class Config {}

    private static final Instant M0 = Instant.parse("2026-11-27T10:00:00Z");
    private static final Instant M1 = Instant.parse("2026-11-27T10:01:00Z");
    private static final Instant M2 = Instant.parse("2026-11-27T10:02:00Z");

    @Autowired
    private ApiMetricBucketRepository repository;

    @Autowired
    private ApiMetricBucketQueryRepository queryRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seed() {
        // minute 0: two instances serve the same endpoint, plus another endpoint
        save(M0, "api-1", "GET", "/api/orders", 4, 1, 0, 400.0, 150.0, 0, 4);
        save(M0, "api-2", "GET", "/api/orders", 6, 0, 2, 900.0, 300.0, 3, 6);
        save(M0, "api-1", "POST", "/api/cart/items", 2, 0, 0, 100.0, 60.0, 2, 2);
        // minute 1
        save(M1, "api-1", "GET", "/api/orders", 1, 0, 0, 20.0, 20.0, 0, 1);
        // minute 2 is outside [M0, M2)
        save(M2, "api-1", "GET", "/api/orders", 100, 0, 0, 100.0, 1.0, 0, 100);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("UTCID01 [N]: AC03 rows are added up per minute across instances and endpoints, end exclusive, oldest first")
    void findMinuteTotals_addsAcrossInstancesAndEndpoints() {
        List<MinuteMetrics> minutes = queryRepository.findMinuteTotals(M0, M2, null, null);

        assertThat(minutes).extracting(MinuteMetrics::bucketStart).containsExactly(M0, M1);
        ApiEndpointMetrics first = minutes.getFirst().metrics();
        assertThat(first.httpMethod()).isNull();
        assertThat(first.uriTemplate()).isNull();
        assertThat(first.requestCount()).isEqualTo(12);
        assertThat(first.clientErrorCount()).isEqualTo(1);
        assertThat(first.serverErrorCount()).isEqualTo(2);
        assertThat(first.totalTimeMs()).isEqualTo(1400.0);
        assertThat(first.maxTimeMs()).isEqualTo(300.0);
        long[] slots = first.latencyBucketCounts();
        assertThat(slots).hasSize(LatencyBuckets.SLOT_COUNT);
        assertThat(slots[0]).isEqualTo(4);
        assertThat(slots[2]).isEqualTo(2);
        assertThat(slots[3]).isEqualTo(6);
    }

    @Test
    @DisplayName("UTCID02 [N]: AC02 filtering by method and uri only adds up the matching rows")
    void findMinuteTotals_filtersByMethodAndUri() {
        List<MinuteMetrics> orders = queryRepository.findMinuteTotals(M0, M2, "GET", "/api/orders");
        List<MinuteMetrics> posts = queryRepository.findMinuteTotals(M0, M2, "POST", null);

        assertThat(orders).extracting(minute -> minute.metrics().requestCount()).containsExactly(10L, 1L);
        assertThat(posts).singleElement().extracting(minute -> minute.metrics().requestCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("UTCID03 [B]: AC04 a range without stored rows returns no minutes")
    void findMinuteTotals_emptyRange() {
        assertThat(queryRepository.findMinuteTotals(M2.plusSeconds(60), M2.plusSeconds(600), null, null)).isEmpty();
    }

    @Test
    @DisplayName("UTCID04 [N]: AC05 endpoint totals add up every minute in the range per method and route")
    void findEndpointTotals_perEndpoint() {
        List<ApiEndpointMetrics> totals = queryRepository.findEndpointTotals(M0, M2, null);

        assertThat(totals)
                .extracting(ApiEndpointMetrics::httpMethod, ApiEndpointMetrics::uriTemplate,
                        ApiEndpointMetrics::requestCount, ApiEndpointMetrics::maxTimeMs)
                .containsExactlyInAnyOrder(
                        tuple("GET", "/api/orders", 11L, 300.0),
                        tuple("POST", "/api/cart/items", 2L, 60.0));
        assertThat(queryRepository.findEndpointTotals(M0, M2, "POST"))
                .extracting(ApiEndpointMetrics::uriTemplate).containsExactly("/api/cart/items");
    }

    private void save(Instant minute, String instance, String method, String uri, long count, long client, long server,
                      double totalMs, double maxMs, int slot, long slotCount) {
        long[] slots = new long[LatencyBuckets.SLOT_COUNT];
        slots[slot] = slotCount;
        repository.save(ApiMetricBucketEntity.of(minute, instance,
                new ApiEndpointMetrics(method, uri, count, client, server, totalMs, maxMs, slots)));
    }
}
