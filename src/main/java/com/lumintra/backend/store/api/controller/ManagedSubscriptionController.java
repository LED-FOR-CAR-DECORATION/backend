package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.response.ManagedSubscriptionResponse;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManagedSubscriptionController {
    private final SubscriptionDetailRepository subscriptions;

    @GetMapping("/api/management/admin/store/subscriptions")
    @PreAuthorize("hasRole('MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List configured subscription plans")
    public ApiResponse<List<ManagedSubscriptionResponse>> list() {
        return ApiResponse.success(subscriptions.listManagedPlans());
    }
}
