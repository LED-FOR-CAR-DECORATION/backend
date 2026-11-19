package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.InputRevisionEstimateRequest;
import com.lumintra.backend.commission.api.response.InputRevisionEstimateResponse;
import com.lumintra.backend.commission.application.InputRevisionEstimateUseCase;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Commission Management", description = "Internal commission workflow actions")
public class CommissionRevisionEstimateController {

    private final InputRevisionEstimateUseCase useCase;
    private final InternalEmployeeAccessService employeeAccess;

    @PatchMapping("/api/management/commissions/{id}/estimate")
    @PreAuthorize("hasRole('DESIGNER')")
    @Operation(
            summary = "Input revision delivery estimate (CMS-24)",
            description = "The assigned DESIGNER estimates the confirmed revision in hours (1..72, BR-FL3-14). "
                    + "Sets sla_due_at to now + estimate, moves the revision request and the commission from "
                    + "AWAITING_ESTIMATE to IN_PROGRESS, and posts a SYSTEM chat message telling the customer the "
                    + "delivery deadline (estimate + 24h buffer).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Estimate applied; commission is IN_PROGRESS"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid ID, or estimatedHours missing or outside 1..72",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller is not a DESIGNER or not the assigned designer",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Commission not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Commission is not AWAITING_ESTIMATE or has no confirmed revision request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<InputRevisionEstimateResponse> inputEstimate(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody InputRevisionEstimateRequest request) {
        var designerId = employeeAccess.requireActiveEmployeeId(authentication.getName(), Set.of("DESIGNER"));
        return ApiResponse.success(useCase.execute(id, designerId, request));
    }
}
