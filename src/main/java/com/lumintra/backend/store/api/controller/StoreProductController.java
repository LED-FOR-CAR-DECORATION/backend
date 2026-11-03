package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductSummaryResponse;
import com.lumintra.backend.store.application.ListProductsUseCase;
import com.lumintra.backend.store.application.ProductPageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreProductController {

    private final ListProductsUseCase listProductsUseCase;

    @GetMapping({"/listings", "/products"})
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> listProducts(
            @Valid @ModelAttribute ProductFilterRequest request) {
        ProductPageResult result = listProductsUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }
}
