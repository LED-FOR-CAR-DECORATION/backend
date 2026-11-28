package com.lumintra.backend.monitoring.infrastructure.metrics;

import com.lumintra.backend.monitoring.domain.model.SystemComponentsStatus;
import com.lumintra.backend.monitoring.domain.service.SystemComponentsProbe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Builds {@link SystemComponentsStatus} from the actuator health indicators ({@code db}, {@code redis})
 * and Micrometer gauges (HikariCP pool, JVM memory, threads, process CPU).
 */
@Component
@RequiredArgsConstructor
public class ActuatorSystemComponentsProbe implements SystemComponentsProbe {

    private static final Logger log = LoggerFactory.getLogger(ActuatorSystemComponentsProbe.class);
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;

    @Override
    public SystemComponentsStatus probe() {
        return new SystemComponentsStatus(
                new SystemComponentsStatus.Database(
                        healthStatus("db"),
                        toInteger(sumGauges(meterRegistry.find("hikaricp.connections.active"))),
                        toInteger(sumGauges(meterRegistry.find("hikaricp.connections.idle"))),
                        toInteger(sumGauges(meterRegistry.find("hikaricp.connections.pending")))
                ),
                new SystemComponentsStatus.Redis(healthStatus("redis")),
                new SystemComponentsStatus.Jvm(
                        toMb(sumGauges(meterRegistry.find("jvm.memory.used").tag("area", "heap"))),
                        toMb(sumGauges(meterRegistry.find("jvm.memory.max").tag("area", "heap"))),
                        toInteger(sumGauges(meterRegistry.find("jvm.threads.live"))),
                        sumGauges(meterRegistry.find("process.cpu.usage"))
                )
        );
    }

    private String healthStatus(String component) {
        try {
            HealthDescriptor descriptor = healthEndpoint.healthForPath(component);
            if (descriptor == null || descriptor.getStatus() == null) {
                return SystemComponentsStatus.STATUS_UNKNOWN;
            }
            return descriptor.getStatus().getCode();
        } catch (RuntimeException ex) {
            log.warn("Health check failed [component={}]", component, ex);
            return SystemComponentsStatus.STATUS_DOWN;
        }
    }

    /**
     * Sums all matching gauges, skipping undefined values (NaN, and the -1 that JVM memory pools
     * report for "no limit"). Returns {@code null} when no gauge has a defined value.
     */
    private static Double sumGauges(Search search) {
        Collection<Gauge> gauges = search.gauges();
        double sum = 0;
        boolean found = false;
        for (Gauge gauge : gauges) {
            double value = gauge.value();
            if (!Double.isNaN(value) && value >= 0) {
                sum += value;
                found = true;
            }
        }
        return found ? sum : null;
    }

    private static Integer toInteger(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private static Double toMb(Double bytes) {
        return bytes == null ? null : bytes / BYTES_PER_MB;
    }
}
