package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.application.ListProductsUseCase;
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

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductListResponse.ProductItem>>> listProducts(
            @Valid @ModelAttribute ProductFilterRequest request) {
        ProductListResponse result = listProductsUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }
}
