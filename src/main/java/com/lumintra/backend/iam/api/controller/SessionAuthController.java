package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.*;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.*;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / INVALID_REQUEST", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_TOKEN / INVALID_GOOGLE_TOKEN", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED / GOOGLE_LINK_REQUIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATA_CONFLICT: concurrent email or Google identity conflict; retry login", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: the account is temporarily locked", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GOOGLE_UNAVAILABLE / AUTH_UNAVAILABLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SessionAuthController {
    private final GoogleLoginUseCase google;
    private final TokenSessionService sessions;
    private final LogoutUseCase logout;

    @PostMapping("/google")
    @Operation(summary = "Google login", description = "Verifies a Google ID token, links an existing email or creates a Customer. Linking a non-Gmail/Workspace email requires the existing account password.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "System JWT token pair", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<LoginResponse>> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(google.execute(request, false)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the token pair", description = "No access token is required. The refresh token must be unexpired, unrevoked and the latest token for its session. Rotation does not extend the original 30-day deadline; the previous token is invalidated immediately.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New JWT token pair", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(sessions.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Log out of all devices", description = "Revokes all database sessions and refresh tokens. Blacklists the current access token in Redis until expiry. Database checks enforce revocation even during a Redis outage.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "All sessions revoked", content = @Content)
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        logout.execute(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
