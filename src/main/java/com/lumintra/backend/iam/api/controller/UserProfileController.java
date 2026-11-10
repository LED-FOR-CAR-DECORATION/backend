package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.application.GetCurrentProfileUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
@Tag(name = "User Profile", description = "Authenticated account profile")
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "bearerAuth", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class UserProfileController {
    private final GetCurrentProfileUseCase useCase;
    @GetMapping("/me") @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current user's profile", description = "Identity comes from the authenticated JWT subject. Returns current database profile and roles; credentials and security fields are excluded.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ApiResponse<CurrentProfileResponse>"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid authentication", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication is required.\",\"status\":401,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Inactive account", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"FORBIDDEN\",\"message\":\"Access is denied.\",\"status\":403,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account or profile missing", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"USER_NOT_FOUND\",\"message\":\"Account not found.\",\"status\":404,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-10T12:00:00Z\",\"traceId\":\"trace-123\",\"details\":[]}")))
    })
    public ResponseEntity<ApiResponse<CurrentProfileResponse>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return ResponseEntity.ok(ApiResponse.success(useCase.execute(authentication.getName())));
    }
}
