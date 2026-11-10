package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import com.lumintra.backend.iam.api.response.ResetPasswordResponse;
import com.lumintra.backend.iam.application.ResetPasswordUseCase;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.*;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResetPasswordControllerTest {

    private ResetPasswordUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        useCase = mock(ResetPasswordUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new ResetPasswordController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class)))
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: HTTP 200 returns standard data envelope for valid reset request")
    void successWithoutConfirmPassword() throws Exception {
        when(useCase.execute(any())).thenReturn(new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@Example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Password has been reset successfully. Please log in with your new password."));

        verify(useCase).execute(new ResetPasswordRequest("user@example.com", "123456", "P@ssword123", null));
    }

    @Test
    @DisplayName("UTCID02 [N]: Valid payload with matching optional confirmPassword succeeds")
    void successWithMatchingConfirmPassword() throws Exception {
        when(useCase.execute(any())).thenReturn(new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@Example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\",\"confirmPassword\":\"P@ssword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());

        verify(useCase).execute(new ResetPasswordRequest("user@example.com", "123456", "P@ssword123", "P@ssword123"));
    }

    @Test
    @DisplayName("UTCID03 [A]: Missing, blank, or malformed email returns 400 before use case")
    void invalidEmail() throws Exception {
        for (String body : new String[]{
                "{\"email\":\"\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}",
                "{\"email\":null,\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}",
                "{\"email\":\"not-an-email\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"
        }) {
            mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID04 [B]: Valid email with exactly 254 characters is accepted")
    void maximumEmailLength() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        when(useCase.execute(any())).thenReturn(new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("UTCID05 [B]: Email exceeding 254 characters is rejected")
    void emailTooLong() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID06 [A]: Malformed or non-6-digit OTP returns 400 before use case")
    void invalidOtpFormat() throws Exception {
        for (String otp : new String[]{"", "12345", "1234567", "abcdef", "12a456"}) {
            mvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"otp\":\"" + otp + "\",\"newPassword\":\"P@ssword123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID07 [A]: Weak password missing uppercase, digit, or special char returns 400")
    void weakPassword() throws Exception {
        for (String pwd : new String[]{"alllowercase1!", "ALLUPPERCASE1!", "NoSpecialChar12", "NoDigits@Word"}) {
            mvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"" + pwd + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID08 [B]: Password with boundary length exactly 8 chars is accepted")
    void passwordBoundary8Chars() throws Exception {
        when(useCase.execute(any())).thenReturn(new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssw0rd\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("UTCID09 [B]: Password with boundary length exactly 72 chars is accepted")
    void passwordBoundary72Chars() throws Exception {
        String pwd = "A1@" + "a".repeat(69);
        when(useCase.execute(any())).thenReturn(new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"" + pwd + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("UTCID10 [B]: Password shorter than 8 characters is rejected")
    void passwordTooShort() throws Exception {
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ss1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID11 [B]: Password longer than 72 characters is rejected")
    void passwordTooLong() throws Exception {
        String pwd = "A1@" + "a".repeat(70);
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"" + pwd + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID12 [A]: Mismatched confirmPassword returns 400 before use case")
    void confirmPasswordMismatch() throws Exception {
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\",\"confirmPassword\":\"Different@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("UTCID13 [A]: Invalid or expired OTP returns 400 INVALID_OTP")
    void invalidOtpFromUseCase() throws Exception {
        when(useCase.execute(any())).thenThrow(new InvalidOtpException("Invalid or expired OTP."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"999999\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"))
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP."));
    }

    @Test
    @DisplayName("UTCID14 [A]: User account not found returns 404 USER_NOT_FOUND")
    void userNotFound() throws Exception {
        when(useCase.execute(any())).thenThrow(new UserNotFoundException("User not found with email: missing@example.com"));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with email: missing@example.com"));
    }

    @Test
    @DisplayName("UTCID15 [A]: Max OTP attempts reached returns 429 MAX_OTP_ATTEMPTS_REACHED")
    void maxOtpAttemptsReached() throws Exception {
        when(useCase.execute(any())).thenThrow(new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code."));

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MAX_OTP_ATTEMPTS_REACHED"))
                .andExpect(jsonPath("$.message").value("Maximum OTP attempts reached. Please request a new code."));
    }

    @Test
    @DisplayName("UTCID16 [A]: Redis or persistence failure returns safe 503")
    void serviceUnavailable() throws Exception {
        when(useCase.execute(any())).thenThrow(new PasswordResetUnavailableException());

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\",\"newPassword\":\"P@ssword123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Password reset is temporarily unavailable."));
    }

    @Test
    @DisplayName("UTCID17 [A]: Malformed JSON and empty body return safe 400")
    void malformedPayload() throws Exception {
        for (String body : new String[]{"", "{", "null"}) {
            mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }
}
