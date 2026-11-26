package com.lumintra.backend.payment;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.payment.api.controller.PaymentController;
import com.lumintra.backend.payment.api.request.SePayWebhookRequest;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.api.response.SePayWebhookResponse;
import com.lumintra.backend.payment.application.GetPaymentSessionUseCase;
import com.lumintra.backend.payment.application.MockPaymentSuccessUseCase;
import com.lumintra.backend.payment.application.ProcessSePayWebhookUseCase;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPaymentSessionUseCase getPaymentSessionUseCase;

    @MockitoBean
    private ProcessSePayWebhookUseCase processSePayWebhookUseCase;

    @MockitoBean
    private MockPaymentSuccessUseCase mockPaymentSuccessUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("PAY-01 UTCID01 [N]: GET /api/payments/{paymentCode} as owning CUSTOMER returns 200 OK with PaymentSessionResponse")
    void getPaymentSession_asOwningCustomer_returns200Ok() throws Exception {
        String code = "LUMORD102A1B2C3D4";
        Instant createdAt = Instant.parse("2026-11-12T16:45:00Z");
        Instant expiresAt = Instant.parse("2026-11-12T17:00:00Z");

        PaymentSessionResponse sessionResponse = PaymentSessionResponse.builder()
                .paymentId(5001L)
                .paymentCode(code)
                .targetType(PaymentTargetType.ORDER)
                .targetId(102L)
                .gateway("SEPAY")
                .qrUrl("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH")
                .bankName("TPBank")
                .accountNumber("10006140844")
                .accountName("NGUYEN QUANG KHANH")
                .amount(new BigDecimal("250000.00"))
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .paidAt(null)
                .createdAt(createdAt)
                .build();

        when(getPaymentSessionUseCase.execute(42L, code)).thenReturn(sessionResponse);

        mockMvc.perform(get("/api/payments/{paymentCode}", code)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(5001))
                .andExpect(jsonPath("$.data.paymentCode").value(code))
                .andExpect(jsonPath("$.data.targetType").value("ORDER"))
                .andExpect(jsonPath("$.data.targetId").value(102))
                .andExpect(jsonPath("$.data.gateway").value("SEPAY"))
                .andExpect(jsonPath("$.data.qrUrl").value("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH"))
                .andExpect(jsonPath("$.data.bankName").value("TPBank"))
                .andExpect(jsonPath("$.data.accountNumber").value("10006140844"))
                .andExpect(jsonPath("$.data.accountName").value("NGUYEN QUANG KHANH"))
                .andExpect(jsonPath("$.data.amount").value(250000.00))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-12T17:00:00Z"))
                .andExpect(jsonPath("$.data.paidAt").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-12T16:45:00Z"));

        verify(getPaymentSessionUseCase).execute(42L, code);
    }

    @Test
    @DisplayName("PAY-01 UTCID02 [A]: GET /api/payments/{paymentCode} without authentication returns 401 UNAUTHORIZED")
    void getPaymentSession_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentCode}", "LUMORD102A1B2C3D4"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(getPaymentSessionUseCase);
    }

    @Test
    @DisplayName("PAY-01 UTCID03 [A]: GET /api/payments/{paymentCode} as STAFF role returns 403 FORBIDDEN")
    void getPaymentSession_asEmployeeRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentCode}", "LUMORD102A1B2C3D4")
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(getPaymentSessionUseCase);
    }

    @Test
    @DisplayName("PAY-01 UTCID04 [A]: GET /api/payments/{paymentCode} for another customer's payment session returns 403 FORBIDDEN")
    void getPaymentSession_differentCustomerOwner_returns403Forbidden() throws Exception {
        String code = "LUMORD102A1B2C3D4";
        when(getPaymentSessionUseCase.execute(99L, code))
                .thenThrow(new AccessDeniedException("You do not have permission to access this payment session."));

        mockMvc.perform(get("/api/payments/{paymentCode}", code)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PAY-01 UTCID05 [A]: GET /api/payments/{paymentCode} when paymentCode does not exist returns 404 PAYMENT_SESSION_NOT_FOUND")
    void getPaymentSession_notFound_returns404NotFound() throws Exception {
        String code = "LUMUNKNOWN999";
        when(getPaymentSessionUseCase.execute(42L, code))
                .thenThrow(new PaymentSessionNotFoundException("Payment session not found."));

        mockMvc.perform(get("/api/payments/{paymentCode}", code)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PAY-01 UTCID06 [B]: GET /api/payments/{paymentCode} with blank paymentCode returns 400 VALIDATION_FAILED")
    void getPaymentSession_blankPaymentCode_returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentCode}", "   ")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(getPaymentSessionUseCase);
    }

    @Test
    @DisplayName("PAY-01 UTCID07 [A]: GET /api/payments/{paymentCode} with non-numeric principal returns 401 INVALID_CREDENTIALS")
    void getPaymentSession_nonNumericPrincipal_returns401InvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentCode}", "LUMORD102A1B2C3D4")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(getPaymentSessionUseCase);
    }

    @Test
    @DisplayName("PAY-02 [N]: POST /api/payments/webhooks/sepay is public and returns 200 ApiResponse")
    void processSepayWebhook_validPayload_returns200() throws Exception {
        SePayWebhookResponse response = new SePayWebhookResponse(
                92704L,
                10L,
                "LMTORD102A1B2C3D4",
                PaymentTargetType.ORDER,
                102L,
                PaymentStatus.SUCCEEDED,
                "PROCESSED",
                "Payment marked as SUCCEEDED."
        );
        when(processSePayWebhookUseCase.execute(any(HttpHeaders.class), any(SePayWebhookRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/webhooks/sepay")
                        .header("Authorization", "Apikey valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 92704,
                                  "gateway": "TPBank",
                                  "transactionDate": "2026-11-13 01:02:03",
                                  "accountNumber": "10006140844",
                                  "code": "LMTORD102A1B2C3D4",
                                  "content": "Chuyen tien LMTORD102A1B2C3D4",
                                  "transferType": "in",
                                  "transferAmount": 500000,
                                  "accumulated": 1500000,
                                  "referenceCode": "FT263170001",
                                  "description": "Bank transfer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sepayTxId").value(92704))
                .andExpect(jsonPath("$.data.paymentId").value(10))
                .andExpect(jsonPath("$.data.paymentCode").value("LMTORD102A1B2C3D4"))
                .andExpect(jsonPath("$.data.targetType").value("ORDER"))
                .andExpect(jsonPath("$.data.targetId").value(102))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.outcome").value("PROCESSED"));

        verify(processSePayWebhookUseCase).execute(any(HttpHeaders.class), any(SePayWebhookRequest.class));
    }

    @Test
    @DisplayName("PAY-02 [A]: POST /api/payments/webhooks/sepay with invalid Apikey returns 401")
    void processSepayWebhook_invalidApiKey_returns401() throws Exception {
        when(processSePayWebhookUseCase.execute(any(HttpHeaders.class), any(SePayWebhookRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid SePay webhook API key."));

        mockMvc.perform(post("/api/payments/webhooks/sepay")
                        .header("Authorization", "Apikey wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 92704,
                                  "transferType": "in",
                                  "transferAmount": 500000
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("PAY-02 [A]: POST /api/payments/webhooks/sepay with invalid payload returns 400 VALIDATION_FAILED")
    void processSepayWebhook_invalidPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/sepay")
                        .header("Authorization", "Apikey valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": null,
                                  "transferType": "",
                                  "transferAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(processSePayWebhookUseCase);
    }

    @Test
    @DisplayName("PAY-02 [N]: POST /api/payments/webhooks/sepay accepts SePay doc sample payload (code=null, no description)")
    void processSepayWebhook_sepayDocSamplePayload_returns200WithTopLevelSuccess() throws Exception {
        SePayWebhookResponse response = SePayWebhookResponse.builder()
                .sepayTxId(92704L)
                .outcome("UNMATCHED")
                .message("No matching payment transaction found for webhook transfer.")
                .build();
        when(processSePayWebhookUseCase.execute(any(HttpHeaders.class), any(SePayWebhookRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/webhooks/sepay")
                        .header("Authorization", "Apikey valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 92704,
                                  "gateway": "Vietcombank",
                                  "transactionDate": "2024-07-02 11:08:33",
                                  "accountNumber": "1017588888",
                                  "subAccount": "",
                                  "code": null,
                                  "content": "NGUYEN VAN A chuyen tien",
                                  "transferType": "in",
                                  "transferAmount": 10000,
                                  "accumulated": 105010000,
                                  "referenceCode": "FT24012345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sepayTxId").value(92704))
                .andExpect(jsonPath("$.data.outcome").value("UNMATCHED"));

        verify(processSePayWebhookUseCase).execute(any(HttpHeaders.class), any(SePayWebhookRequest.class));
    }

    @Test
    @DisplayName("PAY-02 [B]: POST /api/payments/webhooks/sepay accepts id=0 from SePay dashboard test ping and returns 200")
    void processSepayWebhook_zeroIdTestPing_returns200() throws Exception {
        SePayWebhookResponse response = SePayWebhookResponse.builder()
                .sepayTxId(0L)
                .outcome("IGNORED")
                .message("Synthetic SePay test webhook acknowledged without modifying payment state.")
                .build();
        when(processSePayWebhookUseCase.execute(any(HttpHeaders.class), any(SePayWebhookRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/webhooks/sepay")
                        .header("Authorization", "Apikey valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 0,
                                  "gateway": "TPBank",
                                  "transferType": "in",
                                  "transferAmount": 10000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sepayTxId").value(0))
                .andExpect(jsonPath("$.data.outcome").value("IGNORED"));

        verify(processSePayWebhookUseCase).execute(any(HttpHeaders.class), any(SePayWebhookRequest.class));
    }

    @Test
    @DisplayName("PAY-MOCK [N]: POST /api/payments/mock-success/{paymentCode} is public and returns 200 with updated session")
    void mockPaymentSuccess_validCode_returns200Ok() throws Exception {
        String code = "LUMORD102A1B2C3D4";
        PaymentSessionResponse sessionResponse = PaymentSessionResponse.builder()
                .paymentId(5001L)
                .paymentCode(code)
                .targetType(PaymentTargetType.ORDER)
                .targetId(102L)
                .gateway("SEPAY")
                .bankName("TPBank")
                .accountNumber("10006140844")
                .accountName("NGUYEN QUANG KHANH")
                .amount(new BigDecimal("250000.00"))
                .status(PaymentStatus.SUCCEEDED)
                .paidAt(Instant.parse("2026-11-13T10:00:00Z"))
                .createdAt(Instant.parse("2026-11-12T16:45:00Z"))
                .build();

        when(mockPaymentSuccessUseCase.execute(code)).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/payments/mock-success/{paymentCode}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(5001))
                .andExpect(jsonPath("$.data.paymentCode").value(code))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.targetType").value("ORDER"))
                .andExpect(jsonPath("$.data.targetId").value(102));

        verify(mockPaymentSuccessUseCase).execute(code);
    }

    @Test
    @DisplayName("PAY-MOCK [A]: POST /api/payments/mock-success/{paymentCode} when code not found returns 404")
    void mockPaymentSuccess_notFound_returns404() throws Exception {
        String code = "NONEXISTENT";
        when(mockPaymentSuccessUseCase.execute(code))
                .thenThrow(new PaymentSessionNotFoundException("Payment session not found."));

        mockMvc.perform(post("/api/payments/mock-success/{paymentCode}", code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));

        verify(mockPaymentSuccessUseCase).execute(code);
    }
}
