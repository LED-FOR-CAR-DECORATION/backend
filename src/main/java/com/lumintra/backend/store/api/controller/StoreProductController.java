package com.lumintra.backend.store.api.controller;

import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.application.GetProductDetailUseCase;
import com.lumintra.backend.store.application.ListProductsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Store Products", description = "Public endpoints for browsing the store product catalog")
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreProductController {

    private final ListProductsUseCase listProductsUseCase;
    private final GetProductDetailUseCase getProductDetailUseCase;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductListResponse.ProductItem>>> listProducts(
            @Valid @ModelAttribute ProductFilterRequest request) {
        ProductListResponse result = listProductsUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

    @Operation(
            summary = "View public product detail",
            description = "Retrieves full public metadata and type-specific attributes (LED, CHARACTER, BUNDLE, SUBSCRIPTION) for a PUBLISHED or ARCHIVED store product. UNPUBLISHED and DELETED products return 404."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product detail retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid or non-positive product id path parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-11T10:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "id",
                                                  "message": "id must be greater than 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: product does not exist, is UNPUBLISHED, or is DELETED",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Product Not Found",
                                    value = """
                                            {
                                              "code": "PRODUCT_NOT_FOUND",
                                              "message": "Product not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-11T10:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(
            @Parameter(description = "Numeric store product identifier", example = "5")
            @PathVariable @Positive(message = "id must be greater than 0") Long id) {
        ProductDetailResponse response = getProductDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
