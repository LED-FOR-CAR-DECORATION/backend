package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.application.CheckoutOrderUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Endpoints for customers to check out cart items and manage purchase orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutOrderUseCase checkoutOrderUseCase;

    @Operation(
            summary = "Checkout selected cart items to create an order",
            description = "Creates a PENDING_PAYMENT order from selected cart items, deducts physical LED hardware stock atomically, snapshots order items and delivery information (when physical goods are present), initializes SEPAY payment intent, and removes only the checked-out cart items."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Order created successfully and awaiting payment confirmation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: validation failure, invalid cart item, missing delivery address for physical goods, unavailable product, already owned digital product, or insufficient LED stock",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Validation Failed",
                                            value = """
                                                    {
                                                      "code": "VALIDATION_FAILED",
                                                      "message": "Invalid request payload",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T09:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": [
                                                        {
                                                          "field": "cartItemIds",
                                                          "message": "cartItemIds must not be empty"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invalid Cart Item",
                                            value = """
                                                    {
                                                      "code": "INVALID_CART_ITEM",
                                                      "message": "One or more selected cart items do not exist or do not belong to your cart.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T09:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Delivery Address Required",
                                            value = """
                                                    {
                                                      "code": "DELIVERY_ADDRESS_REQUIRED",
                                                      "message": "Delivery address or shipping details are required for physical orders.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T09:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Insufficient Stock",
                                            value = """
                                                    {
                                                      "code": "INSUFFICIENT_STOCK",
                                                      "message": "Insufficient LED hardware stock for one or more selected items.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T09:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Product Already Owned",
                                            value = """
                                                    {
                                                      "code": "PRODUCT_ALREADY_OWNED",
                                                      "message": "Customer already owns one or more selected digital products.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T09:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    )
                            }
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
                    description = "Forbidden: account type is not CUSTOMER (e.g. EMPLOYEE accounts are denied)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: deliveryAddressId does not exist or belongs to another customer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Address Not Found",
                                    value = """
                                            {
                                              "code": "ADDRESS_NOT_FOUND",
                                              "message": "Delivery address not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-10T09:00:00Z",
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
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {
        Long customerId = extractCustomerId(authentication);
        CheckoutResponse response = checkoutOrderUseCase.execute(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private Long extractCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new InvalidCredentialsException("Authentication is required.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated user identifier.");
        }
    }
}
