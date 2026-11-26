package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.response.ManagedBundleDetailResponse;
import com.lumintra.backend.store.application.GetManagedBundleDetailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class GetManagedBundleDetailController {

    private final GetManagedBundleDetailUseCase getManagedBundleDetailUseCase;

    @GetMapping("/api/management/admin/store/bundles/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get managed bundle detail",
            description = "Returns full bundle metadata and resolved LED/Character items for store management."
    )
    public ApiResponse<ManagedBundleDetailResponse> getDetail(
            @PathVariable @Positive(message = "id must be greater than 0") Long id) {
        return ApiResponse.success(getManagedBundleDetailUseCase.execute(id));
    }
}
