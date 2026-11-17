package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.AssignCommissionRequest;
import com.lumintra.backend.commission.api.request.DesignerCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.request.ReviewCommissionSubmissionRequest;
import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.ApproveCommissionResponse;
import com.lumintra.backend.commission.api.response.AssignCommissionResponse;
import com.lumintra.backend.commission.api.response.CommissionSubmissionResponse;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse.DesignerCommissionQueueItem;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.application.ApproveCommissionRequestUseCase;
import com.lumintra.backend.commission.application.AssignCommissionToDesignerUseCase;
import com.lumintra.backend.commission.application.GetDesignerCommissionQueueUseCase;
import com.lumintra.backend.commission.application.GetManagementCommissionDetailUseCase;
import com.lumintra.backend.commission.application.GetStaffCommissionQueueUseCase;
import com.lumintra.backend.commission.application.ReviewCommissionSubmissionUseCase;
import com.lumintra.backend.commission.application.SubmitCommissionDraftUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(
        name = "Commission Management",
        description = "Internal management endpoints for staff, managers, and admins to inspect and process commission queues"
)
@RestController
@RequestMapping("/api/management/commissions")
@RequiredArgsConstructor
public class CommissionManagementController {
    private static final Set<String> MANAGEMENT_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");
    private static final Set<String> DESIGNER_ROLES = Set.of("DESIGNER");

    private final GetStaffCommissionQueueUseCase getStaffCommissionQueueUseCase;
    private final GetManagementCommissionDetailUseCase getManagementCommissionDetailUseCase;
    private final ApproveCommissionRequestUseCase approveCommissionRequestUseCase;
    private final AssignCommissionToDesignerUseCase assignCommissionToDesignerUseCase;
    private final GetDesignerCommissionQueueUseCase getDesignerCommissionQueueUseCase;
    private final SubmitCommissionDraftUseCase submitCommissionDraftUseCase;
    private final ReviewCommissionSubmissionUseCase reviewCommissionSubmissionUseCase;
    private final EmployeeLookupService employeeLookupService;
    private final InternalEmployeeAccessService internalEmployeeAccessService;

    @GetMapping("/designers")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    @Operation(summary = "List active designers for Commission assignment")
    public ResponseEntity<ApiResponse<List<DesignerOption>>> listDesigners() {
        var designers = employeeLookupService.findActiveDesigners().stream()
                .map(profile -> new DesignerOption(profile.employeeId(), profile.fullName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(designers));
    }

    public record DesignerOption(Long designerId, String fullName) {}

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
            summary = "View designer commission queue (CMS-14)",
            description = "Retrieves a paginated list of commission requests assigned specifically to the authenticated designer, with optional filtering by status and sorting by submission date.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Designer commission queue retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Invalid pagination or filter parameters",
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
                    description = "Forbidden - Caller role is not DESIGNER",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<ApiResponse<List<DesignerCommissionQueueItem>>> getDesignerCommissionQueue(
            Authentication authentication,
            @Valid @ModelAttribute DesignerCommissionQueueFilterRequest filterRequest
    ) {
        Long designerId = requireEmployeeId(authentication, DESIGNER_ROLES);
        DesignerCommissionQueueResponse response = getDesignerCommissionQueueUseCase.execute(designerId, filterRequest);
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
        boolean isPrivileged = hasPrivilegedManagementRole(authentication);
        Long callerId = requireEmployeeId(authentication, isPrivileged ? MANAGEMENT_ROLES : DESIGNER_ROLES);
        ManagementCommissionDetailResponse response = getManagementCommissionDetailUseCase.execute(id, callerId, isPrivileged);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Approve commission request and open payment",
            description = "Approves a customer artwork design request in PENDING_REVIEW state, locks assigned staff, sets quote expiration (+72h), initializes a SePay VietQR payment session, and transitions state to PENDING_PAYMENT.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission approved successfully and payment opened",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApproveCommissionResponse.class)
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
                    description = "Forbidden - Caller role is not STAFF/MANAGER/ADMIN, or ticket is locked by another staff member",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Commission request is not in PENDING_REVIEW status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApproveCommissionResponse>> approveCommission(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id
    ) {
        Long callerStaffId = requireEmployeeId(authentication, MANAGEMENT_ROLES);
        boolean isPrivileged = isManagerOrAdmin(authentication);
        ApproveCommissionResponse response = approveCommissionRequestUseCase.execute(id, callerStaffId, isPrivileged);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Assign a paid commission ticket to an internal Designer (CMS-13)",
            description = "Allows assigned staff or manager/admin to assign a PAID commission to an active DESIGNER. Sets sla_due_at (+72h) and transitions status to IN_PROGRESS.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission successfully assigned to designer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Validation failure or target employee is not an active designer",
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
                    description = "Forbidden - Caller role is not STAFF/MANAGER/ADMIN, or ticket is locked by another staff member",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Commission request is not in PAID status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AssignCommissionResponse>> assignCommission(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody AssignCommissionRequest request
    ) {
        Long callerStaffId = requireEmployeeId(authentication, MANAGEMENT_ROLES);
        boolean isPrivileged = isManagerOrAdmin(authentication);
        AssignCommissionResponse response = assignCommissionToDesignerUseCase.execute(id, request, callerStaffId, isPrivileged);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Submit a commission artwork draft version (Designer)",
            description = "Allows the assigned designer to submit a newly drafted version of the Commission Character for Staff review. Transitions status to INTERNAL_REVIEW.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{id}/submissions")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<ApiResponse<CommissionSubmissionResponse>> submitDraft(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody SubmitCommissionDraftRequest request
    ) {
        Long callerDesignerId = requireEmployeeId(authentication, DESIGNER_ROLES);
        CommissionSubmissionResponse response = submitCommissionDraftUseCase.execute(id, request, callerDesignerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Review a commission draft submission (Staff/Manager/Admin)",
            description = "Staff reviews a submitted draft: APPROVE transitions commission to PREVIEW_DELIVERED (delivering to customer), REQUEST_REVISION transitions commission to INTERNAL_REVISION_REQUIRED (requesting designer to draw next version).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{id}/submissions/{submissionId}/review")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CommissionSubmissionResponse>> reviewSubmission(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Parameter(description = "Unique submission draft identifier", example = "901")
            @PathVariable("submissionId") @Positive(message = "submissionId must be greater than 0") Long submissionId,
            @Valid @RequestBody ReviewCommissionSubmissionRequest request
    ) {
        Long callerStaffId = requireEmployeeId(authentication, MANAGEMENT_ROLES);
        boolean isPrivileged = isManagerOrAdmin(authentication);
        CommissionSubmissionResponse response = reviewCommissionSubmissionUseCase.execute(id, submissionId, request, callerStaffId, isPrivileged);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long requireEmployeeId(Authentication authentication, Set<String> allowedRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return internalEmployeeAccessService.requireActiveEmployeeId(authentication.getName(), allowedRoles);
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

    private boolean isManagerOrAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_MANAGER"));
    }
}
