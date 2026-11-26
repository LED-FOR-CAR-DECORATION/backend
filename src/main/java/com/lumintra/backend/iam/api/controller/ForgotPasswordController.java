package com.lumintra.backend.iam.api.controller;
import com.lumintra.backend.iam.api.request.ForgotPasswordRequest;
import com.lumintra.backend.iam.api.response.ForgotPasswordResponse;
import com.lumintra.backend.iam.application.ForgotPasswordUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
@Tag(name = "Authentication", description = "Account authentication and recovery")
public class ForgotPasswordController {
    private final ForgotPasswordUseCase useCase;
    @Operation(summary = "Request a password reset OTP", description = "Public endpoint. Sends a six-digit code valid for five minutes if the email exists. The first three requests in 24 hours are allowed; the fourth locks requests for 24 hours. Unknown email returns the same acknowledgement and has the same request limits.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ApiResponse<ForgotPasswordResponse>: generic acknowledgement"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Invalid request payload\",\"status\":400,\"timestamp\":\"2026-11-09T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Email locked for 24 hours", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"PASSWORD_RESET_LOCKED\",\"message\":\"Password reset requests are locked for 24 hours.\",\"status\":429,\"timestamp\":\"2026-11-09T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Redis or email provider unavailable", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"PASSWORD_RESET_UNAVAILABLE\",\"message\":\"Password reset is temporarily unavailable.\",\"status\":503,\"timestamp\":\"2026-11-09T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-09T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}")))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(useCase.execute(request)));
    }
}
