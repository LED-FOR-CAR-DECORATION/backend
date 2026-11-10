package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.ViewCartUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shopping Cart", description = "Endpoints for customers to view and manage their active shopping cart")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final ViewCartUseCase viewCartUseCase;

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
