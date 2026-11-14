package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.ListStoreTagsUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Store Tags", description = "Public store category filters")
public class StoreTagController {
    private final ListStoreTagsUseCase useCase;

    @GetMapping("/api/store/tags")
    @Operation(
            summary = "List store category tags",
            description = "Returns all tags ordered by name and ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Category tags"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "Unable to load category tags")
    })
    public ApiResponse<StoreTagResponse[]> listTags() {
        return ApiResponse.success(useCase.execute());
    }
}
