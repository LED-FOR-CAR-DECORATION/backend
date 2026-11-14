package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.application.GetLedInstanceDetailUseCase;
import com.lumintra.backend.device.application.ListLedInstancesUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "LED Hardware Inventory",
        description = "Manager/admin endpoints for tracking physical LED units and re-printing factory QR payloads"
)
@RestController
@RequestMapping("/api/management/admin/led-instances")
@RequiredArgsConstructor
public class ManagedLedInstanceController {

    private final ListLedInstancesUseCase listLedInstancesUseCase;
    private final GetLedInstanceDetailUseCase getLedInstanceDetailUseCase;

    @Operation(
            summary = "List and track LED hardware instances",
            description = "Retrieves a paginated list of individual physical ESP32 LED units across warehouse stock, transit, customer ownership, returns, and decommissioning for ADMIN and MANAGER users. Supports filtering by modelId, status, and case-insensitive serialNumber/macAddress search. Returns signed factory QR payloads only when status is neither BOUND nor DECOMMISSIONED."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated LED hardware instances retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid pagination, modelId, or status filter parameter",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "status",
                                                  "message": "Invalid parameter value"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller does not hold ADMIN or MANAGER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Forbidden",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-14T06:00:00Z",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LedInstanceListResponse.LedInstanceItem>>> listLedInstances(
            Authentication authentication,
            @Valid @ModelAttribute LedInstanceFilterRequest filter) {
        requireAuthenticated(authentication);
        LedInstanceListResponse result = listLedInstancesUseCase.execute(filter);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

    @Operation(
            summary = "Get LED hardware instance details and factory QR payload",
            description = "Retrieves full hardware unit details, parent LED model specifications, derived warranty status, linked fulfillment order info, customer owner summary (if bound), and signed factory QR payload for ADMIN and MANAGER users. Suppresses qrPayload when status is BOUND or DECOMMISSIONED."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "LED hardware instance detail retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid or non-positive LED instance id",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
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
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller does not hold ADMIN or MANAGER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Forbidden",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: LED hardware instance does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "LED Instance Not Found",
                                    value = """
                                            {
                                              "code": "LED_INSTANCE_NOT_FOUND",
                                              "message": "LED instance not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-14T06:00:00Z",
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
                                              "timestamp": "2026-11-14T06:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LedInstanceDetailResponse>> getLedInstanceDetail(
            Authentication authentication,
            @Parameter(description = "Numeric LED hardware instance identifier", example = "1")
            @PathVariable @Positive(message = "id must be greater than 0") Long id) {
        requireAuthenticated(authentication);
        LedInstanceDetailResponse response = getLedInstanceDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
    }
}
