package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.application.GetStaffOrderDetailUseCase;
import com.lumintra.backend.commerce.application.ListStaffOrdersUseCase;
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
        name = "Staff Order Fulfillment",
        description = "Endpoints for staff, managers, and admins to inspect and fulfill physical LED orders"
)
@RestController
@RequestMapping("/api/management/staff/orders")
@RequiredArgsConstructor
public class StaffOrderController {

    private final ListStaffOrdersUseCase listStaffOrdersUseCase;
    private final GetStaffOrderDetailUseCase getStaffOrderDetailUseCase;

    @Operation(
            summary = "List staff order fulfillment queue",
            description = "Retrieves a paginated queue of physical LED fulfillment orders (requiring an order_delivery record), sorted by createdAt descending. Defaults to status in (PROCESSING, PARTIALLY_FULFILLED) when status filter is omitted, and supports filtering by order status, latest payment status, and delivery status."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated fulfillment queue retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid pagination or status filter enum",
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
                                              "timestamp": "2026-11-12T06:00:00Z",
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller is not an active internal STAFF, MANAGER, or ADMIN account",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffOrderListResponse.StaffOrderSummaryItem>>> listOrders(
            Authentication authentication,
            @Valid @ModelAttribute StaffOrderFilterRequest request) {
        String subject = extractAuthenticatedSubject(authentication);
        StaffOrderListResponse result = listStaffOrdersUseCase.execute(subject, request);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

    @Operation(
            summary = "View order fulfillment detail",
            description = "Retrieves detailed fulfillment information for any order by ID, including IAM customer contact summary, snapshotted delivery address/tracking, purchased line items, and payment reconciliation summary (excluding customer VietQR retry fields)."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order detail retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid or non-positive order id path parameter",
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
                                              "timestamp": "2026-11-12T06:00:00Z",
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller is not an active internal STAFF, MANAGER, or ADMIN account",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: order does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Order Not Found",
                                    value = """
                                            {
                                              "code": "ORDER_NOT_FOUND",
                                              "message": "Order not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-12T06:00:00Z",
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffOrderDetailResponse>> getOrderDetail(
            Authentication authentication,
            @Parameter(description = "Numeric purchase order identifier", example = "102")
            @PathVariable @Positive(message = "id must be greater than 0") Long id) {
        String subject = extractAuthenticatedSubject(authentication);
        StaffOrderDetailResponse response = getStaffOrderDetailUseCase.execute(subject, id);
        return ResponseEntity.ok(ApiResponse.success(response));
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
