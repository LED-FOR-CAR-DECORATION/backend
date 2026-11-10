package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.UpdateCurrentProfileRequest;
import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.application.UpdateCurrentProfileUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Authenticated account profile")
public class UpdateUserProfileController {
    private final UpdateCurrentProfileUseCase useCase;

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Update the current user's profile",
            description =
                    "Replace name, phone and avatar for the authenticated account. Null or blank"
                        + " phone/avatar clears that profile value. Account email, credentials and"
                        + " roles cannot be changed.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Updated profile inside ApiResponse<CurrentProfileResponse>"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid name, phone, avatar or JSON",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "VALIDATION_FAILED",
                                                          "message": "Invalid request payload",
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
                description = "Inactive account",
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
                responseCode = "404",
                description = "Account or profile missing",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "USER_NOT_FOUND",
                                                          "message": "Account not found.",
                                                          "status": 404,
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
    public ResponseEntity<ApiResponse<CurrentProfileResponse>> update(
            Authentication authentication,
            @Valid @RequestBody UpdateCurrentProfileRequest request) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return ResponseEntity.ok(
                ApiResponse.success(useCase.execute(authentication.getName(), request)));
    }
}
