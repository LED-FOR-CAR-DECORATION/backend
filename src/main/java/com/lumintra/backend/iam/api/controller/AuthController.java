package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.request.ResendVerificationRequest;
import com.lumintra.backend.iam.api.request.VerifyEmailRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.application.*;
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
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ResendVerificationEmailUseCase resendVerificationEmailUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;

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

    @Operation(
            summary = "Verify customer email with OTP",
            description = "Validates the 6-digit OTP code sent to customer's email during registration. Upon successful validation, transitions customer account status from PENDING_VERIFICATION to ACTIVE."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email verified successfully; account is now ACTIVE",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Success",
                                    value = """
                                            {
                                              "data": "Email verified successfully"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: request payload contains invalid fields, invalid/expired OTP, or account already verified",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Validation Error",
                                            value = """
                                                    {
                                                      "code": "VALIDATION_FAILED",
                                                      "message": "Invalid request payload",
                                                      "status": 400,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": [
                                                        {
                                                          "field": "otp",
                                                          "message": "OTP must be exactly 6 digits"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invalid OTP",
                                            value = """
                                                    {
                                                      "code": "INVALID_OTP",
                                                      "message": "Invalid or expired OTP.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Account Already Verified",
                                            value = """
                                                    {
                                                      "code": "ACCOUNT_ALREADY_VERIFIED",
                                                      "message": "Account is already verified",
                                                      "status": 400,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User account not found for provided email",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "User Not Found",
                                    value = """
                                            {
                                              "code": "USER_NOT_FOUND",
                                              "message": "Account not found",
                                              "status": 404,
                                              "timestamp": "2026-11-04T12:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many failed OTP attempts: maximum retry threshold exceeded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Max Attempts Reached",
                                    value = """
                                            {
                                              "code": "MAX_OTP_ATTEMPTS_REACHED",
                                              "message": "Maximum OTP attempts reached. Please request a new code.",
                                              "status": 429,
                                              "timestamp": "2026-11-04T12:00:00Z",
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
                                              "timestamp": "2026-11-04T12:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully"));
    }

    @Operation(
            summary = "Resend verification email",
            description = "Re-generates and emails a new 6-digit verification OTP to a customer in PENDING_VERIFICATION status. Enforces a 60-second cooldown and a maximum of 3 resends per 24 hours."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "Verification email request accepted for delivery"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: invalid request payload or account already verified",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Validation Error",
                                            value = """
                                                    {
                                                      "code": "VALIDATION_FAILED",
                                                      "message": "Invalid request payload",
                                                      "status": 400,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": [
                                                        {
                                                          "field": "email",
                                                          "message": "Invalid email format"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Account Already Verified",
                                            value = """
                                                    {
                                                      "code": "ACCOUNT_ALREADY_VERIFIED",
                                                      "message": "Account is already verified.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User account not found for provided email",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "User Not Found",
                                    value = """
                                            {
                                              "code": "USER_NOT_FOUND",
                                              "message": "Account not found",
                                              "status": 404,
                                              "timestamp": "2026-11-04T12:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Rate limit exceeded: cooldown period active (60s) or maximum resend quota reached (3 attempts)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Cooldown Active",
                                            value = """
                                                    {
                                                      "code": "RESEND_COOLDOWN_ACTIVE",
                                                      "message": "Please wait 60 seconds before requesting another email.",
                                                      "status": 429,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Max Attempts Reached",
                                            value = """
                                                    {
                                                      "code": "MAX_RESEND_ATTEMPTS_REACHED",
                                                      "message": "Maximum resend attempts reached for this email.",
                                                      "status": 429,
                                                      "timestamp": "2026-11-04T12:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    )
                            }
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
                                              "timestamp": "2026-11-04T12:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationEmailUseCase.execute(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(@Valid @RequestBody com.lumintra.backend.iam.api.request.GoogleLoginRequest request) {
        LoginResponse response = googleLoginUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}