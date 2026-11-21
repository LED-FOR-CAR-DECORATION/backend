package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.CreateStoreProductRequest;
import com.lumintra.backend.store.api.response.CreatedStoreProductResponse;
import com.lumintra.backend.store.application.CreateStoreProductUseCase;
import com.lumintra.backend.store.application.ListCharacterStoreDraftsUseCase;
import com.lumintra.backend.store.domain.model.ProductType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DesignerStoreDraftController {
    private final CreateStoreProductUseCase createStoreProductUseCase;
    private final InternalEmployeeAccessService employeeAccess;
    private final ListCharacterStoreDraftsUseCase listCharacterStoreDraftsUseCase;

    @GetMapping("/api/management/admin/store/products/drafts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List unpublished character products for review", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<ListCharacterStoreDraftsUseCase.Draft>>> list() {
        return ResponseEntity.ok(ApiResponse.success(listCharacterStoreDraftsUseCase.execute()));
    }

    @PostMapping("/api/management/designer/store/drafts")
    @PreAuthorize("hasRole('DESIGNER')")
    @Operation(summary = "Submit a character version for Store review", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CreatedStoreProductResponse>> submit(
            Authentication authentication, @Valid @RequestBody CreateStoreProductRequest request) {
        long employeeId = employeeAccess.requireActiveEmployeeId(authentication.getName(), Set.of("DESIGNER"));
        if (request.productType() != ProductType.CHARACTER) {
            throw new AccessDeniedException("Designers can only submit character versions.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createStoreProductUseCase.submitDesignerDraft(request, employeeId)));
    }
}
