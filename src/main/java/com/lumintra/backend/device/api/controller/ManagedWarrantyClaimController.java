package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.ProcessWarrantyClaimRequest;
import com.lumintra.backend.device.api.request.WarrantyClaimFilterRequest;
import com.lumintra.backend.device.api.response.ManagedWarrantyClaimResponse;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.application.ListManagedWarrantyClaimsUseCase;
import com.lumintra.backend.device.application.ProcessWarrantyClaimUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Warranty Claims",
        description = "Staff, manager, and admin endpoints for reviewing and processing customer hardware warranty claims"
)
@RestController
@RequestMapping({"/api/management/manager/warranty-claims", "/api/management/staff/warranty-claims"})
@RequiredArgsConstructor
public class ManagedWarrantyClaimController {

    private final ListManagedWarrantyClaimsUseCase listManagedWarrantyClaimsUseCase;
    private final ProcessWarrantyClaimUseCase processWarrantyClaimUseCase;

    @Operation(
            summary = "List and search warranty claims across the system",
            description = "Retrieves a paginated list of warranty claims with enriched customer and device metadata. Supports filtering by lifecycle status and keyword search across device serial number, customer name, email, phone, or defect description."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated warranty claims retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid pagination or filter parameters",
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
                                              "timestamp": "2026-11-16T15:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "size",
                                                  "message": "size must be at least 1"
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
                                              "timestamp": "2026-11-16T15:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller does not hold STAFF, MANAGER, or ADMIN role",
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
                                              "timestamp": "2026-11-16T15:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ManagedWarrantyClaimResponse>>> listWarrantyClaims(
            Authentication authentication,
            @Valid @ModelAttribute WarrantyClaimFilterRequest filter) {
        requireAuthenticated(authentication);
        Page<ManagedWarrantyClaimResponse> page = listManagedWarrantyClaimsUseCase.execute(filter);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PaginationMeta.from(page)));
    }

    @Operation(
            summary = "Adjudicate and update status of a warranty claim",
            description = "Allows staff, managers, and admins to transition a warranty claim from PENDING to APPROVED/REJECTED, or from APPROVED to COMPLETED/REJECTED. Non-blank resolutionNotes is required when rejecting or completing."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Warranty claim status processed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid ID or missing resolutionNotes for rejection/completion",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
                    description = "Forbidden: caller does not hold STAFF, MANAGER, or ADMIN role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: warranty claim does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict: invalid status transition according to warranty state machine",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<WarrantyClaimResponse>> processWarrantyClaim(
            Authentication authentication,
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody ProcessWarrantyClaimRequest request) {
        requireAuthenticated(authentication);
        WarrantyClaimResponse response = processWarrantyClaimUseCase.execute(id, request);
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
