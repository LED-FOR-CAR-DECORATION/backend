package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.CreateSubscriptionRequest;
import com.lumintra.backend.store.api.response.CreatedSubscriptionResponse;
import com.lumintra.backend.store.application.CreateSubscriptionUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CreateSubscriptionController {
    private final CreateSubscriptionUseCase useCase;

    @PostMapping("/api/management/admin/store/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an unpublished subscription product")
    public ApiResponse<CreatedSubscriptionResponse> create(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return ApiResponse.success(useCase.execute(request));
    }
}
