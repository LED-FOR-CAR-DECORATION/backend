package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.response.FirmwareReleaseSummary;
import com.lumintra.backend.device.application.ListFirmwareReleasesUseCase;
import com.lumintra.backend.device.domain.model.FirmwareReleaseStatus;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FirmwareCatalogController {
    private final ListFirmwareReleasesUseCase useCase;

    @GetMapping("/api/management/admin/firmware")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List firmware releases by LED model and release status")
    public ApiResponse<List<FirmwareReleaseSummary>> list(
            @RequestParam(required = false) @Positive Long modelId,
            @RequestParam(required = false) FirmwareReleaseStatus status) {
        return ApiResponse.success(useCase.execute(modelId, status));
    }
}
