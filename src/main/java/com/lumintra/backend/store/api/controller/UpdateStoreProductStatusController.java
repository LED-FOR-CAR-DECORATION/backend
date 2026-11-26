package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.UpdateStoreProductStatusRequest;
import com.lumintra.backend.store.api.response.UpdatedStoreProductStatusResponse;
import com.lumintra.backend.store.application.UpdateStoreProductStatusUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Store Product Management")
public class UpdateStoreProductStatusController {
    private final UpdateStoreProductStatusUseCase useCase;

    @PatchMapping("/api/management/admin/store/products/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update store product status",
            description = "Publishes, unpublishes, or archives an existing store product.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Updated product")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid ID or payload")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "ADMIN or MANAGER required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product is missing or deleted")
    public ApiResponse<UpdatedStoreProductStatusResponse> update(
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody UpdateStoreProductStatusRequest request) {
        return ApiResponse.success(useCase.execute(id, request));
    }
}
