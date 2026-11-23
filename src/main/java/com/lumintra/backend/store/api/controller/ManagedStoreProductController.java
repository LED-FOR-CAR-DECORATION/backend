package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.CreateStoreProductRequest;
import com.lumintra.backend.store.api.response.CreatedStoreProductResponse;
import com.lumintra.backend.store.api.response.ManagedStoreProductResponse;
import com.lumintra.backend.store.application.CreateStoreProductUseCase;
import com.lumintra.backend.store.application.ListManagedStoreProductsUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Store Product Management")
public class ManagedStoreProductController {
    private final CreateStoreProductUseCase useCase;
    private final ListManagedStoreProductsUseCase listUseCase;

    @GetMapping("/api/management/admin/store/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List managed store products across all statuses",
            description = "Returns paginated store products including UNPUBLISHED, PUBLISHED, ARCHIVED, and DELETED with optional productType and status filters.")
    public ResponseEntity<ApiResponse<List<ManagedStoreProductResponse.Item>>> list(
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        ManagedStoreProductResponse response = listUseCase.execute(productType, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(response.items(), response.meta()));
    }

    @PostMapping("/api/management/admin/store/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an unpublished store product")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Product created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "ADMIN or MANAGER required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Tag or character version missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Character version already linked",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<CreatedStoreProductResponse>> create(
            @Valid @RequestBody CreateStoreProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(useCase.execute(request)));
    }
}
