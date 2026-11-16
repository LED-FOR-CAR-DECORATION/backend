package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.request.LinkOrderFulfillmentDeviceRequest;
import com.lumintra.backend.commerce.api.response.LinkOrderFulfillmentDeviceResponse;
import com.lumintra.backend.commerce.application.LinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.commerce.application.UnlinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Staff Order Fulfillment",
        description = "Endpoints for staff to scan and link physical LED units to orders before dispatch"
)
@RestController
@RequestMapping("/api/management/staff/orders/{id}/fulfillment-devices")
@RequiredArgsConstructor
@Validated
public class OrderFulfillmentController {

    private final LinkOrderFulfillmentDeviceUseCase linkOrderFulfillmentDeviceUseCase;
    private final UnlinkOrderFulfillmentDeviceUseCase unlinkOrderFulfillmentDeviceUseCase;

    @Operation(
            summary = "Scan & Link LED Instance to Order Item",
            description = "Links a physical LED hardware unit (by Serial Number) to a specific order line item and marks it IN_TRANSIT without binding or activating warranty."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<LinkOrderFulfillmentDeviceResponse>> linkDevice(
            Authentication authentication,
            @Parameter(description = "Order ID", example = "102")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody LinkOrderFulfillmentDeviceRequest request) {
        String subject = extractAuthenticatedSubject(authentication);
        LinkOrderFulfillmentDeviceResponse response = linkOrderFulfillmentDeviceUseCase.execute(subject, id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Unlink LED Instance from Order",
            description = "Unlinks a previously scanned LED hardware unit from an order and returns its status back to IN_STOCK."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @DeleteMapping("/{instanceId}")
    public ResponseEntity<ApiResponse<Void>> unlinkDevice(
            Authentication authentication,
            @Parameter(description = "Order ID", example = "102")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Parameter(description = "LED instance ID", example = "15")
            @PathVariable("instanceId") @Positive(message = "instanceId must be greater than 0") Long instanceId) {
        String subject = extractAuthenticatedSubject(authentication);
        unlinkOrderFulfillmentDeviceUseCase.execute(subject, id, instanceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String extractAuthenticatedSubject(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return authentication.getName();
    }
}
