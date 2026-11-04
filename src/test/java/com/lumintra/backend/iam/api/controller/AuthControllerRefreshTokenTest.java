package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.RefreshTokenRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.RefreshTokenUseCase;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTokenTest {

    private static Validator validator;

    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;

    @InjectMocks
    private AuthController authController;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/refresh returns 200 OK with ApiResponse<LoginResponse> envelope")
    void refreshToken_successReturns200WithEnvelope() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");
        LoginResponse responsePayload = LoginResponse.of("new-access-token", "new-refresh-token");

        when(refreshTokenUseCase.execute(request)).thenReturn(responsePayload);

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.refreshToken(request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getData()).isEqualTo(responsePayload);
        verify(refreshTokenUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID02 [A]: POST /api/auth/refresh propagates InvalidCredentialsException for expired/consumed token (401)")
    void refreshToken_propagatesInvalidCredentialsException_invalidToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired.token");

        when(refreshTokenUseCase.execute(request))
                .thenThrow(new InvalidCredentialsException("Invalid or expired refresh token."));

        assertThatThrownBy(() -> authController.refreshToken(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid or expired refresh token.");
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/refresh propagates InvalidCredentialsException for inactive account (401)")
    void refreshToken_propagatesInvalidCredentialsException_inactiveAccount() {
        RefreshTokenRequest request = new RefreshTokenRequest("suspended.token");

        when(refreshTokenUseCase.execute(request))
                .thenThrow(new InvalidCredentialsException("Account is suspended or deleted."));

        assertThatThrownBy(() -> authController.refreshToken(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Account is suspended or deleted.");
    }

    @Test
    @DisplayName("UTCID04 [A]: Validation fails when RefreshTokenRequest refreshToken is blank")
    void refreshToken_validationFailsWhenTokenBlank() {
        RefreshTokenRequest request = new RefreshTokenRequest("   ");

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().equals("Refresh token is required"));
    }
}
