package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.UpdateManagedUserRequest;
import com.lumintra.backend.iam.application.UpdateManagedUserUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Internal account management")
public class UpdateManagedUserController {
    private final UpdateManagedUserUseCase useCase;

    @PutMapping("/api/management/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update an internal user",
            description =
                    "Update profile fields, one assigned role or manual lock status. Changes revoke"
                        + " the user's sessions and are audited after commit.")
    @ApiResponse(responseCode = "204", description = "Update completed", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "Invalid ID, fields or role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller or target is restricted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Target account or profile not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected application failure",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> update(
            Authentication authentication,
            @Positive @PathVariable("id") long id,
            @Valid @RequestBody UpdateManagedUserRequest request) {
        useCase.execute(authentication.getName(), id, request);
        return ResponseEntity.noContent().build();
    }
}
