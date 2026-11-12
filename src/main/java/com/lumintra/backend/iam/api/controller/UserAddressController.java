package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.AddUserAddressRequest;
import com.lumintra.backend.iam.api.request.UpdateUserAddressRequest;
import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.application.AddUserAddressUseCase;
import com.lumintra.backend.iam.application.ListUserAddressesUseCase;
import com.lumintra.backend.iam.application.UpdateUserAddressUseCase;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "User Addresses", description = "Customer saved delivery address book")
public class UserAddressController {

    private final ListUserAddressesUseCase listUserAddressesUseCase;
    private final AddUserAddressUseCase addUserAddressUseCase;
    private final UpdateUserAddressUseCase updateUserAddressUseCase;

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
        ensureAuthenticated(authentication);
        List<UserAddressResponse> addresses =
                listUserAddressesUseCase.execute(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Add a new delivery address for the current customer",
            description =
                    "Saves a customer delivery address using recipient contact details, Goong Map"
                            + " formatted full address, and WGS84 coordinates. The first address is"
                            + " automatically marked default, and setting isDefault=true clears"
                            + " existing defaults.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Created address inside ApiResponse<UserAddressResponse>",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "Created Address",
                                                value =
                                                        """
                                                        {
                                                          "data": {
                                                            "addressId": 10,
                                                            "recipientName": "Nguyen Van A",
                                                            "recipientPhone": "0901234567",
                                                            "fullAddress": "123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City",
                                                            "latitude": 10.7768891,
                                                            "longitude": 106.7008066,
                                                            "isDefault": true,
                                                            "createdAt": "2026-11-12T02:00:00Z",
                                                            "updatedAt": "2026-11-12T02:00:00Z"
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failed or malformed JSON payload",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "VALIDATION_FAILED",
                                                          "message": "Invalid request payload",
                                                          "status": 400,
                                                          "timestamp": "2026-11-12T02:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": [
                                                            {
                                                              "field": "fullAddress",
                                                              "message": "fullAddress must not exceed 255 characters"
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
                                                          "timestamp": "2026-11-12T02:00:00Z",
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
                                                          "timestamp": "2026-11-12T02:00:00Z",
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
                                                          "timestamp": "2026-11-12T02:00:00Z",
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
                                                          "timestamp": "2026-11-12T02:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """)))
    })
    public ResponseEntity<ApiResponse<UserAddressResponse>> addAddress(
            Authentication authentication,
            @Valid @RequestBody AddUserAddressRequest request) {
        ensureAuthenticated(authentication);
        UserAddressResponse created =
                addUserAddressUseCase.execute(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update an existing delivery address for the current customer",
            description =
                    "Updates recipient contact details, Goong Map formatted full address, and"
                            + " WGS84 coordinates for a saved delivery address owned by the"
                            + " authenticated active CUSTOMER account while preserving the"
                            + " existing isDefault flag.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Updated address inside ApiResponse<UserAddressResponse>",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "Updated Address",
                                                value =
                                                        """
                                                        {
                                                          "data": {
                                                            "addressId": 10,
                                                            "recipientName": "Nguyen Van B",
                                                            "recipientPhone": "0912345678",
                                                            "fullAddress": "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                                                            "latitude": 10.7731235,
                                                            "longitude": 106.6987654,
                                                            "isDefault": true,
                                                            "createdAt": "2026-11-12T02:00:00Z",
                                                            "updatedAt": "2026-11-12T03:00:00Z"
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid address id, validation failed, or malformed JSON payload",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "code": "VALIDATION_FAILED",
                                                          "message": "Invalid request payload",
                                                          "status": 400,
                                                          "timestamp": "2026-11-12T03:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": [
                                                            {
                                                              "field": "fullAddress",
                                                              "message": "fullAddress must not exceed 255 characters"
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
                                                          "timestamp": "2026-11-12T03:00:00Z",
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
                                                          "timestamp": "2026-11-12T03:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Delivery address not found/not owned, or user account not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "Address Not Found",
                                            value =
                                                    """
                                                    {
                                                      "code": "ADDRESS_NOT_FOUND",
                                                      "message": "Delivery address not found.",
                                                      "status": 404,
                                                      "timestamp": "2026-11-12T03:00:00Z",
                                                      "traceId": "trace-123",
                                                      "details": []
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "User Not Found",
                                            value =
                                                    """
                                                    {
                                                      "code": "USER_NOT_FOUND",
                                                      "message": "Account not found.",
                                                      "status": 404,
                                                      "timestamp": "2026-11-12T03:00:00Z",
                                                      "traceId": "trace-123",
                                                      "details": []
                                                    }
                                                    """)
                                })),
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
                                                          "timestamp": "2026-11-12T03:00:00Z",
                                                          "traceId": "trace-123",
                                                          "details": []
                                                        }
                                                        """)))
    })
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateAddress(
            Authentication authentication,
            @Parameter(description = "Positive identifier of the saved delivery address", example = "10")
            @PathVariable @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody UpdateUserAddressRequest request) {
        ensureAuthenticated(authentication);
        UserAddressResponse updated =
                updateUserAddressUseCase.execute(authentication.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    private void ensureAuthenticated(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
    }
}
