package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
