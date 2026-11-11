package com.lumintra.backend.store.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Public store product detail payload including common metadata and type-specific attributes")
@Builder
public record ProductDetailResponse(
        @Schema(description = "Unique product identifier", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,

        @Schema(description = "Product display name", example = "Den LED Matrix Lumintra v1", requiredMode = Schema.RequiredMode.REQUIRED)
        String productName,

        @Schema(description = "Detailed product marketing and feature description", example = "Compact 64x64 windshield LED matrix panel", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,

        @Schema(description = "Catalog product category type", example = "LED", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductType productType,

        @Schema(description = "Base selling price in VND", example = "200000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal basePrice,

        @Schema(description = "Publicly viewable lifecycle status (PUBLISHED or ARCHIVED)", example = "ARCHIVED", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductStatus status,

        @Schema(description = "Primary product cover image URL", example = "https://cdn.lumintra.com/products/led-v1.png", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String thumbnailUrl,

        @Schema(description = "Supplementary gallery slider image URLs", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> galleryUrls,

        @Schema(description = "Assigned catalog category tags", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TagItem> tags,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "LED hardware model specifications and stock (present when productType is LED)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        LedDetail ledDetails,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Character animation metadata excluding ledBinaryUrl (present when productType is CHARACTER)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CharacterDetail characterDetails,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Bundle package pricing, schedule, and itemized breakdown (present when productType is BUNDLE)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BundleDetail bundleDetails,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Subscription plan entitlement metadata (present when productType is SUBSCRIPTION)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        SubscriptionDetail subscriptionDetails,

        @Schema(description = "ISO-8601 UTC product creation timestamp", example = "2026-10-01T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {
    @Schema(description = "Category tag attached to the product")
    @Builder
    public record TagItem(
            @Schema(description = "Tag identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long tagId,

            @Schema(description = "Tag display name", example = "matrix", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(description = "Optional tag description", example = "LED matrix hardware", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String description
    ) {}

    @Schema(description = "Hardware specification and inventory details for an LED product")
    @Builder
    public record LedDetail(
            @Schema(description = "LED model identifier", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            Long modelId,

            @Schema(description = "LED hardware model name", example = "Matrix 64x64", requiredMode = Schema.RequiredMode.REQUIRED)
            String modelName,

            @Schema(description = "Technical specifications JSON object", example = "{\"dimensions\":\"64x64\",\"voltage\":\"5V\"}", requiredMode = Schema.RequiredMode.REQUIRED)
            Map<String, Object> specifications,

            @Schema(description = "Current available hardware stock count", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            Long stockQuantity
    ) {}

    @Schema(description = "Public animation preview details for a CHARACTER product (binary file URL excluded)")
    @Builder
    public record CharacterDetail(
            @Schema(description = "Character identifier", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            Long characterId,

            @Schema(description = "Character display name", example = "Cyber Dragon", requiredMode = Schema.RequiredMode.REQUIRED)
            String characterName,

            @Schema(description = "Character lore or artistic description", example = "Neon pixel dragon animation", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String description,

            @Schema(description = "Displayed release version number", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int versionNumber,

            @Schema(description = "Animated GIF preview URL", example = "https://cdn.lumintra.com/characters/dragon-preview.gif", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String previewGifUrl,

            @Schema(description = "Total animation frame count", example = "24", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer frameCount,

            @Schema(description = "Delay per animation frame in milliseconds", example = "80", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer frameDelayMs
    ) {}

    @Schema(description = "Bundle package details and included LED/Character components")
    @Builder
    public record BundleDetail(
            @Schema(description = "Bundle identifier", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            Long bundleId,

            @Schema(description = "Bundle promotional title", example = "Starter Neon Combo", requiredMode = Schema.RequiredMode.REQUIRED)
            String bundleName,

            @Schema(description = "Detailed description of the bundle package", example = "64x64 panel paired with 2 cyberpunk characters", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String description,

            @Schema(description = "Combined original price of components before discount in VND", example = "550000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            BigDecimal originalPrice,

            @Schema(description = "Promotional bundle selling price in VND", example = "450000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            BigDecimal bundlePrice,

            @Schema(description = "ISO-8601 UTC bundle campaign start timestamp", example = "2026-10-01T00:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant startAt,

            @Schema(description = "ISO-8601 UTC bundle campaign end timestamp", example = "2026-12-31T23:59:59Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant endAt,

            @Schema(description = "Itemized hardware and digital components included in this bundle", requiredMode = Schema.RequiredMode.REQUIRED)
            List<BundleItemDetail> items
    ) {}

    @Schema(description = "Single LED hardware or Character animation item included within a bundle")
    @Builder
    public record BundleItemDetail(
            @Schema(description = "Included bundle component type (LED or CHARACTER)", example = "LED", requiredMode = Schema.RequiredMode.REQUIRED)
            BundleItemType itemType,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included LED model identifier (for LED items)", example = "5", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long modelId,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included LED model name (for LED items)", example = "Matrix 64x64", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String modelName,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Current available hardware stock of the LED model (for LED items)", example = "12", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long stockQuantity,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included character version identifier (for CHARACTER items)", example = "21", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long versionId,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included character identifier (for CHARACTER items)", example = "10", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long characterId,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included character name (for CHARACTER items)", example = "Cyber Dragon", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String characterName,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Included character version number (for CHARACTER items)", example = "2", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer versionNumber,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Animated preview GIF URL of the character (for CHARACTER items)", example = "https://cdn.lumintra.com/characters/dragon-preview.gif", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String previewGifUrl,

            @Schema(description = "Quantity of this item included per bundle unit", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            int quantity
    ) {}

    @Schema(description = "Subscription plan entitlement attributes across store, commission, mobile app, and extensible features")
    @Builder
    public record SubscriptionDetail(
            @Schema(description = "Subscription detail identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long subscriptionDetailId,

            @Schema(description = "Unique canonical subscription plan code for cross-module entitlement checks", example = "VIP_LIFETIME", requiredMode = Schema.RequiredMode.REQUIRED)
            String planCode,

            @Schema(description = "Subscription plan display name", example = "Lumintra All-Access VIP Pass", requiredMode = Schema.RequiredMode.REQUIRED)
            String planName,

            @JsonProperty("isLifetime")
            @Schema(description = "Indicates whether entitlement validity is lifetime", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean isLifetime,

            @Schema(description = "Plan validity duration in days when isLifetime is false (null for lifetime plans)", example = "365", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer durationDays,

            @Schema(description = "Store benefit: grants unlimited access to all catalog characters", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean allAccessCharacters,

            @Schema(description = "Commission benefit: number of commission redraw/revision requests included", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            int maxCommissionRevisions,

            @Schema(description = "Mobile app benefit: maximum saved profile slots allowed on mobile", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            int maxMobileProfiles,

            @Schema(
                    description = "Extensible key-value map of additional multi-module feature flags and perks",
                    example = "{\"commissionPriorityQueue\":true,\"commissionDiscountPercent\":10,\"mobileCloudBackup\":true}",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Map<String, Object> features,

            @Schema(description = "Detailed list or summary of subscription plan perks", example = "Unlimited catalog characters, 3 free commission revisions, and 10 mobile saved profiles", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String perksDescription
    ) {}
}
