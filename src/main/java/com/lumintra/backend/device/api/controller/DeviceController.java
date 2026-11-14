package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Customer Devices",
        description = "Customer endpoints for claiming and managing registered LED hardware devices"
)
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final ClaimDeviceUseCase claimDeviceUseCase;

    @Operation(
            summary = "Claim an LED device via factory QR code",
            description = "Verifies the cryptographic HMAC-SHA256 signature embedded in the scanned factory QR payload, "
                    + "confirms the device is not already bound, verifies that the physical unit was fulfilled for a "
                    + "DELIVERED or COMPLETED order owned by the authenticated customer, and atomically binds the device "
                    + "to the customer's account while activating its 180-day warranty.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Device claimed and warranty activated successfully",
                    content = @Content(schema = @Schema(implementation = ClaimDeviceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or invalid/tampered QR code signature (INVALID_DEVICE_QR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized — missing, expired, or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden — requires CUSTOMER role, or device does not belong to an order owned by this customer (DEVICE_OWNERSHIP_MISMATCH)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "LED hardware instance not found (LED_INSTANCE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict — device is already bound (DEVICE_ALREADY_BOUND) or the associated order is not yet DELIVERED/COMPLETED (ORDER_NOT_DELIVERED_YET)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/claim")
    public ResponseEntity<ClaimDeviceResponse> claimDevice(
            @Valid @RequestBody ClaimDeviceRequest request,
            Authentication authentication) {
        Long customerId = extractCustomerId(authentication);
        ClaimDeviceResponse response = claimDeviceUseCase.execute(customerId, request);
        return ResponseEntity.ok(response);
    }

    private Long extractCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new InvalidCredentialsException("Invalid authentication principal");
        }
        try {
            long id = Long.parseLong(name.trim());
            if (id <= 0) {
                throw new InvalidCredentialsException("Invalid customer ID in token");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid customer ID in token");
        }
    }
}
