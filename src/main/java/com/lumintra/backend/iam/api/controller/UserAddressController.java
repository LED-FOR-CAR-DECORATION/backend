package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.application.ListUserAddressesUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "User Addresses", description = "Customer saved delivery address book")
public class UserAddressController {

    private final ListUserAddressesUseCase listUserAddressesUseCase;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List the current customer's saved delivery addresses",
            description =
                    "Returns saved delivery addresses owned by the authenticated active CUSTOMER"
                            + " account ordered default-first, then by latest update/creation"
                            + " timestamp descending.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "ApiResponse<List<UserAddressResponse>>",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "Saved Addresses",
                                                value =
                                                        """
                                                        {
                                                          "data": [
                                                            {
                                                              "addressId": 10,
                                                              "recipientName": "Nguyen Van A",
                                                              "recipientPhone": "0901234567",
                                                              "fullAddress": "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                                                              "latitude": 10.7768890,
                                                              "longitude": 106.7008060,
                                                              "isDefault": true,
                                                              "createdAt": "2026-11-10T02:15:00Z",
                                                              "updatedAt": "2026-11-10T02:15:00Z"
                                                            }
                                                          ]
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing, expired, or invalid authentication",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "UNAUTHORIZED",
                                                          "message": "Authentication is required.",
                                                          "status": 401,
                                                          "timestamp": "2026-11-12T01:30:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active CUSTOMER account",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "FORBIDDEN",
                                                          "message": "Access is denied.",
                                                          "status": 403,
                                                          "timestamp": "2026-11-12T01:30:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Authenticated user account not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "USER_NOT_FOUND",
                                                          "message": "Account not found.",
                                                          "status": 404,
                                                          "timestamp": "2026-11-12T01:30:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "Unexpected storage or server failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "INTERNAL_SERVER_ERROR",
                                                          "message": "An unexpected error occurred",
                                                          "status": 500,
                                                          "timestamp": "2026-11-12T01:30:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """)))
    })
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> listAddresses(
            Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        List<UserAddressResponse> addresses =
                listUserAddressesUseCase.execute(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }
}
