package com.lumintra.backend.iam.api.controller;
import com.lumintra.backend.iam.application.ForgotPasswordUseCase;
import com.lumintra.backend.iam.api.response.ForgotPasswordResponse;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.*;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ForgotPasswordControllerTest {
    ForgotPasswordUseCase useCase; MockMvc mvc;
    @BeforeEach void setup() {
        useCase = mock(ForgotPasswordUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new ForgotPasswordController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class))).build();
    }
    @Test @DisplayName("N: HTTP 200 returns the standard data envelope") void success() throws Exception {
        when(useCase.execute(any())).thenReturn(new ForgotPasswordResponse("If the email is registered, a password reset code has been sent."));
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"User@Example.com\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").exists());
        verify(useCase).execute(new com.lumintra.backend.iam.api.request.ForgotPasswordRequest("user@example.com"));
    }
    @Test @DisplayName("A: Blank, missing and malformed email return 400 before the use case") void invalid() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":null}", "{\"email\":\"   \"}", "{\"email\":\"not-an-email\"}"}) {
            mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }
    @Test @DisplayName("B: Email longer than 254 characters is rejected") void tooLong() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(useCase);
    }
    @Test @DisplayName("B: Valid email with exactly 254 characters is accepted") void maximumLength() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        when(useCase.execute(any())).thenReturn(new ForgotPasswordResponse("If the email is registered, a password reset code has been sent."));
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}"))
            .andExpect(status().isOk());
    }
    @Test @DisplayName("A: Malformed JSON and missing body return safe 400") void malformedBody() throws Exception {
        for (String body : new String[]{"", "{", "null"}) {
            mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }
    @Test @DisplayName("B: Reset lock returns 429 with a consistent error envelope") void locked() throws Exception {
        when(useCase.execute(any())).thenThrow(new PasswordResetLockedException());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_LOCKED")).andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.traceId").exists()).andExpect(jsonPath("$.details").isArray());
    }
    @Test @DisplayName("A: Infrastructure failure returns safe 503") void unavailable() throws Exception {
        when(useCase.execute(any())).thenThrow(new PasswordResetUnavailableException(new RuntimeException("secret")));
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Password reset is temporarily unavailable."));
    }
}
