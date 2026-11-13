package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.response.RegisteredDeviceResponse;
import com.lumintra.backend.device.application.ListRegisteredDevicesUseCase;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RegisteredDeviceController {
    private final ListRegisteredDevicesUseCase useCase;

    @GetMapping("/api/devices")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the authenticated customer's registered LED devices")
    public ApiResponse<List<RegisteredDeviceResponse>> listDevices(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (userId <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        return ApiResponse.success(useCase.execute(userId));
    }
}
