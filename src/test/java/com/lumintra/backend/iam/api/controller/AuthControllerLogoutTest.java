package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LogoutRequest;
import com.lumintra.backend.iam.application.LogoutUseCase;
import jakarta.servlet.http.HttpServletRequest;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutTest {

    private static Validator validator;

    @Mock
    private LogoutUseCase logoutUseCase;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthController authController;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/logout with valid Bearer token returns 204 No Content")
    void logout_successReturns204NoContent() {
        LogoutRequest request = new LogoutRequest("valid.refresh.token");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer sample.access.token");

        ResponseEntity<Void> response = authController.logout(request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(logoutUseCase).execute("sample.access.token", request);
    }

    @Test
    @DisplayName("UTCID02 [N]: POST /api/auth/logout with missing or non-Bearer Authorization header returns 204 without executing use case")
    void logout_missingOrMalformedAuthorizationHeaderReturns204() {
        LogoutRequest request = new LogoutRequest("valid.refresh.token");
        when(httpRequest.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Void> response = authController.logout(request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(logoutUseCase, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/logout propagates unexpected exception from use case")
    void logout_propagatesUnexpectedExceptionFromUseCase() {
        LogoutRequest request = new LogoutRequest("valid.refresh.token");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer sample.access.token");
        doThrow(new RuntimeException("Unexpected error during logout"))
                .when(logoutUseCase).execute("sample.access.token", request);

        assertThatThrownBy(() -> authController.logout(request, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error during logout");
    }

    @Test
    @DisplayName("UTCID04 [A]: Validation fails when LogoutRequest refreshToken is blank")
    void logout_validationFailsWhenRefreshTokenIsBlank() {
        LogoutRequest request = new LogoutRequest("   ");

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().equals("Refresh token is required"));
    }
}
