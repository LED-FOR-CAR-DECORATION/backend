package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.AccountNotVerifiedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RegisterAccountUseCase registerAccountUseCase;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/login returns 200 OK with ApiResponse<LoginResponse> envelope")
    void login_successReturns200WithEnvelope() {
        LoginRequest request = new LoginRequest("user@example.com", "P@ssword123");
        LoginResponse responsePayload = LoginResponse.of("access-token-123", "refresh-token-456");

        when(loginUseCase.execute(request)).thenReturn(responsePayload);

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.login(request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getData()).isEqualTo(responsePayload);
        verify(loginUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID02 [A]: POST /api/auth/login propagates InvalidCredentialsException (401)")
    void login_propagatesInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        when(loginUseCase.execute(request)).thenThrow(new InvalidCredentialsException("Invalid email or password"));

        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/login propagates AccountLockedException (423)")
    void login_propagatesAccountLockedException() {
        LoginRequest request = new LoginRequest("locked@example.com", "P@ssword123");

        when(loginUseCase.execute(request)).thenThrow(new AccountLockedException("Account is locked for 15 minutes."));

        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage("Account is locked for 15 minutes.");
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/auth/login propagates AccountNotVerifiedException (403)")
    void login_propagatesAccountNotVerifiedException() {
        LoginRequest request = new LoginRequest("pending@example.com", "P@ssword123");

        when(loginUseCase.execute(request))
                .thenThrow(new AccountNotVerifiedException("Account email is not verified. Please verify your email."));

        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(AccountNotVerifiedException.class)
                .hasMessage("Account email is not verified. Please verify your email.");
    }

    @Test
    @DisplayName("UTCID05 [N]: POST /api/auth/login normalizes uppercase/whitespace email during JSON binding")
    void login_httpRequestNormalizesEmail() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class)))
                .build();

        when(loginUseCase.execute(any(LoginRequest.class)))
                .thenReturn(LoginResponse.of("access-token-123", "refresh-token-456"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  User@Example.COM  \",\"password\":\"P@ssword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token-123"));

        verify(loginUseCase).execute(new LoginRequest("user@example.com", "P@ssword123"));
    }

    @Test
    @DisplayName("UTCID06 [A]: POST /api/auth/login returns 403 ACCOUNT_NOT_VERIFIED error envelope via GlobalExceptionHandler")
    void login_httpAccountNotVerifiedReturns403ErrorEnvelope() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class)))
                .build();

        when(loginUseCase.execute(any(LoginRequest.class)))
                .thenThrow(new AccountNotVerifiedException("Account email is not verified. Please verify your email."));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"P@ssword123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("Account email is not verified. Please verify your email."))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.details").isArray());
    }
}
