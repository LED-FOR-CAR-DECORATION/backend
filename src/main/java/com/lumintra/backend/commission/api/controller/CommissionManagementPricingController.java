package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.CreatePricingRuleRequest;
import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.application.CreateCustomPricingRuleUseCase;
import com.lumintra.backend.commission.application.GetManagementCommissionPricingUseCase;
import com.lumintra.backend.commission.application.GetManagementPricingRuleDetailUseCase;
import com.lumintra.backend.commission.application.GetManagementPricingTierDetailUseCase;
import com.lumintra.backend.commission.application.UpdatePricingRuleUseCase;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/commissions/pricing")
@RequiredArgsConstructor
@Validated
@Tag(name = "Commission Pricing Management", description = "Management APIs for viewing and managing commission pricing configurations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class CommissionManagementPricingController {

    private final GetManagementCommissionPricingUseCase getManagementCommissionPricingUseCase;
    private final GetManagementPricingTierDetailUseCase getManagementPricingTierDetailUseCase;
    private final GetManagementPricingRuleDetailUseCase getManagementPricingRuleDetailUseCase;
    private final CreateCustomPricingRuleUseCase createCustomPricingRuleUseCase;
    private final UpdatePricingRuleUseCase updatePricingRuleUseCase;

    @DeleteMapping("/rules/{ruleId}")
    @Operation(summary = "Remove a custom commission pricing rule",
            description = "Deactivates an unused custom rule while preserving historical commission pricing.")
    public ResponseEntity<Void> deletePricingRule(@PathVariable @Positive Long ruleId) {
        updatePricingRuleUseCase.delete(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rules/{ruleId}")
    @Operation(summary = "Update a commission pricing rule",
            description = "Partial update; null fields keep their value. System rules: name, description, display order and "
                    + "option label/price/modifierType/isActive/displayOrder; rule code, selection type, UI type, base flag, "
                    + "quantity bounds, required flag, dependency, rule deactivation and removal of core options are locked (409). "
                    + "Custom rules: also UI type, base flag, quantity bounds, required/active flags and an acyclic dependency "
                    + "(max depth 5). Options are soft-deactivated; deactivating anything an active preset or rule still uses "
                    + "returns 409 PRICING_RULE_IN_USE. Validation failures return 400, unknown rule 404.")
    public ResponseEntity<ApiResponse<ManagementPricingRuleResponse>> updatePricingRule(
            @PathVariable @Positive Long ruleId,
            @Valid @RequestBody UpdatePricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updatePricingRuleUseCase.execute(ruleId, request)));
    }

    @GetMapping
    @Operation(
            summary = "View commission pricing configuration overview",
            description = "Retrieves lightweight summary lists of all pricing tiers and configuration rules (active and inactive) for administrative management."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission pricing configuration overview retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagementCommissionPricingResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated or missing/invalid token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"status\":401,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied: requires MANAGER or ADMIN role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenExample",
                                    value = "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"status\":403,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error occurred",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ServerErrorExample",
                                    value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<ManagementCommissionPricingResponse>> getManagementPricing() {
        ManagementCommissionPricingResponse response = getManagementCommissionPricingUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tiers/{tierId}")
    @Operation(
            summary = "View management pricing tier detail",
            description = "Retrieves full details of a specific commission pricing tier, including bundled rule options and discount breakdown."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Pricing tier detail retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagementPricingTierResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid path variable tierId",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidTierIdExample",
                                    value = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Invalid request parameter\",\"status\":400,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[{\"field\":\"tierId\",\"message\":\"tierId must be a positive integer\"}]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated or missing/invalid token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"status\":401,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied: requires MANAGER or ADMIN role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenExample",
                                    value = "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"status\":403,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pricing tier not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "TierNotFoundExample",
                                    value = "{\"code\":\"COMMISSION_TIER_NOT_FOUND\",\"message\":\"Commission pricing tier with ID 999 not found.\",\"status\":404,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error occurred",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ServerErrorExample",
                                    value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<ManagementPricingTierResponse>> getManagementPricingTierDetail(
            @Parameter(description = "Unique ID of the pricing tier", example = "1")
            @PathVariable @Positive(message = "tierId must be a positive integer") Long tierId) {
        ManagementPricingTierResponse response = getManagementPricingTierDetailUseCase.execute(tierId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/rules/{ruleId}")
    @Operation(
            summary = "View management pricing rule detail",
            description = "Retrieves full details of a specific commission pricing configuration rule, including all options and dependency metadata."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Pricing rule detail retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagementPricingRuleResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid path variable ruleId",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidRuleIdExample",
                                    value = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Invalid request parameter\",\"status\":400,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[{\"field\":\"ruleId\",\"message\":\"ruleId must be a positive integer\"}]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated or missing/invalid token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"status\":401,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied: requires MANAGER or ADMIN role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenExample",
                                    value = "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"status\":403,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pricing rule not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "RuleNotFoundExample",
                                    value = "{\"code\":\"COMMISSION_RULE_NOT_FOUND\",\"message\":\"Commission pricing rule with ID 999 not found.\",\"status\":404,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error occurred",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ServerErrorExample",
                                    value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-15T21:30:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<ManagementPricingRuleResponse>> getManagementPricingRuleDetail(
            @Parameter(description = "Unique ID of the pricing configuration rule", example = "1")
            @PathVariable @Positive(message = "ruleId must be a positive integer") Long ruleId) {
        ManagementPricingRuleResponse response = getManagementPricingRuleDetailUseCase.execute(ruleId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/rules")
    @Operation(
            summary = "Create custom commission pricing rule",
            description = "Creates a new custom commission pricing rule using reusable primitives (SINGLE_SELECT, MULTI_SELECT, QUANTITY, TEXT_INPUT), deterministic modifiers (FLAT_ADD, BASE_PERCENT, SUBTOTAL_PERCENT), and optional acyclic multi-level visibility dependencies (depth <= 5)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Custom pricing rule created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagementPricingRuleResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or invalid rule configuration",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidRuleConfigurationExample",
                                    value = "{\"code\":\"INVALID_RULE_CONFIGURATION\",\"message\":\"SINGLE_SELECT rules only support RADIO or DROPDOWN UI types.\",\"status\":400,\"timestamp\":\"2026-11-15T22:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated or missing/invalid token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"status\":401,\"timestamp\":\"2026-11-15T22:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied: requires MANAGER or ADMIN role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenExample",
                                    value = "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"status\":403,\"timestamp\":\"2026-11-15T22:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Duplicate rule code conflict",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "DuplicateRuleCodeExample",
                                    value = "{\"code\":\"DUPLICATE_COMMISSION_RULE\",\"message\":\"Commission pricing rule with code 'FORMAT_TYPE' already exists.\",\"status\":409,\"timestamp\":\"2026-11-15T22:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error occurred",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ServerErrorExample",
                                    value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\",\"status\":500,\"timestamp\":\"2026-11-15T22:00:00Z\",\"traceId\":\"trace-id-sample\",\"details\":[]}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<ManagementPricingRuleResponse>> createCustomPricingRule(
            @Valid @RequestBody CreatePricingRuleRequest request) {
        ManagementPricingRuleResponse response = createCustomPricingRuleUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
