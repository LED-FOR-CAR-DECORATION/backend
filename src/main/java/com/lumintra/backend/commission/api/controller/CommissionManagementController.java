package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.application.GetManagementCommissionDetailUseCase;
import com.lumintra.backend.commission.application.GetStaffCommissionQueueUseCase;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Commission Management",
        description = "Internal management endpoints for staff, managers, and admins to inspect and process commission queues"
)
@RestController
@RequestMapping("/api/management/commissions")
@RequiredArgsConstructor
public class CommissionManagementController {

    private final GetStaffCommissionQueueUseCase getStaffCommissionQueueUseCase;
    private final GetManagementCommissionDetailUseCase getManagementCommissionDetailUseCase;

    @Operation(
            summary = "View staff commission queue",
            description = "Retrieves a paginated list of custom artwork commission requests across all customers with global visibility, supporting filtering by status across all 13 states and sorting by submission date.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Staff commission queue retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StaffCommissionQueueResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter parameters (e.g. invalid page index, size, or sort direction)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ValidationError",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Validation failed for request arguments",
                                              "status": 400,
                                              "timestamp": "2026-11-17T08:00:00Z",
                                              "traceId": "trace-101",
                                              "details": {
                                                "page": "page must be greater than or equal to 0"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or expired bearer token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-17T08:00:00Z",
                                              "traceId": "trace-401",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller role is not permitted (requires STAFF, MANAGER, or ADMIN)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenRoleExample",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-17T08:00:00Z",
                                              "traceId": "trace-403",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffCommissionQueueItem>>> getStaffCommissionQueue(
            @Valid @ModelAttribute StaffCommissionQueueFilterRequest filterRequest
    ) {
        StaffCommissionQueueResponse response = getStaffCommissionQueueUseCase.execute(filterRequest);
        return ResponseEntity.ok(ApiResponse.success(response.items(), response.meta()));
    }

    @Operation(
            summary = "View management commission details",
            description = "Retrieves complete management details for a commission ticket, including full internal draft submission history, customer details, assignment info, SLA timestamps, and active payment code. Restricted to STAFF, MANAGER, ADMIN, or the assigned DESIGNER.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Management commission details retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagementCommissionDetailResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid commission identifier (e.g. id <= 0)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or expired bearer token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller role is not permitted or designer is not assigned to this ticket",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Commission request not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'DESIGNER')")
    public ResponseEntity<ApiResponse<ManagementCommissionDetailResponse>> getManagementCommissionDetail(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id
    ) {
        Long callerId = extractCallerId(authentication);
        boolean isPrivileged = hasPrivilegedManagementRole(authentication);
        ManagementCommissionDetailResponse response = getManagementCommissionDetailUseCase.execute(id, callerId, isPrivileged);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long extractCallerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new InvalidCredentialsException("Invalid authentication principal");
        }
        try {
            long id = Long.parseLong(name.trim());
            if (id <= 0) {
                throw new InvalidCredentialsException("Invalid user ID in token");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid user ID in token");
        }
    }

    private boolean hasPrivilegedManagementRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF")
                            || a.getAuthority().equals("ROLE_MANAGER")
                            || a.getAuthority().equals("ROLE_ADMIN"));
    }
}
