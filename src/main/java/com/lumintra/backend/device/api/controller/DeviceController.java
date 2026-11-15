package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceDetailResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.application.ListCustomerDevicesUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Customer Devices",
        description = "Customer endpoints for claiming and managing registered LED hardware devices"
)
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final ClaimDeviceUseCase claimDeviceUseCase;
    private final ListCustomerDevicesUseCase listCustomerDevicesUseCase;
    private final GetCustomerDeviceDetailUseCase getCustomerDeviceDetailUseCase;

    @Operation(
            summary = "Claim an LED device via factory QR code",
            description = "Verifies the cryptographic HMAC-SHA256 signature embedded in the scanned factory QR payload, "
                    + "confirms the device is not already bound, verifies that the physical unit was fulfilled for a "
                    + "DELIVERED or COMPLETED order owned by the authenticated customer, and atomically binds the device "
                    + "to the customer's account while activating its 180-day warranty.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Device claimed and warranty activated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClaimDeviceResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error or invalid/tampered QR code signature (INVALID_DEVICE_QR)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Invalid Device QR",
                                    value = """
                                            {
                                              "code": "INVALID_DEVICE_QR",
                                              "message": "QR code signature verification failed.",
                                              "status": 400,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized — missing, expired, or invalid JWT token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden — requires CUSTOMER role, or device does not belong to an order owned by this customer (DEVICE_OWNERSHIP_MISMATCH)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Ownership Mismatch",
                                    value = """
                                            {
                                              "code": "DEVICE_OWNERSHIP_MISMATCH",
                                              "message": "Device was not fulfilled for an order owned by the authenticated customer.",
                                              "status": 403,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "LED hardware instance not found (LED_INSTANCE_NOT_FOUND)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Instance Not Found",
                                    value = """
                                            {
                                              "code": "LED_INSTANCE_NOT_FOUND",
                                              "message": "LED hardware instance not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict — device is already bound (DEVICE_ALREADY_BOUND) or the associated order is not yet DELIVERED/COMPLETED (ORDER_NOT_DELIVERED_YET)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Device Already Bound",
                                    value = """
                                            {
                                              "code": "DEVICE_ALREADY_BOUND",
                                              "message": "Device is already registered and bound to an account.",
                                              "status": 409,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Internal Server Error",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-14T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
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

    @Operation(
            summary = "List customer owned LED devices",
            description = "Returns a paginated list of physical LED hardware devices currently bound to the authenticated CUSTOMER account, ordered from newest to oldest by pairing timestamp (`boundAt DESC`). Includes device identifiers, BLE MAC address, display name, lifecycle status, hardware model resolution (`widthPx` x `heightPx`), reported firmware ID, and computed warranty status.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated list of customer-owned LED hardware devices retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": [
                                                {
                                                  "field": "page",
                                                  "message": "page must be greater than or equal to 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required to access this resource",
                                              "status": 401,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Authenticated user does not hold the CUSTOMER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenRole",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied",
                                              "status": 403,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected internal error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDeviceListResponse.CustomerDeviceItem>>> listDevices(
            Authentication authentication,
            @Valid @ModelAttribute CustomerDeviceFilterRequest filter) {
        Long customerId = extractCustomerId(authentication);
        CustomerDeviceListResponse result = listCustomerDevicesUseCase.execute(customerId, filter);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

    @Operation(
            summary = "Get customer owned LED device detail",
            description = "Returns the full hardware profile, linked parent LED model specifications (including deprecated models for already-owned hardware), calculated warranty coverage status, and original purchase order fulfillment linkage for a single device owned by the authenticated CUSTOMER account. Factory QR payload secrets and live BLE state are omitted.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Customer-owned LED hardware device details retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid device path identifier",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": [
                                                {
                                                  "field": "id",
                                                  "message": "id must be greater than 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required to access this resource",
                                              "status": 401,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller lacks the CUSTOMER role or does not own the requested LED hardware device",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "DeviceOwnershipMismatch",
                                            value = """
                                                    {
                                                      "code": "DEVICE_OWNERSHIP_MISMATCH",
                                                      "message": "You do not own this LED hardware device.",
                                                      "status": 403,
                                                      "timestamp": "2026-11-14T10:15:30Z",
                                                      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                                      "details": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "ForbiddenRole",
                                            value = """
                                                    {
                                                      "code": "FORBIDDEN",
                                                      "message": "Access is denied",
                                                      "status": 403,
                                                      "timestamp": "2026-11-14T10:15:30Z",
                                                      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                                      "details": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Requested LED hardware device was not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "LedInstanceNotFound",
                                    value = """
                                            {
                                              "code": "LED_INSTANCE_NOT_FOUND",
                                              "message": "LED hardware device not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected internal error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-14T10:15:30Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": null
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDeviceDetailResponse>> getDeviceDetail(
            Authentication authentication,
            @Parameter(description = "Unique LED hardware device identifier (instance ID)", example = "2001", required = true)
            @PathVariable @Positive(message = "id must be greater than 0") Long id) {
        Long customerId = extractCustomerId(authentication);
        CustomerDeviceDetailResponse response = getCustomerDeviceDetailUseCase.execute(customerId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
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
