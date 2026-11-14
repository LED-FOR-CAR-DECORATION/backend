package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.store.application.DeleteBundleUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Store Product Management")
public class DeleteBundleController {
    private final DeleteBundleUseCase useCase;

    @DeleteMapping("/api/management/admin/store/bundles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Archive or soft-delete a bundle")
    @ApiResponse(responseCode = "204", description = "Bundle removed from the catalog")
    @ApiResponse(responseCode = "400", description = "Invalid bundle ID")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "MANAGER required")
    @ApiResponse(responseCode = "404", description = "BUNDLE_NOT_FOUND")
    public void delete(@PathVariable("id") @Positive long id) {
        useCase.execute(id);
    }
}
