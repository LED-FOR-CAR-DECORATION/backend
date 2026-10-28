package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterUseCase;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import org.springframework.http.HttpStatus;
import com.lumintra.backend.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Customer registration and login with email and password.")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;

    @PostMapping("/register")
    @Operation(summary = "Register a customer account", description = "Public API. Creates a pending Customer account and profile, then sends a six-digit email OTP. "
            + "The request has no fullName field. Confirm the email through POST /api/auth/verify-email before signing in.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created successfully", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"data":{"userId":123,"email":"customer@example.com","status":"PENDING_VERIFICATION"}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or the JSON payload is invalid",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "Validation", value = """
                                    {"code":"VALIDATION_FAILED","message":"Please check the submitted fields.","status":400,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[{"field":"confirmPassword","message":"The password confirmation does not match."}]}
                                    """),
                            @ExampleObject(name = "InvalidJson", value = """
                                    {"code":"INVALID_REQUEST","message":"The request payload is invalid.","status":400,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[]}
                                    """) })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered or a database constraint conflict",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "DuplicateEmail", value = """
                                    {"code":"EMAIL_ALREADY_REGISTERED","message":"This email address is already registered.","status":409,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[{"field":"email","message":"This email address is already registered."}]}
                                    """),
                            @ExampleObject(name = "ConcurrentConflict", value = """
                                    {"code":"DATA_CONFLICT","message":"The data already exists or is no longer valid. Please check your request.","status":409,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[]}
                                    """) })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(registerUseCase.execute(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Public API. Returns an access token valid for 900 seconds and a refresh token. "
            + "Five failed password attempts lock the account for 15 minutes.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INVALID_REQUEST", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: incorrect email or password", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: the account is temporarily locked", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
