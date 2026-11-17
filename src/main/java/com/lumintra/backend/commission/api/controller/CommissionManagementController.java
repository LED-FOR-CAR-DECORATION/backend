package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.application.GetStaffCommissionQueueUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
}
