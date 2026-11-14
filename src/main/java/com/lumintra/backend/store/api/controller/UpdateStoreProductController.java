package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.UpdateStoreProductRequest;
import com.lumintra.backend.store.api.response.UpdatedStoreProductResponse;
import com.lumintra.backend.store.application.UpdateStoreProductUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Store Product Management")
public class UpdateStoreProductController {
    private final UpdateStoreProductUseCase useCase;

    @PutMapping("/api/management/admin/store/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update an existing store product",
            description =
                    "Updates product details and optional tag associations. Historical order prices"
                        + " are preserved.")
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
            description = "Product or tag not found")
    public ApiResponse<UpdatedStoreProductResponse> update(
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody UpdateStoreProductRequest request) {
        return ApiResponse.success(useCase.execute(id, request));
    }
}
