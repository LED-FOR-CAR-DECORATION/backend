package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.UpdateCommissionPresetsRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.application.UpdateCommissionPresetsUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/commissions/presets")
@RequiredArgsConstructor
@Tag(name = "Commission Pricing Management", description = "Management APIs for viewing and managing commission pricing configurations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class CommissionPresetManagementController {

    private final UpdateCommissionPresetsUseCase updateCommissionPresetsUseCase;

    @PutMapping
    @Operation(
            summary = "Configure commission presets and the CUSTOM base mode",
            description = "Creates or fully replaces each listed tier, keyed by tierCode (case-insensitive); tiers not listed "
                    + "are unchanged and an unknown code creates a new preset. maxRevisions: BASIC = 1, ADVANCED = 3, "
                    + "others 1..5. CUSTOM cannot be deactivated, bundle or trigger on options, or carry a discount. "
                    + "Presets may bundle options of active SINGLE_SELECT/MULTI_SELECT rules (one per single-select rule); "
                    + "every bundled option's prerequisite chain (max depth 5) must be bundled or listed in triggerOptionIds. "
                    + "No CUSTOM-over-preset price floor; changes only affect future commission requests. "
                    + "Returns every tier after the update.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presets updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid body or preset configuration",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "BrokenDependencyExample",
                                    value = "{\"code\":\"INVALID_RULE_CONFIGURATION\",\"message\":\"Tier 'ADVANCED' bundles dependent option '6-15' without bundling or triggering on its prerequisite option (ID 2).\",\"status\":400,\"timestamp\":\"2026-11-20T10:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Requires MANAGER or ADMIN role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<ManagementPricingTierResponse>>> updatePresets(
            @Valid @RequestBody UpdateCommissionPresetsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateCommissionPresetsUseCase.execute(request)));
    }
}
