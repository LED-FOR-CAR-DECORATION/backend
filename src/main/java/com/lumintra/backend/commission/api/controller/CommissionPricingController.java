package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.application.GetPublicCommissionPricingUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
@Tag(name = "Commission Pricing", description = "Public and customer endpoints for retrieving commission pricing tiers, packages, and rules")
public class CommissionPricingController {

    private final GetPublicCommissionPricingUseCase getPublicCommissionPricingUseCase;

    @GetMapping("/pricing")
    @Operation(
            summary = "Get commission pricing tiers and rules",
            description = "Retrieves the active CUSTOM configurator (plus any active preset packages when configured) and active ordered pricing rules built with reusable primitives (SINGLE_SELECT, MULTI_SELECT, QUANTITY, TEXT_INPUT) and deterministic modifiers (FLAT_ADD, BASE_PERCENT, SUBTOTAL_PERCENT)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission pricing tiers and rules retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublicCommissionPricingResponse.class)
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
    public ResponseEntity<ApiResponse<PublicCommissionPricingResponse>> getCommissionPricing() {
        PublicCommissionPricingResponse response = getPublicCommissionPricingUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
