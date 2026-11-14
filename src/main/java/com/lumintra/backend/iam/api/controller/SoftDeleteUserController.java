package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.SoftDeleteUserRequest;
import com.lumintra.backend.iam.application.SoftDeleteUserUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Account management")
public class SoftDeleteUserController {
    private final SoftDeleteUserUseCase useCase;

    @DeleteMapping("/api/management/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Soft delete an account",
            description =
                    "Sets the account to DELETED and audits the supplied reason code after commit."
                            + " The caller and default administrator are protected. Repeating a"
                            + " completed deletion returns 204.")
    @ApiResponse(responseCode = "204", description = "Account soft deleted", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "Invalid account ID or reason",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller not authorized or account protected",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody SoftDeleteUserRequest request) {
        useCase.execute(authentication.getName(), id, request.reason());
        return ResponseEntity.noContent().build();
    }
}
