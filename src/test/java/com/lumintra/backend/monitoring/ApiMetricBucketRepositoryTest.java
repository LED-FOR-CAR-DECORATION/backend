package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.ApiEndpointMetrics;
import com.lumintra.backend.monitoring.domain.model.LatencyBuckets;
import com.lumintra.backend.monitoring.infrastructure.persistence.entity.ApiMetricBucketEntity;
import com.lumintra.backend.monitoring.infrastructure.persistence.repository.ApiMetricBucketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(classes = ApiMetricBucketRepositoryTest.Config.class)
class ApiMetricBucketRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ApiMetricBucketEntity.class)
    @EnableJpaRepositories(basePackageClasses = ApiMetricBucketRepository.class)
    static class Config {}

    private static final Instant MINUTE = Instant.parse("2026-11-26T12:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApiMetricBucketRepository repository;

    @Test
    @DisplayName("UTCID01 [N]: AC07 a bucket row round-trips all columns, including the latency bucket array")
    void save_roundTripsAllColumns() {
        long[] buckets = new long[LatencyBuckets.SLOT_COUNT];
        buckets[0] = 4;
        buckets[13] = 1;
        ApiEndpointMetrics metrics = new ApiEndpointMetrics(
                "GET", "/api/orders/{id}", 5, 1, 1, 10_250.5, 10_100.25, buckets);

        Long id = repository.save(ApiMetricBucketEntity.of(MINUTE, "api-1", metrics)).getBucketId();
        entityManager.flush();
        entityManager.clear();

        ApiMetricBucketEntity found = repository.findById(id).orElseThrow();
        assertThat(found.getBucketStart()).isEqualTo(MINUTE);
        assertThat(found.getInstanceId()).isEqualTo("api-1");
        assertThat(found.getHttpMethod()).isEqualTo("GET");
        assertThat(found.getUriTemplate()).isEqualTo("/api/orders/{id}");
        assertThat(found.getRequestCount()).isEqualTo(5);
        assertThat(found.getClientErrorCount()).isEqualTo(1);
        assertThat(found.getServerErrorCount()).isEqualTo(1);
        assertThat(found.getTotalTimeMs()).isEqualTo(10_250.5);
        assertThat(found.getMaxTimeMs()).isEqualTo(10_100.25);
        assertThat(found.getLatencyBuckets()).containsExactly(buckets);
    }

    @Test
    @DisplayName("UTCID02 [A]: AC07 a second row for the same minute, instance and endpoint violates the unique key")
    void save_duplicateMinuteEndpoint_rejected() {
        ApiEndpointMetrics metrics = new ApiEndpointMetrics(
                "GET", "/api/cart", 1, 0, 0, 5.0, 5.0, new long[LatencyBuckets.SLOT_COUNT]);
        repository.saveAndFlush(ApiMetricBucketEntity.of(MINUTE, "api-1", metrics));

        assertThatThrownBy(() -> repository.saveAndFlush(ApiMetricBucketEntity.of(MINUTE, "api-1", metrics)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UTCID03 [N]: BR-GEN-25 two instances can store the same minute and endpoint")
    void save_sameMinuteDifferentInstances_allowed() {
        ApiEndpointMetrics metrics = new ApiEndpointMetrics(
                "GET", "/api/cart", 1, 0, 0, 5.0, 5.0, new long[LatencyBuckets.SLOT_COUNT]);
        repository.saveAndFlush(ApiMetricBucketEntity.of(MINUTE, "api-1", metrics));
        repository.saveAndFlush(ApiMetricBucketEntity.of(MINUTE, "api-2", metrics));

        assertThat(repository.count()).isEqualTo(2);
    }
}
