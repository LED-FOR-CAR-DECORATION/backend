package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.response.ManagedUserDetailResponse;
import com.lumintra.backend.iam.application.GetManagedUserDetailUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Internal account management")
@SecurityScheme(
        name = "managedUserDetailBearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class ManagedUserDetailController {
    private final GetManagedUserDetailUseCase useCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "managedUserDetailBearerAuth")
    @Operation(
            summary = "View an internal user's detail",
            description =
                    "Active Admin/Manager only. Returns profile, assigned role, account status and"
                        + " password-login lockout state. CUSTOMER accounts/roles are forbidden."
                        + " Successful views of other users publish an audit event, logged after"
                        + " transaction commit; self views do not.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "ApiResponse<ManagedUserDetailResponse>"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Malformed, nonpositive or overflowing account ID",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "VALIDATION_FAILED",
                                                          "message": "Invalid request parameter",
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
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "UNAUTHORIZED",
                                                          "message": "Authentication is required.",
                                                          "status": 401,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden caller or CUSTOMER target",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "FORBIDDEN",
                                                          "message": "Access is denied.",
                                                          "status": 403,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target account or employee profile missing",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "USER_NOT_FOUND",
                                                          "message": "User not found.",
                                                          "status": 404,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "Unexpected infrastructure or profile integrity failure",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = """
                                                        {
                                                          "code": "INTERNAL_SERVER_ERROR",
                                                          "message": "An unexpected error occurred",
                                                          "status": 500,
                                                          "timestamp": "2026-11-10T12:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
    })
    public ResponseEntity<ApiResponse<ManagedUserDetailResponse>> detail(
            Authentication authentication,
            @Parameter(
                            description = "Target account ID, greater than zero",
                            example = "42",
                            required = true)
                    @Positive(message = "id must be greater than zero")
                    @PathVariable("id")
                    long id) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return ResponseEntity.ok(
                ApiResponse.success(useCase.execute(authentication.getName(), id)));
    }
}
