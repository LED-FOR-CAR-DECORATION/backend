package com.lumintra.backend.store;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.StoreProductController;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.application.GetProductDetailUseCase;
import com.lumintra.backend.store.application.ListProductsUseCase;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StoreProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class StoreProductDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListProductsUseCase listProductsUseCase;

    @MockitoBean
    private GetProductDetailUseCase getProductDetailUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("STR-02 UTCID01 [N]: Anonymous GET /api/store/products/{id} returns 200 OK with LED specifications and ARCHIVED status allowed")
    void getProductDetail_ledArchivedPublicAccess_returns200() throws Exception {
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(5L)
                .productName("Den LED Matrix Lumintra v1")
                .description("Compact 64x64 windshield LED panel")
                .productType(ProductType.LED)
                .basePrice(new BigDecimal("200000.00"))
                .status(ProductStatus.ARCHIVED)
                .thumbnailUrl("https://cdn.lumintra.com/products/led-v1.png")
                .galleryUrls(List.of("https://cdn.lumintra.com/products/led-1.png"))
                .tags(List.of(
                        ProductDetailResponse.TagItem.builder()
                                .tagId(1L)
                                .name("matrix")
                                .description("LED matrix hardware")
                                .build()
                ))
                .ledDetails(ProductDetailResponse.LedDetail.builder()
                        .modelId(5L)
                        .modelName("Matrix 64x64")
                        .specifications(Map.of("dimensions", "64x64", "voltage", "5V"))
                        .stockQuantity(0L)
                        .build())
                .createdAt(createdAt)
                .build();

        when(getProductDetailUseCase.execute(5L)).thenReturn(response);

        mockMvc.perform(get("/api/store/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(5))
                .andExpect(jsonPath("$.data.productName").value("Den LED Matrix Lumintra v1"))
                .andExpect(jsonPath("$.data.description").value("Compact 64x64 windshield LED panel"))
                .andExpect(jsonPath("$.data.productType").value("LED"))
                .andExpect(jsonPath("$.data.basePrice").value(200000.00))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.lumintra.com/products/led-v1.png"))
                .andExpect(jsonPath("$.data.galleryUrls[0]").value("https://cdn.lumintra.com/products/led-1.png"))
                .andExpect(jsonPath("$.data.tags[0].tagId").value(1))
                .andExpect(jsonPath("$.data.tags[0].name").value("matrix"))
                .andExpect(jsonPath("$.data.ledDetails.modelId").value(5))
                .andExpect(jsonPath("$.data.ledDetails.modelName").value("Matrix 64x64"))
                .andExpect(jsonPath("$.data.ledDetails.specifications.dimensions").value("64x64"))
                .andExpect(jsonPath("$.data.ledDetails.specifications.voltage").value("5V"))
                .andExpect(jsonPath("$.data.ledDetails.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.characterDetails").doesNotExist())
                .andExpect(jsonPath("$.data.bundleDetails").doesNotExist())
                .andExpect(jsonPath("$.data.subscriptionDetails").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T10:00:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(getProductDetailUseCase).execute(5L);
    }

    @Test
    @DisplayName("STR-02 UTCID02 [N]: GET /api/store/products/{id} returns 200 OK for CHARACTER without ledBinaryUrl (even with Customer JWT)")
    void getProductDetail_character_returns200WithoutBinaryUrl() throws Exception {
        Instant createdAt = Instant.parse("2026-10-02T08:00:00Z");
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(8L)
                .productName("Cyber Dragon")
                .description("Neon dragon pixel art")
                .productType(ProductType.CHARACTER)
                .basePrice(new BigDecimal("59000.00"))
                .status(ProductStatus.PUBLISHED)
                .thumbnailUrl("https://cdn.lumintra.com/products/dragon.png")
                .galleryUrls(List.of())
                .tags(List.of())
                .characterDetails(ProductDetailResponse.CharacterDetail.builder()
                        .characterId(10L)
                        .characterName("Cyber Dragon")
                        .description("Neon dragon animation lore")
                        .versionNumber(2)
                        .previewGifUrl("https://cdn.lumintra.com/characters/dragon-preview.gif")
                        .frameCount(24)
                        .frameDelayMs(80)
                        .build())
                .createdAt(createdAt)
                .build();

        when(getProductDetailUseCase.execute(8L)).thenReturn(response);

        mockMvc.perform(get("/api/store/products/8")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("CHARACTER"))
                .andExpect(jsonPath("$.data.characterDetails.characterId").value(10))
                .andExpect(jsonPath("$.data.characterDetails.characterName").value("Cyber Dragon"))
                .andExpect(jsonPath("$.data.characterDetails.versionNumber").value(2))
                .andExpect(jsonPath("$.data.characterDetails.previewGifUrl")
                        .value("https://cdn.lumintra.com/characters/dragon-preview.gif"))
                .andExpect(jsonPath("$.data.characterDetails.frameCount").value(24))
                .andExpect(jsonPath("$.data.characterDetails.frameDelayMs").value(80))
                .andExpect(jsonPath("$.data.characterDetails.ledBinaryUrl").doesNotExist())
                .andExpect(jsonPath("$.data.ledDetails").doesNotExist());

        verify(getProductDetailUseCase).execute(8L);
    }

    @Test
    @DisplayName("STR-02 UTCID03 [N]: GET /api/store/products/{id} returns 200 OK for BUNDLE with prices, campaign window, and LED/Character items")
    void getProductDetail_bundle_returns200WithFullBundleBreakdown() throws Exception {
        Instant createdAt = Instant.parse("2026-10-03T10:00:00Z");
        Instant startAt = Instant.parse("2026-10-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-12-31T23:59:59Z");

        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(12L)
                .productName("Starter Neon Combo")
                .description("Bundle landing description")
                .productType(ProductType.BUNDLE)
                .basePrice(new BigDecimal("450000.00"))
                .status(ProductStatus.PUBLISHED)
                .thumbnailUrl("https://cdn.lumintra.com/products/bundle.png")
                .galleryUrls(List.of("https://cdn.lumintra.com/products/bundle-1.png"))
                .tags(List.of())
                .bundleDetails(ProductDetailResponse.BundleDetail.builder()
                        .bundleId(3L)
                        .bundleName("Starter Neon Combo")
                        .description("64x64 panel + Cyber Dragon")
                        .originalPrice(new BigDecimal("550000.00"))
                        .bundlePrice(new BigDecimal("450000.00"))
                        .startAt(startAt)
                        .endAt(endAt)
                        .items(List.of(
                                ProductDetailResponse.BundleItemDetail.builder()
                                        .itemType(BundleItemType.LED)
                                        .modelId(5L)
                                        .modelName("Matrix 64x64")
                                        .stockQuantity(12L)
                                        .quantity(1)
                                        .build(),
                                ProductDetailResponse.BundleItemDetail.builder()
                                        .itemType(BundleItemType.CHARACTER)
                                        .versionId(21L)
                                        .characterId(10L)
                                        .characterName("Cyber Dragon")
                                        .versionNumber(2)
                                        .previewGifUrl("https://cdn.lumintra.com/characters/dragon-preview.gif")
                                        .quantity(2)
                                        .build()
                        ))
                        .build())
                .createdAt(createdAt)
                .build();

        when(getProductDetailUseCase.execute(12L)).thenReturn(response);

        mockMvc.perform(get("/api/store/products/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("BUNDLE"))
                .andExpect(jsonPath("$.data.bundleDetails.bundleId").value(3))
                .andExpect(jsonPath("$.data.bundleDetails.bundleName").value("Starter Neon Combo"))
                .andExpect(jsonPath("$.data.bundleDetails.description").value("64x64 panel + Cyber Dragon"))
                .andExpect(jsonPath("$.data.bundleDetails.originalPrice").value(550000.00))
                .andExpect(jsonPath("$.data.bundleDetails.bundlePrice").value(450000.00))
                .andExpect(jsonPath("$.data.bundleDetails.startAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.bundleDetails.endAt").value("2026-12-31T23:59:59Z"))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].itemType").value("LED"))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].modelId").value(5))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].modelName").value("Matrix 64x64"))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].stockQuantity").value(12))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.bundleDetails.items[0].versionId").doesNotExist())
                .andExpect(jsonPath("$.data.bundleDetails.items[1].itemType").value("CHARACTER"))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].versionId").value(21))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].characterId").value(10))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].characterName").value("Cyber Dragon"))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].versionNumber").value(2))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].previewGifUrl")
                        .value("https://cdn.lumintra.com/characters/dragon-preview.gif"))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].quantity").value(2))
                .andExpect(jsonPath("$.data.bundleDetails.items[1].ledBinaryUrl").doesNotExist())
                .andExpect(jsonPath("$.data.bundleDetails.items[1].modelId").doesNotExist());

        verify(getProductDetailUseCase).execute(12L);
    }

    @Test
    @DisplayName("STR-02 UTCID04 [N]: GET /api/store/products/{id} returns 200 OK for SUBSCRIPTION with store, commission, mobile quotas, and extensible features")
    void getProductDetail_subscription_returns200WithIsLifetimeProperty() throws Exception {
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(99L)
                .productName("Lumintra All-Access VIP Pass")
                .description("Unlock catalog characters, extra commission revisions, and mobile profiles")
                .productType(ProductType.SUBSCRIPTION)
                .basePrice(new BigDecimal("990000.00"))
                .status(ProductStatus.PUBLISHED)
                .galleryUrls(List.of())
                .tags(List.of())
                .subscriptionDetails(ProductDetailResponse.SubscriptionDetail.builder()
                        .subscriptionDetailId(7L)
                        .planCode("VIP_LIFETIME")
                        .planName("Lumintra All-Access VIP Pass")
                        .isLifetime(true)
                        .durationDays(null)
                        .allAccessCharacters(true)
                        .maxCommissionRevisions(5)
                        .maxMobileProfiles(10)
                        .features(Map.of(
                                "commissionPriorityQueue", true,
                                "commissionDiscountPercent", 15,
                                "mobileCloudBackup", true
                        ))
                        .perksDescription("Unlimited access to catalog characters, 5 commission revisions, and 10 mobile profiles")
                        .build())
                .createdAt(Instant.parse("2026-10-04T09:00:00Z"))
                .build();

        when(getProductDetailUseCase.execute(99L)).thenReturn(response);

        mockMvc.perform(get("/api/store/products/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.data.subscriptionDetails.subscriptionDetailId").value(7))
                .andExpect(jsonPath("$.data.subscriptionDetails.planCode").value("VIP_LIFETIME"))
                .andExpect(jsonPath("$.data.subscriptionDetails.planName").value("Lumintra All-Access VIP Pass"))
                .andExpect(jsonPath("$.data.subscriptionDetails.isLifetime").value(true))
                .andExpect(jsonPath("$.data.subscriptionDetails.lifetime").doesNotExist())
                .andExpect(jsonPath("$.data.subscriptionDetails.durationDays").isEmpty())
                .andExpect(jsonPath("$.data.subscriptionDetails.allAccessCharacters").value(true))
                .andExpect(jsonPath("$.data.subscriptionDetails.maxCommissionRevisions").value(5))
                .andExpect(jsonPath("$.data.subscriptionDetails.maxMobileProfiles").value(10))
                .andExpect(jsonPath("$.data.subscriptionDetails.features.commissionPriorityQueue").value(true))
                .andExpect(jsonPath("$.data.subscriptionDetails.features.commissionDiscountPercent").value(15))
                .andExpect(jsonPath("$.data.subscriptionDetails.features.mobileCloudBackup").value(true))
                .andExpect(jsonPath("$.data.subscriptionDetails.perksDescription")
                        .value("Unlimited access to catalog characters, 5 commission revisions, and 10 mobile profiles"));

        verify(getProductDetailUseCase).execute(99L);
    }

    @Test
    @DisplayName("STR-02 UTCID05 [B]: GET /api/store/products/{id} rejects non-positive and non-numeric path ids with 400 VALIDATION_FAILED")
    void getProductDetail_invalidPathId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/store/products/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        mockMvc.perform(get("/api/store/products/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        mockMvc.perform(get("/api/store/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        verifyNoInteractions(getProductDetailUseCase);
    }

    @Test
    @DisplayName("STR-02 UTCID06 [A]: GET /api/store/products/{id} maps ProductNotFoundException to 404 and unexpected failures to 500")
    void getProductDetail_notFoundAndUnexpectedErrors_returnStandardErrorResponse() throws Exception {
        when(getProductDetailUseCase.execute(999L))
                .thenThrow(new ProductNotFoundException("Product not found."));

        mockMvc.perform(get("/api/store/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found."))
                .andExpect(jsonPath("$.status").value(404));

        when(getProductDetailUseCase.execute(1000L))
                .thenThrow(new IllegalStateException("database connection leaked detail"));

        mockMvc.perform(get("/api/store/products/1000"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
