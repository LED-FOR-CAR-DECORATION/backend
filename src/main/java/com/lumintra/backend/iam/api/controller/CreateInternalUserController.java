package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.CreateInternalUserRequest;
import com.lumintra.backend.iam.api.response.CreateInternalUserResponse;
import com.lumintra.backend.iam.application.CreateInternalUserUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Internal account management")
@SecurityScheme(
        name = "internalUserBearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class CreateInternalUserController {
    private final CreateInternalUserUseCase useCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "internalUserBearerAuth")
    @Operation(
            summary = "Create an internal user",
            description =
                    "Current active ADMIN only. Creates an ACTIVE EMPLOYEE with one STAFF, MANAGER"
                        + " or DESIGNER role. Generates a random password; only its BCrypt hash is"
                        + " stored. Credentials email is sent asynchronously after commit with"
                        + " bounded retries. Email failure does not roll back the created account."
                        + " Creation audit is logged after commit. Password is never returned.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Internal account created; credentials email dispatched after commit",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "data": {
                                                            "accountId": 42,
                                                            "email": "staff@example.com",
                                                            "name": "Nguyen Van An",
                                                            "role": "STAFF",
                                                            "status": "ACTIVE"
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid email, name, role or JSON",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "VALIDATION_FAILED",
                                                          "message": "Invalid email, name, role or JSON",
                                                          "status": 400,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Authentication required or caller account missing",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "UNAUTHORIZED",
                                                          "message": "Authentication required or caller account missing",
                                                          "status": 401,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Current active ADMIN access required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "FORBIDDEN",
                                                          "message": "Current active ADMIN access required",
                                                          "status": 403,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Email already registered",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "EMAIL_ALREADY_REGISTERED",
                                                          "message": "Email already registered",
                                                          "status": 409,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "Unexpected infrastructure or missing configured role",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "INTERNAL_SERVER_ERROR",
                                                          "message": "Unexpected infrastructure or missing configured role",
                                                          "status": 500,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
    })
    public ResponseEntity<ApiResponse<CreateInternalUserResponse>> create(
            Authentication authentication, @Valid @RequestBody CreateInternalUserRequest request) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken)
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(useCase.execute(authentication.getName(), request)));
    }
}
