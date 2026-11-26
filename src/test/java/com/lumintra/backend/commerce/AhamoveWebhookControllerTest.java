package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.AhamoveWebhookController;
import com.lumintra.backend.commerce.api.request.AhamoveWebhookRequest;
import com.lumintra.backend.commerce.api.response.AhamoveWebhookResponse;
import com.lumintra.backend.commerce.application.ProcessAhamoveWebhookUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AhamoveWebhookController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AhamoveWebhookControllerTest {

    private static final String PATH = "/api/commerce/webhooks/ahamove";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessAhamoveWebhookUseCase processAhamoveWebhookUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("COM-14 UTCID01 [N]: Public webhook endpoint permits un-JWT calls with valid API-Key/Bearer credentials and ignores extra carrier fields")
    void handleAhamoveWebhook_validRequest_returns200() throws Exception {
        AhamoveWebhookResponse response = AhamoveWebhookResponse.builder()
                .trackingNumber("26ABCDEF")
                .outcome("PROCESSED")
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .orderStatus(OrderStatus.DELIVERED)
                .message("Webhook processed successfully.")
                .build();

        when(processAhamoveWebhookUseCase.execute(any(HttpHeaders.class), any(AhamoveWebhookRequest.class)))
                .thenReturn(response);

        String ahamovePayload = """
                {
                  "_id": "26ABCDEF",
                  "status": "COMPLETED",
                  "sub_status": "",
                  "shared_link": "https://expressstg.ahamove.com/s/26ABCDEF",
                  "total_fee": 35000,
                  "complete_time": 1762938000,
                  "supplier_id": "84909998888",
                  "path": [
                    {"address": "Warehouse", "status": "COMPLETED"},
                    {"address": "Recipient", "status": "COMPLETED"}
                  ]
                }
                """;

        mockMvc.perform(post(PATH)
                        .header("X-Api-Key", "aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ahamovePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("26ABCDEF"))
                .andExpect(jsonPath("$.data.outcome").value("PROCESSED"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ahamovePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("PROCESSED"));

        verify(processAhamoveWebhookUseCase, org.mockito.Mockito.times(2))
                .execute(any(HttpHeaders.class), any(AhamoveWebhookRequest.class));
    }

    @Test
    @DisplayName("COM-14 UTCID02 [A]: Missing or invalid webhook credentials return 401 UNAUTHORIZED")
    void handleAhamoveWebhook_invalidCredentials_returns401Unauthorized() throws Exception {
        when(processAhamoveWebhookUseCase.execute(any(HttpHeaders.class), any(AhamoveWebhookRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid AhaMove webhook credentials."));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_id\":\"26ABCDEF\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("COM-14 UTCID03 [B]: Malformed JSON or blank _id / status returns 400 VALIDATION_FAILED without calling use case")
    void handleAhamoveWebhook_invalidPayload_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Api-Key", "aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post(PATH)
                        .header("X-Api-Key", "aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_id\":\"   \",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("orderId"));

        mockMvc.perform(post(PATH)
                        .header("X-Api-Key", "aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_id\":\"26ABCDEF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        verifyNoInteractions(processAhamoveWebhookUseCase);
    }

    @Test
    @DisplayName("COM-14 UTCID04 [A]: Unexpected runtime exception maps to 500 INTERNAL_SERVER_ERROR")
    void handleAhamoveWebhook_unexpectedFailure_returns500() throws Exception {
        when(processAhamoveWebhookUseCase.execute(any(HttpHeaders.class), any(AhamoveWebhookRequest.class)))
                .thenThrow(new IllegalStateException("DB timeout"));

        mockMvc.perform(post(PATH)
                        .header("X-Api-Key", "aha-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_id\":\"26ABCDEF\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
