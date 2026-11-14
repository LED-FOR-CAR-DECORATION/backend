package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.CreateBundleRequest;
import com.lumintra.backend.store.api.response.CreatedBundleResponse;
import com.lumintra.backend.store.application.CreateBundleUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CreateBundleController {
    private final CreateBundleUseCase useCase;

    @PostMapping("/api/management/admin/store/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an unpublished bundle product")
    public ApiResponse<CreatedBundleResponse> create(
            @Valid @RequestBody CreateBundleRequest request) {
        return ApiResponse.success(useCase.execute(request));
    }
}
