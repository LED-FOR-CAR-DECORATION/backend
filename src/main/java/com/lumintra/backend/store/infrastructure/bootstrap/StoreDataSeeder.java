package com.lumintra.backend.store.infrastructure.bootstrap;

import com.lumintra.backend.character.domain.service.CharacterCreationService;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seeds representative store tags, LED hardware products, 10 character
 * products,
 * bundle combos, and subscription plans before {@code CommerceDataSeeder} runs.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class StoreDataSeeder implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(StoreDataSeeder.class);

        public static final String TAG_MATRIX = "Tấm LED";
        public static final String TAG_CYBERPUNK = "Ánh sáng Neon";
        public static final String TAG_BUNDLE = "Combo tiết kiệm";
        public static final String TAG_SUBSCRIPTION = "Gói hội viên";
        public static final String TAG_PIXEL_ART = "Tranh Pixel";
        public static final String TAG_ANIMAL = "Động vật";
        public static final String TAG_ANIME = "Anime";
        public static final String TAG_ANIMATED = "Ảnh động";
        public static final String TAG_CUTE = "Dễ thương";
        public static final String TAG_ZEN = "Thiền Nhật";
        public static final String TAG_HALLOWEEN = "Halloween";
        public static final String TAG_STATIC = "Ảnh tĩnh";

        public static final String PRODUCT_LED_PRO = "Tấm LED Lumintra 64x64 Cao Cấp";
        public static final String PRODUCT_LED_COMPACT = "Tấm LED Lumintra 64x32 Gọn Nhẹ";
        public static final String PRODUCT_LED_MINI = "Tấm LED Lumintra 32x16 Nhỏ Gọn";

        public static final String PRODUCT_CHAR_01 = "Heo Hồng Cổ Điển";
        public static final String PRODUCT_CHAR_02 = "Capybara Ôm Táo Đỏ";
        public static final String PRODUCT_CHAR_03 = "Capybara Nhâm Nhi Táo";
        public static final String PRODUCT_CHAR_04 = "Cô Nàng Tinh Nghịch";
        public static final String PRODUCT_CHAR_05 = "Nezuko (Thanh Gươm Diệt Quỷ)";
        public static final String PRODUCT_CHAR_06 = "Ống Trúc Thiền Nhật Bản";
        public static final String PRODUCT_CHAR_07 = "Thiếu Nữ Kimono Viết Thơ";
        public static final String PRODUCT_CHAR_08 = "Lâu Đài Ma Ám Halloween";
        public static final String PRODUCT_CHAR_09 = "Maomao (Dược Sư Tự Sự)";
        public static final String PRODUCT_CHAR_10 = "Sakura Miku Hoa Anh Đào";

        public static final String PRODUCT_BUNDLE_STARTER = "Combo Khởi Đầu";
        public static final String PRODUCT_BUNDLE_ANIME_TRIO = "Combo Bộ Ba Nhân Vật Anime";
        public static final String PRODUCT_SUB_VIP = "Gói Hội Viên Mở Khóa Toàn Bộ Nhân Vật";
        public static final String PRODUCT_SUB_STANDARD_30D = "Gói Hội Viên Tiêu Chuẩn 30 Ngày";
        public static final String PRODUCT_SUB_PRO_30D = "Gói Hội Viên Mở Rộng 30 Ngày";
        public static final String PRODUCT_SUB_ULTIMATE_LIFETIME = "Gói Hội Viên Toàn Diện Trọn Đời";
        public static final String SUBSCRIPTION_PLAN_CODE_VIP = "VIP_LIFETIME";
        public static final String SUBSCRIPTION_PLAN_CODE_STANDARD_30D = "VIP_STANDARD_30D";
        public static final String SUBSCRIPTION_PLAN_CODE_PRO_30D = "VIP_PRO_30D";
        public static final String SUBSCRIPTION_PLAN_CODE_ULTIMATE_LIFETIME = "VIP_ULTIMATE_LIFETIME";

        private static final List<TagSeedSpec> TAG_SEEDS = List.of(
                        new TagSeedSpec(TAG_MATRIX, "Tấm LED hiển thị phần cứng"),
                        new TagSeedSpec(TAG_CYBERPUNK, "Phong cách hiện đại và ánh sáng neon nổi bật"),
                        new TagSeedSpec(TAG_BUNDLE, "Combo kết hợp tấm LED và nhân vật hiển thị"),
                        new TagSeedSpec(TAG_SUBSCRIPTION, "Gói hội viên mở khóa quyền lợi"),
                        new TagSeedSpec(TAG_PIXEL_ART, "Tranh và hoạt ảnh pixel vẽ thủ công"),
                        new TagSeedSpec(TAG_ANIMAL, "Thú cưng và động vật đáng yêu"),
                        new TagSeedSpec(TAG_ANIME, "Nhân vật hoạt hình Nhật Bản nổi tiếng"),
                        new TagSeedSpec(TAG_ANIMATED, "Hoạt ảnh động nhiều khung hình sống động"),
                        new TagSeedSpec(TAG_CUTE, "Phong cách dễ thương, màu dịu nhẹ và thư giãn"),
                        new TagSeedSpec(TAG_ZEN, "Không gian Nhật Bản và thiên nhiên yên bình"),
                        new TagSeedSpec(TAG_HALLOWEEN, "Chủ đề lễ hội hóa trang huyền bí"),
                        new TagSeedSpec(TAG_STATIC, "Ảnh tĩnh sắc nét, độ tương phản cao"));

        public static final List<CharacterSeedSpec> CHARACTER_SEEDS = List.of(
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_01,
                                        new BigDecimal("25000.00"),
                                        "Tranh pixel chú heo hồng má phấn đáng yêu phong cách cổ điển 16x16 trên nền trời xanh cỏ non.",
                                        "https://cdn.lumintra.shop/characters/1/Con_lon.jpg",
                                        "https://cdn.lumintra.shop/characters/1/con-lon-v1.bin",
                                        1,
                                        0,
                                        98131,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIMAL, TAG_CUTE, TAG_STATIC)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_02,
                                        new BigDecimal("30000.00"),
                                        "Chú Capybara thư giãn ôm trái táo đỏ rực rỡ, hiển thị sắc nét trên tấm LED ô tô.",
                                        "https://cdn.lumintra.shop/characters/2/Capypara.jpg",
                                        "https://cdn.lumintra.shop/characters/2/capypara-v1.bin",
                                        1,
                                        0,
                                        77093,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIMAL, TAG_CUTE, TAG_STATIC)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_03,
                                        new BigDecimal("49000.00"),
                                        "Hoạt ảnh động 9 khung hình chú Capybara gặm táo đỏ nhịp nhàng, mang lại cảm giác thư thái khi lái xe.",
                                        "https://cdn.lumintra.shop/characters/3/Capybara%20apple-1.gif",
                                        "https://cdn.lumintra.shop/characters/3/capybara-apple-v1.bin",
                                        9,
                                        260,
                                        43811,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIMAL, TAG_CUTE, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_04,
                                        new BigDecimal("69000.00"),
                                        "Chân dung hoạt ảnh pixel 16 khung hình thiếu nữ tóc nâu ánh mắt tinh nghịch với biểu cảm nháy mắt sinh động.",
                                        "https://cdn.lumintra.shop/characters/4/mischievous%20girl-2.gif",
                                        "https://cdn.lumintra.shop/characters/4/mischievous-girl-v1.bin",
                                        16,
                                        500,
                                        263429,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIME, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_05,
                                        new BigDecimal("99000.00"),
                                        "Hoạt ảnh pixel cao cấp 37 khung hình siêu mượt lấy cảm hứng từ Nezuko (Thanh Gươm Diệt Quỷ) cùng hiệu ứng ánh sáng huyền ảo.",
                                        "https://cdn.lumintra.shop/characters/5/Nezuko%20Demon%20slayer.gif",
                                        "https://cdn.lumintra.shop/characters/5/nezuko-demon-slayer-v1.bin",
                                        37,
                                        50,
                                        777900,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIME, TAG_ANIMATED, TAG_CYBERPUNK)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_06,
                                        new BigDecimal("59000.00"),
                                        "Cảnh suối nước chảy qua ống trúc thiền và thảm rêu xanh Nhật Bản 16 khung hình tạo cảm giác tĩnh tại.",
                                        "https://cdn.lumintra.shop/characters/6/Bamboo.gif",
                                        "https://cdn.lumintra.shop/characters/6/bamboo-v1.bin",
                                        16,
                                        150,
                                        258032,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ZEN, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_07,
                                        new BigDecimal("55000.00"),
                                        "Hoạt ảnh cô gái mặc kimono đỏ ngồi viết thơ bên khung cửa sổ nhìn ra đỉnh núi Phú Sĩ thanh bình.",
                                        "https://cdn.lumintra.shop/characters/7/Haiku%20Day.gif",
                                        "https://cdn.lumintra.shop/characters/7/haiku-day-v1.bin",
                                        4,
                                        300,
                                        48715,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIME, TAG_ZEN, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_08,
                                        new BigDecimal("55000.00"),
                                        "Khung cảnh đêm Halloween cổ điển với tòa lâu đài ánh cam ma mị, đèn bí ngô rực sáng và dơi bay lượn.",
                                        "https://cdn.lumintra.shop/characters/8/Haunted2026%20Halloween-Mansion.gif",
                                        "https://cdn.lumintra.shop/characters/8/haunted-halloween-mansion-v1.bin",
                                        4,
                                        200,
                                        76109,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_HALLOWEEN, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_09,
                                        new BigDecimal("65000.00"),
                                        "Chân dung chibi Maomao (Dược Sư Tự Sự) với mái tóc xanh lục bảo đặc trưng và hoạt ảnh chớp mắt long lanh.",
                                        "https://cdn.lumintra.shop/characters/9/Maomao.gif",
                                        "https://cdn.lumintra.shop/characters/9/maomao-v1.bin",
                                        6,
                                        230,
                                        58806,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIME, TAG_CUTE, TAG_ANIMATED)),
                        new CharacterSeedSpec(
                                        PRODUCT_CHAR_10,
                                        new BigDecimal("59000.00"),
                                        "Hoạt ảnh pixel Sakura Miku tông hồng anh đào rạng rỡ kết hợp phụ kiện trái anh đào nổi bật trên nền trời xanh.",
                                        "https://cdn.lumintra.shop/characters/10/Sakura%20Miku.gif",
                                        "https://cdn.lumintra.shop/characters/10/sakura-miku-v1.bin",
                                        2,
                                        210,
                                        27548,
                                        ProductStatus.PUBLISHED,
                                        List.of(TAG_PIXEL_ART, TAG_ANIME, TAG_CUTE, TAG_ANIMATED)));

        private final TagRepository tagRepository;
        private final ProductRepository productRepository;
        private final BundleRepository bundleRepository;
        private final BundleItemRepository bundleItemRepository;
        private final SubscriptionDetailRepository subscriptionDetailRepository;
        private final LedModelCreationService ledModelCreationService;
        private final LedModelLookupService ledModelLookupService;
        private final CharacterCreationService characterCreationService;
        private final CharacterLookupService characterLookupService;
        private final EmployeeLookupService employeeLookupService;
        private final StoreCacheEvictor storeCacheEvictor;

        @Value("${app.seed.test-designer-email:}")
        private String designerEmail;

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                log.info("Checking database for base Store catalog seed data...");
                AtomicBoolean seededAny = new AtomicBoolean(false);

                Map<String, TagEntity> tagsByName = seedTags(seededAny);
                TagEntity matrixTag = tagsByName.get(TAG_MATRIX);
                TagEntity cyberpunkTag = tagsByName.get(TAG_CYBERPUNK);
                TagEntity bundleTag = tagsByName.get(TAG_BUNDLE);
                TagEntity subscriptionTag = tagsByName.get(TAG_SUBSCRIPTION);
                TagEntity animeTag = tagsByName.get(TAG_ANIME);
                TagEntity cuteTag = tagsByName.get(TAG_CUTE);

                seedLedProduct(
                                PRODUCT_LED_PRO,
                                new BigDecimal("250000.00"),
                                "Tấm LED 64x64 đủ màu cao cấp, điều khiển qua Bluetooth và Wi-Fi.",
                                "https://cdn.lumintra.shop/store/products/led/Led_64_64.webp",
                                List.of("https://cdn.lumintra.shop/store/products/led/Led_64_64.webp"),
                                ProductStatus.PUBLISHED,
                                Set.of(matrixTag),
                                buildLedSpec("64x64", "192x192x18mm", "P3", "5V USB-C", "Bluetooth + Wi-Fi", 1200),
                                10L,
                                seededAny);

                long ledCompactModelId = seedLedProduct(
                                PRODUCT_LED_COMPACT,
                                new BigDecimal("190000.00"),
                                "Tấm LED 64x32 gọn nhẹ cho kính sau ô tô và góc bàn làm việc.",
                                "https://cdn.lumintra.shop/store/products/led/Led_64_32.webp",
                                List.of("https://cdn.lumintra.shop/store/products/led/Led_64_32.webp"),
                                ProductStatus.PUBLISHED,
                                Set.of(matrixTag),
                                buildLedSpec("64x32", "192x96x18mm", "P3", "5V USB-C", "Bluetooth + Wi-Fi", 1000),
                                10L,
                                seededAny);

                seedLedProduct(
                                PRODUCT_LED_MINI,
                                new BigDecimal("145000.00"),
                                "Tấm LED 32x16 nhỏ gọn, dễ lắp đặt cho xe máy và bàn học.",
                                "https://cdn.lumintra.shop/store/products/led/Led_32_16.webp",
                                List.of("https://cdn.lumintra.shop/store/products/led/Led_32_16.webp"),
                                ProductStatus.PUBLISHED,
                                Set.of(matrixTag),
                                buildLedSpec("32x16", "96x48x16mm", "P3", "5V USB-C", "Bluetooth", 900),
                                10L,
                                seededAny);

                Map<String, Long> seededCharacterVersions = seedCharacterProducts(tagsByName, seededAny);
                Long heoHongVersionId = seededCharacterVersions.get(PRODUCT_CHAR_01);
                Long capybaraVersionId = seededCharacterVersions.get(PRODUCT_CHAR_02);
                Long nezukoVersionId = seededCharacterVersions.get(PRODUCT_CHAR_05);
                Long maomaoVersionId = seededCharacterVersions.get(PRODUCT_CHAR_09);
                Long sakuraMikuVersionId = seededCharacterVersions.get(PRODUCT_CHAR_10);

                seedStarterBundleProduct(
                                ledCompactModelId,
                                heoHongVersionId,
                                capybaraVersionId,
                                Set.of(matrixTag, cyberpunkTag, bundleTag),
                                seededAny);

                seedAnimeTrioBundleProduct(
                                nezukoVersionId,
                                maomaoVersionId,
                                sakuraMikuVersionId,
                                Set.of(animeTag, cuteTag, bundleTag),
                                seededAny);

                seedSubscriptionProduct(
                                PRODUCT_SUB_VIP,
                                SUBSCRIPTION_PLAN_CODE_VIP,
                                new BigDecimal("999999.00"),
                                "Mở khóa toàn bộ thư viện nhân vật",
                                "https://cdn.lumintra.shop/store/products/subcription/All_Character.png",
                                true,
                                null,
                                true,
                                0,
                                3,
                                Map.of(),
                                "Mở khóa toàn bộ thư viện nhân vật",
                                Set.of(subscriptionTag),
                                seededAny);

                seedSubscriptionProduct(
                                PRODUCT_SUB_STANDARD_30D,
                                SUBSCRIPTION_PLAN_CODE_STANDARD_30D,
                                new BigDecimal("399999.00"),
                                "Tối đa 5 mẫu chữ trên ứng dụng điện thoại · 3 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                "https://cdn.lumintra.shop/store/products/subcription/Vip_Commission.png",
                                false,
                                30,
                                false,
                                3,
                                5,
                                Map.of(),
                                "Tối đa 5 mẫu chữ trên ứng dụng điện thoại · 3 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                Set.of(subscriptionTag),
                                seededAny);

                seedSubscriptionProduct(
                                PRODUCT_SUB_PRO_30D,
                                SUBSCRIPTION_PLAN_CODE_PRO_30D,
                                new BigDecimal("599999.00"),
                                "Tối đa 10 mẫu chữ trên ứng dụng điện thoại · 5 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                "https://cdn.lumintra.shop/store/products/subcription/Vip_Commission.png",
                                false,
                                30,
                                false,
                                5,
                                10,
                                Map.of(),
                                "Tối đa 10 mẫu chữ trên ứng dụng điện thoại · 5 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                Set.of(subscriptionTag),
                                seededAny);

                seedSubscriptionProduct(
                                PRODUCT_SUB_ULTIMATE_LIFETIME,
                                SUBSCRIPTION_PLAN_CODE_ULTIMATE_LIFETIME,
                                new BigDecimal("4999999.00"),
                                "Mở khóa toàn bộ thư viện nhân vật · Tối đa 20 mẫu chữ trên ứng dụng điện thoại · 10 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                "https://cdn.lumintra.shop/store/products/subcription/All_Character.png",
                                true,
                                null,
                                true,
                                10,
                                20,
                                Map.of(),
                                "Mở khóa toàn bộ thư viện nhân vật · Tối đa 20 mẫu chữ trên ứng dụng điện thoại · 10 lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế",
                                Set.of(subscriptionTag),
                                seededAny);

                if (seededAny.get()) {
                        storeCacheEvictor.evictAllProducts();
                }
                log.info("Store catalog seed data verification complete.");
        }

        private Map<String, TagEntity> seedTags(AtomicBoolean seededAny) {
                Map<String, TagEntity> tagsByName = new LinkedHashMap<>();
                for (TagSeedSpec spec : TAG_SEEDS) {
                        tagsByName.put(spec.name(), ensureTag(spec.name(), spec.description(), seededAny));
                }
                return tagsByName;
        }

        private Map<String, Long> seedCharacterProducts(Map<String, TagEntity> tagsByName, AtomicBoolean seededAny) {
                Long designerEmployeeId = resolveDesignerEmployeeId();
                Map<String, Long> versionIdsByProductName = new LinkedHashMap<>();
                for (CharacterSeedSpec spec : CHARACTER_SEEDS) {
                        Set<TagEntity> tags = resolveTags(spec.tagNames(), tagsByName);
                        long versionId = seedCharacterProduct(
                                        spec.productName(),
                                        spec.basePrice(),
                                        spec.description(),
                                        spec.previewUrl(),
                                        spec.ledBinaryUrl(),
                                        spec.frameCount(),
                                        spec.frameDelayMs(),
                                        spec.byteSize(),
                                        spec.status(),
                                        tags,
                                        designerEmployeeId,
                                        seededAny);
                        versionIdsByProductName.put(spec.productName(), versionId);
                }
                return versionIdsByProductName;
        }

        private Long resolveDesignerEmployeeId() {
                if (employeeLookupService == null) {
                        return null;
                }
                List<EmployeeProfileSnapshot> designers = employeeLookupService.findActiveDesigners();
                if (designers == null || designers.isEmpty()) {
                        return null;
                }
                if (designerEmail != null && !designerEmail.isBlank()) {
                        String normalizedEmail = designerEmail.trim();
                        for (EmployeeProfileSnapshot profile : designers) {
                                if (profile.email() != null && profile.email().equalsIgnoreCase(normalizedEmail)) {
                                        return profile.employeeId();
                                }
                        }
                }
                return designers.get(0).employeeId();
        }

        private Set<TagEntity> resolveTags(List<String> tagNames, Map<String, TagEntity> tagsByName) {
                Set<TagEntity> resolved = new LinkedHashSet<>();
                for (String tagName : tagNames) {
                        TagEntity tag = tagsByName.get(tagName);
                        if (tag != null) {
                                resolved.add(tag);
                        }
                }
                return resolved;
        }

        private TagEntity ensureTag(String name, String description, AtomicBoolean seededAny) {
                return tagRepository.findByName(name).orElseGet(() -> {
                        TagEntity saved = tagRepository.save(TagEntity.create(name, description));
                        seededAny.set(true);
                        log.info("Seeded store tag [tagId={}] [name={}]", saved.getTagId(), name);
                        return saved;
                });
        }

        private long seedLedProduct(
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
                                seededAny);

                return ledModelLookupService.findByProductId(product.getProductId())
                                .map(snapshot -> snapshot.modelId())
                                .orElseGet(() -> {
                                        long modelId = ledModelCreationService.createForProduct(
                                                        product.getProductId(), productName, spec, stockQuantity);
                                        seededAny.set(true);
                                        log.info("Seeded LED hardware model [modelId={}] [productId={}] [stockQuantity={}]",
                                                        modelId, product.getProductId(), stockQuantity);
                                        return modelId;
                                });
        }

        private long seedCharacterProduct(
                        String productName,
                        BigDecimal basePrice,
                        String description,
                        String previewUrl,
                        String ledBinaryUrl,
                        int frameCount,
                        int frameDelayMs,
                        int byteSize,
                        ProductStatus status,
                        Set<TagEntity> tags,
                        Long designerEmployeeId,
                        AtomicBoolean seededAny) {
                ProductEntity product = findOrCreateProduct(
                                productName,
                                ProductType.CHARACTER,
                                basePrice,
                                description,
                                previewUrl,
                                List.of(previewUrl),
                                status,
                                tags,
                                seededAny);

                return characterLookupService.findVersionIdByProductId(product.getProductId())
                                .map(existingVersionId -> {
                                        if (designerEmployeeId != null
                                                        && characterCreationService.assignCreatorIfUnownedForProduct(
                                                                        product.getProductId(), designerEmployeeId)) {
                                                seededAny.set(true);
                                        }
                                        return existingVersionId;
                                })
                                .orElseGet(() -> {
                                        long versionId = characterCreationService.createForProduct(
                                                        product.getProductId(),
                                                        designerEmployeeId,
                                                        productName,
                                                        description,
                                                        1,
                                                        previewUrl,
                                                        ledBinaryUrl,
                                                        frameCount,
                                                        frameDelayMs,
                                                        byteSize);
                                        seededAny.set(true);
                                        log.info("Seeded character version [productId={}] [versionId={}] [creatorEmployeeId={}] [name={}]",
                                                        product.getProductId(), versionId, designerEmployeeId, productName);
                                        return versionId;
                                });
        }

        private void seedStarterBundleProduct(
                        Long ledCompactModelId,
                        Long heoHongVersionId,
                        Long capybaraVersionId,
                        Set<TagEntity> tags,
                        AtomicBoolean seededAny) {
                ProductEntity product = findOrCreateProduct(
                                PRODUCT_BUNDLE_STARTER,
                                ProductType.BUNDLE,
                                new BigDecimal("209000.00"),
                                "Combo gồm tấm LED 64x32 Gọn Nhẹ cùng cặp nhân vật Heo Hồng Cổ Điển và Capybara Ôm Táo Đỏ.",
                                "https://cdn.lumintra.shop/store/products/bundle/Combo1.png",
                                List.of("https://cdn.lumintra.shop/store/products/bundle/Combo1.png"),
                                ProductStatus.PUBLISHED,
                                tags,
                                seededAny);

                if (bundleRepository.findByProductId(product.getProductId()).isEmpty()) {
                        BundleEntity bundle = bundleRepository.save(BundleEntity.create(
                                        product.getProductId(),
                                        PRODUCT_BUNDLE_STARTER,
                                        "Combo 1 tấm LED 64x32 Gọn Nhẹ, Heo Hồng Cổ Điển và Capybara Ôm Táo Đỏ",
                                        new BigDecimal("245000.00"),
                                        new BigDecimal("209000.00"),
                                        ProductStatus.PUBLISHED.name(),
                                        Instant.now().minus(7, ChronoUnit.DAYS),
                                        null));
                        bundleItemRepository.save(BundleItemEntity.createLedItem(bundle, ledCompactModelId, 1));
                        bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, heoHongVersionId, 1));
                        bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, capybaraVersionId, 1));
                        seededAny.set(true);
                        log.info("Seeded bundle product [productId={}] [bundleId={}]",
                                        product.getProductId(), bundle.getBundleId());
                }
        }

        private void seedAnimeTrioBundleProduct(
                        Long nezukoVersionId,
                        Long maomaoVersionId,
                        Long sakuraMikuVersionId,
                        Set<TagEntity> tags,
                        AtomicBoolean seededAny) {
                ProductEntity product = findOrCreateProduct(
                                PRODUCT_BUNDLE_ANIME_TRIO,
                                ProductType.BUNDLE,
                                new BigDecimal("129000.00"),
                                "Combo 3 nhân vật Anime: Nezuko (Thanh Gươm Diệt Quỷ), Maomao (Dược Sư Tự Sự) và Sakura Miku Hoa Anh Đào.",
                                "https://cdn.lumintra.shop/store/products/bundle/Combo2.png",
                                List.of("https://cdn.lumintra.shop/store/products/bundle/Combo2.png"),
                                ProductStatus.PUBLISHED,
                                tags,
                                seededAny);

                if (bundleRepository.findByProductId(product.getProductId()).isEmpty()) {
                        BundleEntity bundle = bundleRepository.save(BundleEntity.create(
                                        product.getProductId(),
                                        PRODUCT_BUNDLE_ANIME_TRIO,
                                        "Combo 3 nhân vật: Nezuko (Thanh Gươm Diệt Quỷ), Maomao (Dược Sư Tự Sự) và Sakura Miku Hoa Anh Đào",
                                        new BigDecimal("154000.00"),
                                        new BigDecimal("129000.00"),
                                        ProductStatus.PUBLISHED.name(),
                                        Instant.now().minus(5, ChronoUnit.DAYS),
                                        null));
                        bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, nezukoVersionId, 1));
                        bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, maomaoVersionId, 1));
                        bundleItemRepository.save(BundleItemEntity.createCharacterItem(bundle, sakuraMikuVersionId, 1));
                        seededAny.set(true);
                        log.info("Seeded bundle product [productId={}] [bundleId={}]",
                                        product.getProductId(), bundle.getBundleId());
                }
        }

        private void seedSubscriptionProduct(
                        String productName,
                        String planCode,
                        BigDecimal price,
                        String description,
                        String thumbnailUrl,
                        boolean isLifetime,
                        Integer durationDays,
                        boolean unlocksAllCharacters,
                        int freeCommissionRevisions,
                        int maxMobileSlots,
                        Map<String, Object> benefitFeatures,
                        String benefitSummary,
                        Set<TagEntity> tags,
                        AtomicBoolean seededAny) {
                ProductEntity product = findOrCreateProduct(
                                productName,
                                ProductType.SUBSCRIPTION,
                                price,
                                description,
                                thumbnailUrl,
                                List.of(thumbnailUrl),
                                ProductStatus.PUBLISHED,
                                tags,
                                seededAny);

                boolean detailMissing = subscriptionDetailRepository.findByProductId(product.getProductId()).isEmpty()
                                && subscriptionDetailRepository.findByPlanCode(planCode).isEmpty();
                if (detailMissing) {
                        subscriptionDetailRepository.save(SubscriptionDetailEntity.create(
                                        product.getProductId(),
                                        planCode,
                                        productName,
                                        isLifetime,
                                        durationDays,
                                        unlocksAllCharacters,
                                        freeCommissionRevisions,
                                        maxMobileSlots,
                                        benefitFeatures,
                                        benefitSummary));
                        seededAny.set(true);
                        log.info("Seeded subscription detail [productId={}] [planCode={}]",
                                        product.getProductId(), planCode);
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
                                        tags));
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

        public record TagSeedSpec(String name, String description) {
        }

        public record CharacterSeedSpec(
                        String productName,
                        BigDecimal basePrice,
                        String description,
                        String previewUrl,
                        String ledBinaryUrl,
                        int frameCount,
                        int frameDelayMs,
                        int byteSize,
                        ProductStatus status,
                        List<String> tagNames) {
        }
}
