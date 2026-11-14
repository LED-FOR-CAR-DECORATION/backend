package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.ManagedUserFilterRequest;
import com.lumintra.backend.iam.api.response.ManagedUserListResponse;
import com.lumintra.backend.iam.application.ListManagedUsersUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Internal account management")
public class ManagedUserController {
    private final ListManagedUsersUseCase useCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List internal users",
            description =
                    "Admin/manager only. Combines role, active status and email/name search"
                        + " filters. Excludes customer accounts and CUSTOMER roles in the database"
                        + " query before pagination. Results use stable accountId descending"
                        + " order.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "ApiResponse<ManagedUserListResponse>, containing items and meta"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid filters or pagination",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
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
                description = "Authentication required",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
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
                description = "Caller is not an active admin/manager",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
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
                responseCode = "500",
                description = "Unexpected storage failure",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred",
  "status": 500,
  "timestamp": "2026-11-10T12:00:00Z",
  "traceId": "trace-123",
  "details": []
}
""")))
    })
    public ResponseEntity<ApiResponse<ManagedUserListResponse>> list(
            Authentication authentication, @Valid @ModelAttribute ManagedUserFilterRequest filter) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return ResponseEntity.ok(
                ApiResponse.success(useCase.execute(authentication.getName(), filter)));
    }
}
