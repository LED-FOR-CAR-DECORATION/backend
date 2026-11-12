package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.request.AddCartItemRequest;
import com.lumintra.backend.commerce.api.request.UpdateCartItemQuantityRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.AddCartItemUseCase;
import com.lumintra.backend.commerce.application.UpdateCartItemQuantityUseCase;
import com.lumintra.backend.commerce.application.ViewCartUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shopping Cart", description = "Endpoints for customers to view and manage their active shopping cart")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final ViewCartUseCase viewCartUseCase;
    private final AddCartItemUseCase addCartItemUseCase;
    private final UpdateCartItemQuantityUseCase updateCartItemQuantityUseCase;

    @Operation(
            summary = "View active shopping cart",
            description = "Retrieves the authenticated customer's active shopping cart with real-time product base prices, item subtotals, and availability flags. Initializes an empty cart if none exists."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Active shopping cart retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized Error",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-10T03:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: account type is not CUSTOMER (e.g. EMPLOYEE accounts are denied)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Forbidden Error",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-10T03:00:00Z",
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
                                              "timestamp": "2026-11-10T03:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> viewCart(Authentication authentication) {
        Long customerId = extractCustomerId(authentication);
        CartResponse response = viewCartUseCase.execute(customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Add item to active shopping cart",
            description = "Adds a published product to the authenticated customer's active cart, initializing the cart if absent, enforcing digital item limits/ownership, and accumulating LED/bundle quantities within available stock."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Item added and updated shopping cart returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: validation failure, unavailable product, already owned digital product, digital quantity limit exceeded, or insufficient stock",
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
                                                      "timestamp": "2026-11-10T03:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": [
                                                        {
                                                          "field": "quantity",
                                                          "message": "quantity must be at least 1"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Product Not Available",
                                            value = """
                                                    {
                                                      "code": "PRODUCT_NOT_AVAILABLE",
                                                      "message": "Product is not published for purchase.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T03:00:00Z",
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
                                                      "message": "Customer already owns this digital product.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T03:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Digital Item Quantity Limit",
                                            value = """
                                                    {
                                                      "code": "DIGITAL_ITEM_QUANTITY_LIMIT",
                                                      "message": "Digital products cannot exceed a quantity of 1 in the cart.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T03:00:00Z",
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
                                                      "message": "Requested LED quantity exceeds available stock.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-10T03:00:00Z",
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
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request) {
        Long customerId = extractCustomerId(authentication);
        CartResponse response = addCartItemUseCase.execute(customerId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Update cart item quantity",
            description = "Overwrites the quantity of a physical item (LED or BUNDLE) in the authenticated customer's active cart after enforcing ownership (anti-IDOR), product availability, and real-time hardware stock. Quantity updates on digital products (CHARACTER or SUBSCRIPTION) are strictly rejected."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cart item quantity updated and refreshed shopping cart returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid path id or quantity, unavailable product, already owned digital product, digital quantity limit exceeded, or insufficient stock",
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
                                                      "timestamp": "2026-11-11T15:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": [
                                                        {
                                                          "field": "quantity",
                                                          "message": "quantity must be at least 1"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Product Not Available",
                                            value = """
                                                    {
                                                      "code": "PRODUCT_NOT_AVAILABLE",
                                                      "message": "Product is not published for purchase.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-11T15:00:00Z",
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
                                                      "message": "Customer already owns this digital product.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-11T15:00:00Z",
                                                      "traceId": "trace-12345",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Digital Item Quantity Limit",
                                            value = """
                                                    {
                                                      "code": "DIGITAL_ITEM_QUANTITY_LIMIT",
                                                      "message": "Quantity cannot be modified for digital products in the cart.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-11T15:00:00Z",
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
                                                      "message": "Requested LED quantity exceeds available stock.",
                                                      "status": 400,
                                                      "timestamp": "2026-11-11T15:00:00Z",
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
                    description = "Not Found: cart item does not exist or belongs to another customer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Cart Item Not Found",
                                    value = """
                                            {
                                              "code": "CART_ITEM_NOT_FOUND",
                                              "message": "Cart item not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-11T15:00:00Z",
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
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            Authentication authentication,
            @Parameter(description = "Numeric cart item identifier", example = "501")
            @PathVariable @Positive(message = "itemId must be greater than 0") Long itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request) {
        Long customerId = extractCustomerId(authentication);
        CartResponse response = updateCartItemQuantityUseCase.execute(customerId, itemId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
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
