package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.response.FirmwareUpdateResponse;
import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Firmware OTA")
public class FirmwareUpdateController {
    private final CheckFirmwareUpdateUseCase useCase;

    @GetMapping("/api/devices/{id}/ota/check")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Check for a newer compatible firmware release",
            description =
                    "Checks the active release for the owned device's hardware revision. Download"
                            + " and installation require Wi-Fi.")
    public ApiResponse<FirmwareUpdateResponse> check(
            Authentication authentication,
            @PathVariable("id") @Size(min = 1, max = 12) String id,
            @RequestParam(value = "currentVersion", defaultValue = "")
                    @Parameter(example = "1.0.0", required = true)
                    @Pattern(regexp = CheckFirmwareUpdateUseCase.VERSION_PATTERN)
                    String currentVersion) {
        return ApiResponse.success(useCase.execute(authentication.getName(), id, currentVersion));
    }
}
