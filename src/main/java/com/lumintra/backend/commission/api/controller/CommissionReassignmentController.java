package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.ReassignCommissionRequest;
import com.lumintra.backend.commission.api.response.ReassignCommissionResponse;
import com.lumintra.backend.commission.application.ReassignCommissionUseCase;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@Tag(name = "Commission Management", description = "Internal commission workflow actions")
public class CommissionReassignmentController {

    private static final Set<String> OVERRIDE_ROLES = Set.of("MANAGER", "ADMIN");

    private final ReassignCommissionUseCase useCase;
    private final InternalEmployeeAccessService employeeAccess;

    @PatchMapping("/api/management/commissions/{id}/reassign")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(
            summary = "Manually reassign a commission ticket (CMS-25)",
            description = "MANAGER/ADMIN override of the assigned staff member and/or designer; the status never changes. "
                    + "Staff can be reassigned in any non-terminal status; a designer only once one is assigned "
                    + "(IN_PROGRESS through AWAITING_ESTIMATE). A new designer gets a fresh deadline (now + 72h while "
                    + "drafting, now + 24h while AWAITING_ESTIMATE) and the overdue flag is cleared. A SYSTEM chat message "
                    + "without names or reason is posted when the chat is open; the reason is only logged.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission reassigned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid id or body (no target, blank reason), target already assigned, "
                            + "or target is not an active STAFF/DESIGNER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller is not a MANAGER or ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Commission not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Commission is COMPLETED/CANCELLED/EXPIRED, or a designer can't be reassigned in its status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ReassignCommissionResponse> reassign(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody ReassignCommissionRequest request) {
        Long callerId = employeeAccess.requireActiveEmployeeId(authentication.getName(), OVERRIDE_ROLES);
        return ApiResponse.success(useCase.execute(id, request, callerId));
    }
}
