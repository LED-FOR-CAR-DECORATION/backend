package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.response.ManagedBundleResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.repository.ManagedBundleRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManagedBundleController {
    private final ManagedBundleRepository bundles;

    @GetMapping("/api/management/admin/store/bundles")
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List store bundles",
            description =
                    "Returns a page of bundles with an optional status filter and the number of"
                        + " included item entries.")
    public ApiResponse<List<ManagedBundleResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ProductStatus status) {
        var results =
                bundles.list(status == null ? null : status.name(), PageRequest.of(page, size));
        return ApiResponse.success(results.getContent(), PaginationMeta.from(results));
    }
}
