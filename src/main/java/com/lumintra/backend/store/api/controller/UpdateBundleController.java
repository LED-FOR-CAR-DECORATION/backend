package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.UpdateBundleRequest;
import com.lumintra.backend.store.api.response.UpdatedBundleResponse;
import com.lumintra.backend.store.application.UpdateBundleUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UpdateBundleController {
    private final UpdateBundleUseCase useCase;

    @PutMapping("/api/management/admin/store/bundles/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a bundle product")
    public ApiResponse<UpdatedBundleResponse> update(
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody UpdateBundleRequest request) {
        return ApiResponse.success(useCase.execute(id, request));
    }
}
