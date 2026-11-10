package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import com.lumintra.backend.iam.api.response.ResetPasswordResponse;
import com.lumintra.backend.iam.application.ResetPasswordUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Account authentication and recovery")
public class ResetPasswordController {

    private final ResetPasswordUseCase useCase;

    @Operation(
            summary = "Reset password using OTP",
            description = "Public endpoint. Validates 6-digit OTP code against Redis, updates and hashes the new password, revokes all existing sessions/refresh tokens across all devices, and logs an audit event if internal staff."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "ApiResponse<ResetPasswordResponse>: password reset successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: request payload contains missing/invalid fields, weak password, or invalid/expired OTP",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Invalid OTP",
                                            value = "{\"code\":\"INVALID_OTP\",\"message\":\"Invalid or expired OTP.\",\"status\":400,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"
                                    ),
                                    @ExampleObject(
                                            name = "Validation Failed",
                                            value = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Invalid request payload\",\"status\":400,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[{\"field\":\"newPassword\",\"message\":\"Password must be 8-72 characters long, contain at least one uppercase letter, one lowercase letter, one digit, and one special character, with no whitespace\"}]}"
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User account not found with the provided email address",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"code\":\"USER_NOT_FOUND\",\"message\":\"User not found with email: user@example.com\",\"status\":404,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Maximum OTP verification attempts reached: token invalidated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"code\":\"MAX_OTP_ATTEMPTS_REACHED\",\"message\":\"Maximum OTP attempts reached. Please request a new code.\",\"status\":429,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Redis or persistence store unavailable",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"code\":\"PASSWORD_RESET_UNAVAILABLE\",\"message\":\"Password reset is temporarily unavailable.\",\"status\":503,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"
                            )
                    )
            )
    })
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(useCase.execute(request)));
    }
}
