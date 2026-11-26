package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.PostRevisionCardRequest;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.application.PostRevisionCardUseCase;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@Tag(name = "Commission Management", description = "Internal commission workflow actions")
public class CommissionRevisionCardController {
    private final PostRevisionCardUseCase useCase;
    private final InternalEmployeeAccessService employeeAccess;

    @PostMapping("/api/management/commissions/{id}/revision-card")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "Post a revision brief card (CMS-19)",
            description =
                    "The assigned staff proposes a free or paid revision while the commission"
                            + " remains PREVIEW_DELIVERED.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Revision card created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid ID or brief",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Assigned STAFF required",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Commission not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Commission is not PREVIEW_DELIVERED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<CommissionMessageResponse> post(
            Authentication authentication,
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody PostRevisionCardRequest request) {
        var staffId =
                employeeAccess.requireActiveEmployeeId(authentication.getName(), Set.of("STAFF"));
        return ApiResponse.success(useCase.execute(id, staffId, request));
    }
}
