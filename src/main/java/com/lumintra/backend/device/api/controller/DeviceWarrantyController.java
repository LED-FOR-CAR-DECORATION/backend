package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.SubmitWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.CustomerDeviceWarrantyResponse;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.application.GetCustomerDeviceWarrantyUseCase;
import com.lumintra.backend.device.application.SubmitWarrantyClaimUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Customer Device Warranty",
        description = "Endpoints for customer hardware warranty claims and status inspection"
)
@Validated
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceWarrantyController {

    private final SubmitWarrantyClaimUseCase submitWarrantyClaimUseCase;
    private final GetCustomerDeviceWarrantyUseCase getCustomerDeviceWarrantyUseCase;

    @Operation(
            summary = "View device warranty coverage and claim history",
            description = "Retrieves active warranty coverage, remaining warranty days, and full history of warranty claims for an LED device owned by the authenticated customer.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Warranty coverage and claims retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerDeviceWarrantyResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid device ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized — missing, expired, or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden — non-customer role or caller does not own the device",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}/warranty")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerDeviceWarrantyResponse>> getDeviceWarranty(
            Authentication authentication,
            @Parameter(description = "Device instance ID", required = true)
            @PathVariable("id") @Positive Long id
    ) {
        Long customerId = resolveCustomerId(authentication);
        CustomerDeviceWarrantyResponse response = getCustomerDeviceWarrantyUseCase.execute(customerId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Submit a hardware warranty claim",
            description = "Submits a new warranty claim for an active LED device owned by the authenticated customer. "
                    + "Verifies 180-day active warranty coverage, validates evidence media, prevents duplicate active claims, "
                    + "and creates a PENDING warranty ticket.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Warranty claim created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WarrantyClaimResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failure, warranty not activated, or warranty expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized — missing, expired, or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden — non-customer role or caller does not own the device",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Active warranty claim already exists for this device",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/warranty")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<WarrantyClaimResponse>> submitWarrantyClaim(
            Authentication authentication,
            @Parameter(description = "Device instance ID", required = true)
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody SubmitWarrantyClaimRequest request
    ) {
        Long customerId = resolveCustomerId(authentication);
        WarrantyClaimResponse response = submitWarrantyClaimUseCase.execute(customerId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private Long resolveCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InsufficientAuthenticationException("Authentication is required.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid customer identity in token.");
        }
    }
}
