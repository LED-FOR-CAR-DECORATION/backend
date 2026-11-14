package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.UpdateSubscriptionRequest;
import com.lumintra.backend.store.api.response.UpdatedSubscriptionResponse;
import com.lumintra.backend.store.application.UpdateSubscriptionUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UpdateSubscriptionController {
    private final UpdateSubscriptionUseCase useCase;

    @PutMapping("/api/management/admin/store/subscriptions/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "SUBSCRIPTION_NOT_FOUND")
    @Operation(summary = "Update a subscription plan")
    public ApiResponse<UpdatedSubscriptionResponse> update(
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return ApiResponse.success(useCase.execute(id, request));
    }
}
