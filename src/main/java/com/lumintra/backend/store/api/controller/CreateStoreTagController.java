package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.CreateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.CreateStoreTagUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Store Tag Management")
public class CreateStoreTagController {
    private final CreateStoreTagUseCase useCase;

    @PostMapping("/api/management/admin/store/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a category tag")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Tag created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid tag name"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN or MANAGER required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "TAG_ALREADY_EXISTS")
    })
    public ApiResponse<StoreTagResponse> create(@Valid @RequestBody CreateStoreTagRequest request) {
        return ApiResponse.success(useCase.execute(request));
    }
}
