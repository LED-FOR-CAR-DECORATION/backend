package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.infrastructure.metrics.ApiRequestMetricsRecorder;
import com.lumintra.backend.monitoring.infrastructure.metrics.ApiRequestObservationHandler;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApiRequestObservationHandlerTest {

    @Mock
    private ApiRequestMetricsRecorder recorder;

    private ApiRequestObservationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiRequestObservationHandler(recorder);
    }

    @Test
    @DisplayName("UTCID01 [N]: AC04 a completed request is recorded with method, route template, status and duration")
    void onStop_completedRequest_recordsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        context.setPathPattern("/api/orders/{id}");

        handler.onStart(context);
        handler.onStop(context);

        verify(recorder).record(eq("GET"), eq("/api/orders/{id}"), eq(404), eq(false), geq(0L));
    }

    @Test
    @DisplayName("UTCID02 [A]: AC04 a request that ended with an exception is flagged as failed")
    void onStop_withError_flagsFailed() {
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                new MockHttpServletRequest("POST", "/api/orders/checkout"), new MockHttpServletResponse());
        context.setPathPattern("/api/orders/checkout");
        context.setError(new IllegalStateException("boom"));

        handler.onStart(context);
        handler.onStop(context);

        verify(recorder).record(eq("POST"), eq("/api/orders/checkout"), eq(200), eq(true), anyLong());
    }

    @Test
    @DisplayName("UTCID03 [B]: AC04 a stop without a matching start is ignored")
    void onStop_withoutStart_isIgnored() {
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", "/api/cart"), new MockHttpServletResponse());

        handler.onStop(context);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("UTCID04 [N]: AC03 the handler only applies to server HTTP request observations")
    void supportsContext_onlyServerRequests() {
        ServerRequestObservationContext serverContext = new ServerRequestObservationContext(
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(handler.supportsContext(serverContext)).isTrue();
        assertThat(handler.supportsContext(new Observation.Context())).isFalse();
    }
}
