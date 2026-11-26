package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
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
class AuthControllerRegisterTest {

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RegisterAccountUseCase registerAccountUseCase;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("POST /api/auth/register returns 201 CREATED with ApiResponse<RegisterResponse> envelope")
    void register_returns201CreatedWithEnvelope() {
        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "P@ssword123",
                "P@ssword123"
        );
        RegisterResponse expectedPayload = RegisterResponse.builder()
                .id(101L)
                .email("customer@example.com")
                .accountType("CUSTOMER")
                .status("PENDING_VERIFICATION")
                .build();

        when(registerAccountUseCase.execute(request)).thenReturn(expectedPayload);

        ResponseEntity<ApiResponse<RegisterResponse>> responseEntity = authController.register(request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getData()).isEqualTo(expectedPayload);
        verify(registerAccountUseCase).execute(request);
    }

    @Test
    @DisplayName("POST /api/auth/register propagates EmailAlreadyRegisteredException to GlobalExceptionHandler")
    void register_propagatesEmailAlreadyRegisteredException() {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com",
                "P@ssword123",
                "P@ssword123"
        );

        when(registerAccountUseCase.execute(request))
                .thenThrow(new EmailAlreadyRegisteredException("Email is already registered"));

        assertThatThrownBy(() -> authController.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered");
    }
}
