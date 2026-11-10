package com.lumintra.backend.store;

import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.InvalidPriceRangeException;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.controller.StoreProductController;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.application.ListProductsUseCase;
import com.lumintra.backend.store.domain.model.ProductType;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoreProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ListProductsUseCase listProductsUseCase;

    @Mock
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        StoreProductController controller = new StoreProductController(listProductsUseCase);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(tracer);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    @DisplayName("GET /api/store/products - Returns 200 with standard response envelope and meta")
    void shouldReturn200WithEnvelopeForProducts() throws Exception {
        ProductListResponse.ProductItem.TagItem tag = ProductListResponse.ProductItem.TagItem.builder()
                .tagId(1L)
                .name("Cyberpunk")
                .description("Cyberpunk theme")
                .build();

        ProductListResponse.ProductItem item = ProductListResponse.ProductItem.builder()
                .productId(1L)
                .productName("LED Panel 16x16")
                .description("High brightness matrix")
                .productType(ProductType.LED)
                .basePrice(new BigDecimal("300000.00"))
                .thumbnailUrl("https://img.lumintra.shop/matrix16.png")
                .galleryUrls(List.of("https://img.lumintra.shop/matrix16-1.png"))
                .tags(List.of(tag))
                .createdAt(Instant.now())
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listProductsUseCase.execute(any(ProductFilterRequest.class)))
                .thenReturn(new ProductListResponse(List.of(item), meta));

        mockMvc.perform(get("/api/store/products")
                        .param("page", "0")
                        .param("size", "20")
                        .param("productType", "LED")
                        .param("categoryTag", "cyberpunk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productId").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("LED Panel 16x16"))
                .andExpect(jsonPath("$.data[0].productType").value("LED"))
                .andExpect(jsonPath("$.data[0].tags[0].name").value("Cyberpunk"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/store/products - Returns 200 with default pagination parameters")
    void shouldReturn200WithDefaultParametersForProducts() throws Exception {
        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listProductsUseCase.execute(any(ProductFilterRequest.class)))
                .thenReturn(new ProductListResponse(List.of(), meta));

        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(0));
    }

    @Test
    @DisplayName("GET /api/store/products - Filters by SUBSCRIPTION product type")
    void shouldAcceptSubscriptionProductType() throws Exception {
        ProductListResponse.ProductItem item = ProductListResponse.ProductItem.builder()
                .productId(99L)
                .productName("VIP Pass")
                .productType(ProductType.SUBSCRIPTION)
                .basePrice(new BigDecimal("990000.00"))
                .tags(List.of())
                .createdAt(Instant.now())
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listProductsUseCase.execute(any(ProductFilterRequest.class)))
                .thenReturn(new ProductListResponse(List.of(item), meta));

        mockMvc.perform(get("/api/store/products")
                        .param("productType", "SUBSCRIPTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productType").value("SUBSCRIPTION"));
    }

    @Test
    @DisplayName("GET /api/store/products - Validation failure returns 400 with VALIDATION_FAILED code")
    void shouldReturn400OnValidationFailure() throws Exception {
        mockMvc.perform(get("/api/store/products")
                        .param("minPrice", "-1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("minPrice"));
    }

    @Test
    @DisplayName("GET /api/store/products - Custom DTO validation failure when minPrice > maxPrice")
    void shouldReturn400WhenMinPriceGreaterThanMaxPriceViaDtoValidation() throws Exception {
        mockMvc.perform(get("/api/store/products")
                        .param("minPrice", "500000")
                        .param("maxPrice", "200000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("minPrice"));
    }

    @Test
    @DisplayName("GET /api/store/products - InvalidPriceRangeException returns 400 with INVALID_PRICE_RANGE code")
    void shouldReturn400OnInvalidPriceRangeException() throws Exception {
        when(listProductsUseCase.execute(any(ProductFilterRequest.class)))
                .thenThrow(new InvalidPriceRangeException("Minimum price cannot be greater than maximum price"));

        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
    }
}
