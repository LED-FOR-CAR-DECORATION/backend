package com.lumintra.backend.commission.infrastructure.bootstrap;

import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Component
@Order(5)
@RequiredArgsConstructor
public class CommissionPricingDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommissionPricingDataSeeder.class);

    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final CommissionPricingTierRepository tierRepository;
    private final PricingInvariantValidator invariantValidator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configRepository.count() > 0 && tierRepository.count() > 0) {
            log.info("Commission pricing data already seeded. Skipping seeder.");
            return;
        }

        log.info("Seeding Commission pricing starter blueprint rules and presets...");

        // 1. System Rule: FORMAT_TYPE (ANIMATED_GIF scales via +50% of Base bucket)
        CommissionPriceConfigEntity formatTypeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FORMAT_TYPE")
                .name("Định dạng hiển thị (Format Type)")
                .description("Chọn định dạng hình ảnh cho thiết kế LED (tĩnh hoặc ảnh động GIF)")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity staticImageOption = CommissionPriceOptionEntity.builder()
                .optionCode("STATIC_IMAGE")
                .optionLabel("Ảnh tĩnh (PNG)")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity animatedGifOption = CommissionPriceOptionEntity.builder()
                .optionCode("ANIMATED_GIF")
                .optionLabel("Ảnh động (GIF) (+50% Base)")
                .modifierType(ModifierType.BASE_PERCENT)
                .basePrice(new BigDecimal("50.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        formatTypeConfig.addOption(staticImageOption);
        formatTypeConfig.addOption(animatedGifOption);
        invariantValidator.validateRule(formatTypeConfig);
        formatTypeConfig = configRepository.save(formatTypeConfig);

        CommissionPriceOptionEntity savedAnimatedGif = formatTypeConfig.getOptions().stream()
                .filter(o -> "ANIMATED_GIF".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();
        CommissionPriceOptionEntity savedStaticImage = formatTypeConfig.getOptions().stream()
                .filter(o -> "STATIC_IMAGE".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();

        // 2. System Rule: MATRIX_SIZE (Marked isBasePrice = true so BASE_PERCENT scales with resolution)
        CommissionPriceConfigEntity matrixSizeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("MATRIX_SIZE")
                .name("Kích thước ma trận LED (Matrix Size)")
                .description("Độ phân giải hiển thị ma trận LED của thiết bị")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(true)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity size16Option = CommissionPriceOptionEntity.builder()
                .optionCode("16x16")
                .optionLabel("16 x 16 Pixels")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity size32Option = CommissionPriceOptionEntity.builder()
                .optionCode("32x32")
                .optionLabel("32 x 32 Pixels")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity size64Option = CommissionPriceOptionEntity.builder()
                .optionCode("64x64")
                .optionLabel("64 x 64 Pixels")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("60000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(3)
                .build();

        matrixSizeConfig.addOption(size16Option);
        matrixSizeConfig.addOption(size32Option);
        matrixSizeConfig.addOption(size64Option);
        invariantValidator.validateRule(matrixSizeConfig);
        matrixSizeConfig = configRepository.save(matrixSizeConfig);

        CommissionPriceOptionEntity savedSize16 = matrixSizeConfig.getOptions().stream()
                .filter(o -> "16x16".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();
        CommissionPriceOptionEntity savedSize32 = matrixSizeConfig.getOptions().stream()
                .filter(o -> "32x32".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();

        // 3. System Rule: FRAME_RANGE (Conditional on ANIMATED_GIF)
        CommissionPriceConfigEntity frameRangeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FRAME_RANGE")
                .name("Số lượng khung hình (Frame Count)")
                .description("Số lượng khung hình chuyển động cho thiết kế ảnh động GIF")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(false)
                .isSystem(true)
                .isActive(true)
                .displayOrder(3)
                .dependsOnOptionId(savedAnimatedGif.getOptionId())
                .build();

        CommissionPriceOptionEntity frame1To5Option = CommissionPriceOptionEntity.builder()
                .optionCode("1-5")
                .optionLabel("1 - 5 Khung hình (Ngắn)")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity frame6To15Option = CommissionPriceOptionEntity.builder()
                .optionCode("6-15")
                .optionLabel("6 - 15 Khung hình (Trung bình)")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity frame16To30Option = CommissionPriceOptionEntity.builder()
                .optionCode("16-30")
                .optionLabel("16 - 30 Khung hình (Dài)")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("60000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(3)
                .build();

        frameRangeConfig.addOption(frame1To5Option);
        frameRangeConfig.addOption(frame6To15Option);
        frameRangeConfig.addOption(frame16To30Option);
        invariantValidator.validateRule(frameRangeConfig);
        frameRangeConfig = configRepository.save(frameRangeConfig);

        CommissionPriceOptionEntity savedFrame1To5 = frameRangeConfig.getOptions().stream()
                .filter(o -> "1-5".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();

        // 4. Tier: CUSTOM
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .name("Thiết Kế Tùy Chỉnh (Custom Design)")
                .description("Tự do cấu hình kích thước, hiệu ứng chuyển động và yêu cầu chi tiết theo ý muốn.")
                .basePrice(new BigDecimal("100000.00"))
                .discountPercent(new BigDecimal("0.00"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(5)
                .isCustom(true)
                .isActive(true)
                .displayOrder(3)
                .includedOptions(new HashSet<>())
                .triggerOptions(new HashSet<>())
                .build();

        invariantValidator.validateTier(customTier, null);
        customTier = tierRepository.save(customTier);

        // 5. Tier: BASIC (Static 16x16, 1 revision)
        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierCode("BASIC")
                .name("Gói Cơ Bản (Basic Package)")
                .description("Gói thiết kế tĩnh tiêu chuẩn cho ma trận LED nhỏ gọn, tiết kiệm chi phí.")
                .basePrice(new BigDecimal("79000.00"))
                .discountPercent(new BigDecimal("21.00"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>(Set.of(savedStaticImage, savedSize16)))
                .triggerOptions(new HashSet<>())
                .build();

        invariantValidator.validateTier(basicTier, customTier);
        tierRepository.save(basicTier);

        // 6. Tier: ADVANCED (Animated GIF 32x32 1-5 frames, 3 revisions)
        // Custom pipeline equivalent: (100k + 30k) + 50%*(130k) + 0 = 195k -> 149k = 23.59% savings
        CommissionPricingTierEntity advancedTier = CommissionPricingTierEntity.builder()
                .tierCode("ADVANCED")
                .name("Gói Nâng Cao (Advanced Package)")
                .description("Gói thiết kế chuyển động GIF sống động với ma trận 32x32 và khung hình mượt mà.")
                .basePrice(new BigDecimal("149000.00"))
                .discountPercent(new BigDecimal("23.59"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(3)
                .isCustom(false)
                .isActive(true)
                .displayOrder(2)
                .includedOptions(new HashSet<>(Set.of(savedAnimatedGif, savedSize32, savedFrame1To5)))
                .triggerOptions(new HashSet<>())
                .build();

        invariantValidator.validateTier(advancedTier, customTier);
        tierRepository.save(advancedTier);

        log.info("Commission pricing starter blueprint rules and presets seeded successfully.");
    }
}
