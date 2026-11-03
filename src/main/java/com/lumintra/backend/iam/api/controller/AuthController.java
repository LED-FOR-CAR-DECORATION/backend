package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Endpoints for user authentication and account registration")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterAccountUseCase registerAccountUseCase;

    @Operation(
            summary = "Authenticate user credentials",
            description = "Authenticates user credentials using email and password. Upon success, returns access and refresh tokens. Failed attempts are tracked (lockout occurs after 5 failed attempts)."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User authenticated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: request payload contains missing or invalid fields",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Error",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request payload",
                                              "status": 400,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "email",
                                                  "message": "Invalid email format"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: Invalid email or password",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Invalid Credentials",
                                    value = """
                                            {
                                              "code": "INVALID_CREDENTIALS",
                                              "message": "Invalid email or password",
                                              "status": 401,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "423",
                    description = "Locked: Account is locked for 15 minutes due to consecutive failed attempts",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Account Locked",
                                    value = """
                                            {
                                              "code": "ACCOUNT_LOCKED",
                                              "message": "Account is locked for 15 minutes.",
                                              "status": 423,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error: Unexpected system failure",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Internal Error",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Register a new customer account",
            description = "Registers a new customer account using email and password. Upon successful registration, the account is created in PENDING_VERIFICATION status and a verification OTP is dispatched to the user's email address."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Customer account registered successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: request payload contains invalid fields or passwords do not match",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Error",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request payload",
                                              "status": 400,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "confirmPassword",
                                                  "message": "Passwords do not match"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict: The email address is already registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Email Conflict",
                                    value = """
                                            {
                                              "code": "EMAIL_ALREADY_REGISTERED",
                                              "message": "Email is already registered",
                                              "status": 409,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error: Unexpected system failure",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Internal Error",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-10-29T07:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registerAccountUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}