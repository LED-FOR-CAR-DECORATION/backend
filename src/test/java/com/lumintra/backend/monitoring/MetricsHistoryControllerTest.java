package com.lumintra.backend.monitoring;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.monitoring.api.controller.SystemMetricsController;
import com.lumintra.backend.monitoring.api.request.MetricsHistoryRequest;
import com.lumintra.backend.monitoring.api.response.MetricsHistoryResponse;
import com.lumintra.backend.monitoring.application.GetLiveSystemMetricsUseCase;
import com.lumintra.backend.monitoring.application.GetMetricsHistoryUseCase;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.monitoring.InvalidMetricsRangeException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemMetricsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class MetricsHistoryControllerTest {

    private static final String PATH = "/api/management/admin/system/metrics/history";
    private static final String FROM = "2026-11-27T00:00:00+07:00";
    private static final String TO = "2026-11-28T00:00:00+07:00";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMetricsHistoryUseCase useCase;

    @MockitoBean
    private GetLiveSystemMetricsUseCase liveSystemMetricsUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("UTCID01 [N]: AC01/AC04 ADMIN receives 200 with summary, points and top endpoints in the ApiResponse envelope")
    void admin_returns200() throws Exception {
        when(useCase.execute(any(MetricsHistoryRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .param("from", FROM)
                        .param("to", TO)
                        .param("interval", "1d")
                        .param("method", "GET")
                        .with(user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value(FROM))
                .andExpect(jsonPath("$.data.interval").value("1d"))
                .andExpect(jsonPath("$.data.filter.method").value("GET"))
                .andExpect(jsonPath("$.data.summary.requestCount").value(5))
                .andExpect(jsonPath("$.data.points[0].periodStart").value(FROM))
                .andExpect(jsonPath("$.data.points[1].requestCount").value(0))
                .andExpect(jsonPath("$.data.points[1].avgLatencyMs").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.topEndpoints[0].uri").value("/api/orders"));

        ArgumentCaptor<MetricsHistoryRequest> captor = ArgumentCaptor.forClass(MetricsHistoryRequest.class);
        verify(useCase).execute(captor.capture());
        MetricsHistoryRequest request = captor.getValue();
        assertThat(request.from()).isEqualTo(OffsetDateTime.parse(FROM));
        assertThat(request.to()).isEqualTo(OffsetDateTime.parse(TO));
        assertThat(request.interval()).isEqualTo("1d");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isNull();
    }

    @Test
    @DisplayName("UTCID02 [N]: AC02 interval defaults to 1h and the uri filter is passed through")
    void defaults_andUriFilter() throws Exception {
        when(useCase.execute(any(MetricsHistoryRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .param("from", FROM)
                        .param("to", TO)
                        .param("uri", "/api/orders/{id}")
                        .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<MetricsHistoryRequest> captor = ArgumentCaptor.forClass(MetricsHistoryRequest.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue().interval()).isEqualTo("1h");
        assertThat(captor.getValue().uri()).isEqualTo("/api/orders/{id}");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(value = {
            "missing from;                 to=" + TO,
            "missing to;                   from=" + FROM,
            "unparseable from;             from=yesterday&to=" + TO,
            "bad interval;                 from=" + FROM + "&to=" + TO + "&interval=2h",
            "bad method;                   from=" + FROM + "&to=" + TO + "&method=get"
    }, delimiter = ';')
    @DisplayName("UTCID03 [A]: AC02 missing or malformed parameters return 400 VALIDATION_FAILED")
    void invalidParameters_return400(String caseName, String query) throws Exception {
        var builder = get(PATH).with(user("1").roles("ADMIN")).accept(MediaType.APPLICATION_JSON);
        for (String pair : query.trim().split("&")) {
            String[] parts = pair.split("=", 2);
            builder = builder.param(parts[0], parts[1]);
        }

        mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID04 [A]: AC02 a range the use case rejects returns 400 INVALID_METRICS_RANGE with its message")
    void invalidRange_returns400() throws Exception {
        when(useCase.execute(any(MetricsHistoryRequest.class)))
                .thenThrow(new InvalidMetricsRangeException("from must be within the last 30 days"));

        mockMvc.perform(get(PATH)
                        .param("from", FROM)
                        .param("to", TO)
                        .with(user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_METRICS_RANGE"))
                .andExpect(jsonPath("$.message").value("from must be within the last 30 days"));
    }

    @Test
    @DisplayName("UTCID05 [A]: AC01 an unauthenticated request returns 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PATH).param("from", FROM).param("to", TO).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(useCase);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"MANAGER", "STAFF", "DESIGNER", "CUSTOMER"})
    @DisplayName("UTCID06 [B]: AC01 non-admin roles, including MANAGER, receive 403")
    void nonAdmin_returns403(String role) throws Exception {
        mockMvc.perform(get(PATH)
                        .param("from", FROM)
                        .param("to", TO)
                        .with(user("5").roles(role))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(useCase);
    }

    private static MetricsHistoryResponse sampleResponse() {
        MetricsHistoryResponse.Stats summary = new MetricsHistoryResponse.Stats(
                5, 0.0, 120.0, 100.0, 200.0, 200.0, 180.0, 1, 0, 0.0);
        return new MetricsHistoryResponse(
                OffsetDateTime.parse(FROM),
                OffsetDateTime.parse(TO),
                "1d",
                new MetricsHistoryResponse.Filter("GET", null),
                summary,
                List.of(
                        new MetricsHistoryResponse.Point(OffsetDateTime.parse(FROM), 5, 0.0, 120.0, 100.0, 200.0, 200.0, 180.0, 1, 0, 0.0),
                        new MetricsHistoryResponse.Point(OffsetDateTime.parse(TO), 0, 0.0, null, null, null, null, null, 0, 0, null)),
                List.of(new MetricsHistoryResponse.EndpointItem("GET", "/api/orders", 5, 120.0, 200.0, 200.0, 180.0, 1, 0, 0.0)));
    }
}
