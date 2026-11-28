package com.lumintra.backend.monitoring;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.monitoring.api.controller.SystemMetricsController;
import com.lumintra.backend.monitoring.api.request.LiveSystemMetricsRequest;
import com.lumintra.backend.monitoring.api.response.LiveSystemMetricsResponse;
import com.lumintra.backend.monitoring.application.GetLiveSystemMetricsUseCase;
import com.lumintra.backend.monitoring.application.GetMetricsHistoryUseCase;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
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
class SystemMetricsControllerTest {

    private static final String PATH = "/api/management/admin/system/metrics";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLiveSystemMetricsUseCase useCase;

    @MockitoBean
    private GetMetricsHistoryUseCase metricsHistoryUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("UTCID01 [N]: AC01/AC08 ADMIN receives 200 with the ApiResponse envelope and +07:00 timestamps")
    void admin_returns200() throws Exception {
        when(useCase.execute(any(LiveSystemMetricsRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .param("window", "5m")
                        .param("sort", "count")
                        .param("limit", "20")
                        .with(user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedAt").value("2026-11-26T23:00:05+07:00"))
                .andExpect(jsonPath("$.data.window.from").value("2026-11-26T22:55:00+07:00"))
                .andExpect(jsonPath("$.data.instance.instanceId").value("api-1"))
                .andExpect(jsonPath("$.data.overview.totalRequests").value(12))
                .andExpect(jsonPath("$.data.overview.p95LatencyMs").value(200.0))
                .andExpect(jsonPath("$.data.endpoints[0].uri").value("/api/orders/{id}"))
                .andExpect(jsonPath("$.data.components.db.status").value("UP"))
                .andExpect(jsonPath("$.data.components.redis.status").value("DOWN"))
                .andExpect(jsonPath("$.data.components.jvm.threadsLive").value(58));

        ArgumentCaptor<LiveSystemMetricsRequest> captor = ArgumentCaptor.forClass(LiveSystemMetricsRequest.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LiveSystemMetricsRequest("5m", "count", 20));
    }

    @Test
    @DisplayName("UTCID02 [N]: AC02 omitted parameters default to window=15m, sort=p95Ms, limit=10")
    void defaults_applied() throws Exception {
        when(useCase.execute(any(LiveSystemMetricsRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH).with(user("1").roles("ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<LiveSystemMetricsRequest> captor = ArgumentCaptor.forClass(LiveSystemMetricsRequest.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LiveSystemMetricsRequest("15m", "p95Ms", 10));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "window, 10m",
            "window, 1h",
            "sort, latency",
            "limit, 0",
            "limit, 51",
            "limit, abc"
    })
    @DisplayName("UTCID03 [A]: AC02 invalid window, sort or limit returns 400 VALIDATION_FAILED")
    void invalidParameter_returns400(String name, String value) throws Exception {
        mockMvc.perform(get(PATH)
                        .param(name, value)
                        .with(user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID04 [A]: AC01 an unauthenticated request returns 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(useCase);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"MANAGER", "STAFF", "DESIGNER", "CUSTOMER"})
    @DisplayName("UTCID05 [B]: AC01 non-admin roles, including MANAGER, receive 403")
    void nonAdmin_returns403(String role) throws Exception {
        mockMvc.perform(get(PATH)
                        .with(user("5").roles(role))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(useCase);
    }

    private static LiveSystemMetricsResponse sampleResponse() {
        return new LiveSystemMetricsResponse(
                OffsetDateTime.parse("2026-11-26T23:00:05+07:00"),
                new LiveSystemMetricsResponse.WindowInfo(
                        OffsetDateTime.parse("2026-11-26T22:55:00+07:00"),
                        OffsetDateTime.parse("2026-11-26T23:00:00+07:00")),
                new LiveSystemMetricsResponse.InstanceInfo("api-1", OffsetDateTime.parse("2026-11-26T22:00:00+07:00"), 3605),
                new LiveSystemMetricsResponse.Overview(12, 2.4, 120.5, 100.0, 200.0, 200.0, 180.2, 1, 0, 0.0),
                List.of(new LiveSystemMetricsResponse.EndpointItem(
                        "GET", "/api/orders/{id}", 12, 120.5, 200.0, 200.0, 180.2, 1, 0, 0.0)),
                new LiveSystemMetricsResponse.Components(
                        new LiveSystemMetricsResponse.DbStatus("UP", 1, 2, 0),
                        new LiveSystemMetricsResponse.RedisStatus("DOWN"),
                        new LiveSystemMetricsResponse.JvmStatus(400.0, 2048.0, 58, 0.1)));
    }
}
