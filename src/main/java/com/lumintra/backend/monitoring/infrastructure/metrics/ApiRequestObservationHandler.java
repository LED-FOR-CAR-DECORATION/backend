package com.lumintra.backend.monitoring.infrastructure.metrics;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * Feeds every completed HTTP request observed by Spring ({@code http.server.requests}) into
 * {@link ApiRequestMetricsRecorder}. Spring Boot registers {@code ObservationHandler} beans with
 * the observation registry automatically.
 */
@Component
@RequiredArgsConstructor
public class ApiRequestObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    private static final String START_NANOS_KEY = ApiRequestObservationHandler.class.getName() + ".startNanos";

    private final ApiRequestMetricsRecorder recorder;

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(START_NANOS_KEY, System.nanoTime());
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        Long startNanos = context.get(START_NANOS_KEY);
        HttpServletRequest request = context.getCarrier();
        if (startNanos == null || request == null) {
            return;
        }
        HttpServletResponse response = context.getResponse();
        recorder.record(
                request.getMethod(),
                context.getPathPattern(),
                response != null ? response.getStatus() : 0,
                context.getError() != null,
                System.nanoTime() - startNanos
        );
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }
}
