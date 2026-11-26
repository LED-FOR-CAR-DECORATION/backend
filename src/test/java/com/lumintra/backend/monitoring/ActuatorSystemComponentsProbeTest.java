package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.SystemComponentsStatus;
import com.lumintra.backend.monitoring.infrastructure.metrics.ActuatorSystemComponentsProbe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActuatorSystemComponentsProbeTest {

    private static final double MB = 1024.0 * 1024.0;

    @Mock
    private HealthEndpoint healthEndpoint;

    private MeterRegistry registry;
    private ActuatorSystemComponentsProbe probe;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        probe = new ActuatorSystemComponentsProbe(healthEndpoint, registry);
    }

    @Test
    @DisplayName("UTCID01 [N]: AC07 db and redis status come from the health indicators; pool and JVM figures from gauges")
    void probe_allAvailable() {
        HealthDescriptor dbHealth = descriptor(Status.UP);
        when(healthEndpoint.healthForPath("db")).thenReturn(dbHealth);
        HealthDescriptor redisHealth = descriptor(Status.UP);
        when(healthEndpoint.healthForPath("redis")).thenReturn(redisHealth);
        gauge("hikaricp.connections.active", 1, "pool", "HikariPool-1");
        gauge("hikaricp.connections.idle", 2, "pool", "HikariPool-1");
        gauge("hikaricp.connections.pending", 0, "pool", "HikariPool-1");
        gauge("jvm.memory.used", 100 * MB, "area", "heap", "id", "G1 Eden Space");
        gauge("jvm.memory.used", 50 * MB, "area", "heap", "id", "G1 Old Gen");
        gauge("jvm.memory.used", 80 * MB, "area", "nonheap", "id", "Metaspace");
        gauge("jvm.memory.max", -1, "area", "heap", "id", "G1 Eden Space");
        gauge("jvm.memory.max", 2048 * MB, "area", "heap", "id", "G1 Old Gen");
        gauge("jvm.threads.live", 58);
        gauge("process.cpu.usage", 0.12);

        SystemComponentsStatus status = probe.probe();

        assertThat(status.db()).isEqualTo(new SystemComponentsStatus.Database("UP", 1, 2, 0));
        assertThat(status.redis().status()).isEqualTo("UP");
        // non-heap memory is excluded; the -1 ("no limit") pool max is skipped
        assertThat(status.jvm().heapUsedMb()).isEqualTo(150.0);
        assertThat(status.jvm().heapMaxMb()).isEqualTo(2048.0);
        assertThat(status.jvm().threadsLive()).isEqualTo(58);
        assertThat(status.jvm().cpuUsage()).isEqualTo(0.12);
    }

    @Test
    @DisplayName("UTCID02 [A]: AC07 a health check that throws is reported as DOWN and the probe still returns")
    void probe_healthCheckThrows_reportsDown() {
        when(healthEndpoint.healthForPath("db")).thenThrow(new IllegalStateException("connection refused"));
        HealthDescriptor redisHealth = descriptor(Status.DOWN);
        when(healthEndpoint.healthForPath("redis")).thenReturn(redisHealth);

        SystemComponentsStatus status = probe.probe();

        assertThat(status.db().status()).isEqualTo("DOWN");
        assertThat(status.redis().status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("UTCID03 [B]: AC07 a component without a health indicator is UNKNOWN and missing gauges are null")
    void probe_missingIndicatorAndGauges() {
        HealthDescriptor dbHealth = descriptor(Status.UP);
        when(healthEndpoint.healthForPath("db")).thenReturn(dbHealth);
        when(healthEndpoint.healthForPath("redis")).thenReturn(null);

        SystemComponentsStatus status = probe.probe();

        assertThat(status.redis().status()).isEqualTo("UNKNOWN");
        assertThat(status.db().activeConnections()).isNull();
        assertThat(status.jvm().heapUsedMb()).isNull();
        assertThat(status.jvm().heapMaxMb()).isNull();
        assertThat(status.jvm().threadsLive()).isNull();
        assertThat(status.jvm().cpuUsage()).isNull();
    }

    @Test
    @DisplayName("UTCID04 [B]: AC07 a gauge reporting NaN (e.g. CPU not yet measured) is null")
    void probe_nanGauge_isNull() {
        HealthDescriptor dbHealth = descriptor(Status.UP);
        when(healthEndpoint.healthForPath("db")).thenReturn(dbHealth);
        HealthDescriptor redisHealth = descriptor(Status.UP);
        when(healthEndpoint.healthForPath("redis")).thenReturn(redisHealth);
        gauge("process.cpu.usage", Double.NaN);

        assertThat(probe.probe().jvm().cpuUsage()).isNull();
    }

    private void gauge(String name, double value, String... tags) {
        Gauge.builder(name, () -> value).tags(tags).register(registry);
    }

    /** Built before the stubbing call that returns it, since Mockito does not allow nested stubbing. */
    private static HealthDescriptor descriptor(Status status) {
        IndicatedHealthDescriptor descriptor = mock(IndicatedHealthDescriptor.class);
        when(descriptor.getStatus()).thenReturn(status);
        return descriptor;
    }
}
