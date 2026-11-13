package com.lumintra.backend.store.infrastructure.bootstrap;

import com.lumintra.backend.character.domain.service.CharacterCreationService;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seeds representative store tags, products, hardware models, character versions, bundles,
 * and subscription plans before {@code CommerceDataSeeder} runs.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class StoreDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreDataSeeder.class);

    public static final String TAG_MATRIX = "matrix";
    public static final String TAG_CYBERPUNK = "cyberpunk";
    public static final String TAG_BUNDLE = "bundle";
    public static final String TAG_SUBSCRIPTION = "subscription";

    public static final String PRODUCT_LED_PRO = "Lumintra Matrix 64x64 Pro";
    public static final String PRODUCT_LED_COMPACT = "Lumintra Matrix 32x64 Compact";
    public static final String PRODUCT_LED_PROTOTYPE = "Lumintra Matrix 64x64 Prototype";
    public static final String PRODUCT_CHAR_CAT = "Cyberpunk Cat";
    public static final String PRODUCT_CHAR_DRAGON = "Cyber Dragon";
    public static final String PRODUCT_CHAR_MASCOT = "Lumintra Classic Mascot";
    public static final String PRODUCT_BUNDLE_STARTER = "Starter Neon Combo";
    public static final String PRODUCT_SUB_VIP = "Lumintra All-Access VIP Pass";
    public static final String SUBSCRIPTION_PLAN_CODE_VIP = "VIP_LIFETIME";

    private final TagRepository tagRepository;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;
    private final SubscriptionDetailRepository subscriptionDetailRepository;
    private final LedModelCreationService ledModelCreationService;
    private final LedModelLookupService ledModelLookupService;
    private final CharacterCreationService characterCreationService;
    private final CharacterLookupService characterLookupService;
    private final StoreCacheEvictor storeCacheEvictor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base Store catalog seed data...");
        AtomicBoolean seededAny = new AtomicBoolean(false);

        TagEntity matrixTag = ensureTag(TAG_MATRIX, "LED matrix hardware panels", seededAny);
        TagEntity cyberpunkTag = ensureTag(TAG_CYBERPUNK, "Cyberpunk and neon pixel animations", seededAny);
        TagEntity bundleTag = ensureTag(TAG_BUNDLE, "Hardware and character combo packages", seededAny);
        TagEntity subscriptionTag = ensureTag(TAG_SUBSCRIPTION, "All-access membership plans", seededAny);

        ProductEntity ledPro = seedLedProduct(
                PRODUCT_LED_PRO,
                new BigDecimal("250000.00"),
                "Flagship 64x64 full-color windshield LED matrix panel with BLE and Wi-Fi control.",
                "https://cdn.lumintra.com/products/matrix-64x64-pro.png",
                List.of(
                        "https://cdn.lumintra.com/products/matrix-64x64-pro-1.png",
                        "https://cdn.lumintra.com/products/matrix-64x64-pro-2.png"
                ),
                ProductStatus.PUBLISHED,
                Set.of(matrixTag),
                buildLedSpec("64x64", "192x192x18mm", "P3", "5V USB-C", "BLE + Wi-Fi", 1200),
                50L,
                seededAny
        );

        seedLedProduct(
                PRODUCT_LED_COMPACT,
                new BigDecimal("190000.00"),
                "Compact 32x64 rear-window LED matrix display for quick emoji and text alerts.",
                "https://cdn.lumintra.com/products/matrix-32x64-compact.png",
                List.of("https://cdn.lumintra.com/products/matrix-32x64-compact-1.png"),
                ProductStatus.PUBLISHED,
                Set.of(matrixTag),
                buildLedSpec("32x64", "96x192x18mm", "P3", "5V USB-C", "BLE + Wi-Fi", 1000),
                30L,
                seededAny
        );

        seedLedProduct(
                PRODUCT_LED_PROTOTYPE,
                new BigDecimal("299000.00"),
                "Upcoming high-brightness prototype panel staged as unpublished.",
                "https://cdn.lumintra.com/products/matrix-64x64-prototype.png",
                List.of(),
                ProductStatus.UNPUBLISHED,
                Set.of(matrixTag),
                buildLedSpec("64x64", "192x192x16mm", "P2.5", "5V USB-C", "BLE + Wi-Fi", 1500),
                5L,
                seededAny
        );

        seedCharacterProduct(
                PRODUCT_CHAR_CAT,
                new BigDecimal("50000.00"),
                "Animated neon pixel cat with holographic visor glow.",
                "https://cdn.lumintra.com/characters/cyberpunk-cat-preview.gif",
                "https://cdn.lumintra.com/characters/cyberpunk-cat-v1.bin",
                24,
                80,
                196608,
                ProductStatus.PUBLISHED,
                Set.of(cyberpunkTag),
                seededAny
        );

        long dragonVersionId = seedCharacterProduct(
                PRODUCT_CHAR_DRAGON,
                new BigDecimal("75000.00"),
                "Animated neon fire dragon optimized for 64x64 panels.",
                "https://cdn.lumintra.com/characters/cyber-dragon-preview.gif",
                "https://cdn.lumintra.com/characters/cyber-dragon-v1.bin",
                30,
                70,
                245760,
                ProductStatus.PUBLISHED,
                Set.of(cyberpunkTag),
                seededAny
        );

        seedCharacterProduct(
                PRODUCT_CHAR_MASCOT,
                new BigDecimal("40000.00"),
                "Classic Lumintra starter mascot animation from the legacy launch wave.",
                "https://cdn.lumintra.com/characters/classic-mascot-preview.gif",
                "https://cdn.lumintra.com/characters/classic-mascot-v1.bin",
                16,
                100,
                131072,
                ProductStatus.ARCHIVED,
                Set.of(cyberpunkTag),
                seededAny
        );

        seedBundleProduct(
                ledPro.getProductId(),
                dragonVersionId,
                Set.of(matrixTag, cyberpunkTag, bundleTag),
                seededAny
        );

        seedSubscriptionProduct(Set.of(subscriptionTag), seededAny);

        if (seededAny.get()) {
            storeCacheEvictor.evictAllProducts();
        }
        log.info("Store catalog seed data verification complete.");
    }

    private TagEntity ensureTag(String name, String description, AtomicBoolean seededAny) {
        return tagRepository.findByName(name).orElseGet(() -> {
            TagEntity saved = tagRepository.save(TagEntity.create(name, description));
            seededAny.set(true);
            log.info("Seeded store tag [tagId={}] [name={}]", saved.getTagId(), name);
            return saved;
        });
    }

    private ProductEntity seedLedProduct(
            String productName,
            BigDecimal basePrice,
            String description,
            String thumbnailUrl,
            List<String> galleryUrls,
            ProductStatus status,
            Set<TagEntity> tags,
            Map<String, Object> spec,
            long stockQuantity,
            AtomicBoolean seededAny) {
        ProductEntity product = findOrCreateProduct(
                productName,
                ProductType.LED,
                basePrice,
                description,
                thumbnailUrl,
                galleryUrls,
                status,
                tags,
                seededAny
        );

        if (ledModelLookupService.findByModelId(product.getProductId()).isEmpty()) {
            ledModelCreationService.createForProduct(product.getProductId(), productName, spec, stockQuantity);
            seededAny.set(true);
            log.info("Seeded LED hardware model [modelId={}] [stockQuantity={}]",
                    product.getProductId(), stockQuantity);
        }
        return product;
    }

    private long seedCharacterProduct(
            String productName,
            BigDecimal basePrice,
            String description,
            String previewGifUrl,
            String ledBinaryUrl,
            int frameCount,
            int frameDelayMs,
            int byteSize,
            ProductStatus status,
            Set<TagEntity> tags,
            AtomicBoolean seededAny) {
        ProductEntity product = findOrCreateProduct(
                productName,
                ProductType.CHARACTER,
                basePrice,
                description,
                previewGifUrl,
                List.of(previewGifUrl),
                status,
                tags,
                seededAny
        );

        return characterLookupService.findVersionIdByProductId(product.getProductId()).orElseGet(() -> {
            long versionId = characterCreationService.createForProduct(
                    product.getProductId(),
                    productName,
                    description,
                    1,
                    previewGifUrl,
                    ledBinaryUrl,
                    frameCount,
                    frameDelayMs,
                    byteSize
            );
            seededAny.set(true);
            log.info("Seeded character version [productId={}] [versionId={}] [name={}]",
                    product.getProductId(), versionId, productName);
            return versionId;
        });
    }

    private void seedBundleProduct(
            Long ledModelId,
            Long characterVersionId,
            Set<TagEntity> tags,
            AtomicBoolean seededAny) {
        ProductEntity product = findOrCreateProduct(
                PRODUCT_BUNDLE_STARTER,
                ProductType.BUNDLE,
                new BigDecimal("280000.00"),
                "64x64 LED Matrix panel paired with the animated Cyber Dragon pack.",
                "https://cdn.lumintra.com/products/starter-neon-combo.png",
                List.of("https://cdn.lumintra.com/products/starter-neon-combo-1.png"),
                ProductStatus.PUBLISHED,
                tags,
                seededAny
        );

        if (bundleRepository.findByProductId(product.getProductId()).isEmpty()) {
            BundleEntity bundle = bundleRepository.save(BundleEntity.create(
                    product.getProductId(),
                    PRODUCT_BUNDLE_STARTER,
                    "64x64 LED Matrix panel paired with Cyber Dragon animation",
                    new BigDecimal("325000.00"),
                    new BigDecimal("280000.00"),
                    ProductStatus.PUBLISHED.name(),
                    Instant.now().minus(7, ChronoUnit.DAYS),
                    null
            ));
            bundleItemRepository.save(BundleItemEntity.createLedItem(bundle, ledModelId, 1));
            bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, characterVersionId, 1));
            seededAny.set(true);
            log.info("Seeded bundle product [productId={}] [bundleId={}]",
                    product.getProductId(), bundle.getBundleId());
        }
    }

    private void seedSubscriptionProduct(Set<TagEntity> tags, AtomicBoolean seededAny) {
        ProductEntity product = findOrCreateProduct(
                PRODUCT_SUB_VIP,
                ProductType.SUBSCRIPTION,
                new BigDecimal("99000.00"),
                "Lifetime all-access pass across catalog characters, commissions, and mobile slots.",
                "https://cdn.lumintra.com/products/vip-lifetime-pass.png",
                List.of(),
                ProductStatus.PUBLISHED,
                tags,
                seededAny
        );

        boolean detailMissing = subscriptionDetailRepository.findByProductId(product.getProductId()).isEmpty()
                && subscriptionDetailRepository.findByPlanCode(SUBSCRIPTION_PLAN_CODE_VIP).isEmpty();
        if (detailMissing) {
            subscriptionDetailRepository.save(SubscriptionDetailEntity.create(
                    product.getProductId(),
                    SUBSCRIPTION_PLAN_CODE_VIP,
                    PRODUCT_SUB_VIP,
                    true,
                    null,
                    true,
                    3,
                    10,
                    Map.of(
                            "commissionPriorityQueue", true,
                            "commissionDiscountPercent", 10,
                            "mobileCloudBackup", true
                    ),
                    "Unlimited catalog characters, 3 free commission revisions, and 10 mobile saved profiles"
            ));
            seededAny.set(true);
            log.info("Seeded subscription detail [productId={}] [planCode={}]",
                    product.getProductId(), SUBSCRIPTION_PLAN_CODE_VIP);
        }
    }

    private ProductEntity findOrCreateProduct(
            String productName,
            ProductType productType,
            BigDecimal basePrice,
            String description,
            String thumbnailUrl,
            List<String> galleryUrls,
            ProductStatus status,
            Set<TagEntity> tags,
            AtomicBoolean seededAny) {
        return productRepository.findFirstByProductName(productName).orElseGet(() -> {
            ProductEntity saved = productRepository.save(ProductEntity.create(
                    productName,
                    productType,
                    basePrice,
                    description,
                    thumbnailUrl,
                    galleryUrls,
                    status,
                    tags
            ));
            seededAny.set(true);
            log.info("Seeded store product [productId={}] [productName={}] [type={}] [status={}]",
                    saved.getProductId(), productName, productType, status);
            return saved;
        });
    }

    private Map<String, Object> buildLedSpec(
            String resolution,
            String dimensionsMm,
            String pixelPitch,
            String voltage,
            String connectivity,
            int brightnessNits) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("resolution", resolution);
        spec.put("dimensionsMm", dimensionsMm);
        spec.put("pixelPitch", pixelPitch);
        spec.put("voltage", voltage);
        spec.put("connectivity", connectivity);
        spec.put("brightnessNits", brightnessNits);
        return spec;
    }
}
