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
                .name("Định dạng hiển thị")
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
                .optionLabel("Ảnh động (GIF)")
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

        // 2. System Rule: MATRIX_SIZE (Marked isBasePrice = true so BASE_PERCENT scales with resolution)
        CommissionPriceConfigEntity matrixSizeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("MATRIX_SIZE")
                .name("Kích thước ma trận LED")
                .description("Độ phân giải hiển thị ma trận LED của thiết bị")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(true)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity size32Option = CommissionPriceOptionEntity.builder()
                .optionCode("32x32")
                .optionLabel("32 x 32 điểm ảnh")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity size32x64Option = CommissionPriceOptionEntity.builder()
                .optionCode("32x64")
                .optionLabel("32 x 64 điểm ảnh")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity size64Option = CommissionPriceOptionEntity.builder()
                .optionCode("64x64")
                .optionLabel("64 x 64 điểm ảnh")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("60000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(3)
                .build();

        matrixSizeConfig.addOption(size32Option);
        matrixSizeConfig.addOption(size32x64Option);
        matrixSizeConfig.addOption(size64Option);
        invariantValidator.validateRule(matrixSizeConfig);
        configRepository.save(matrixSizeConfig);

        // 3. System Rule: FRAME_RANGE (Conditional on ANIMATED_GIF)
        CommissionPriceConfigEntity frameRangeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FRAME_RANGE")
                .name("Số lượng khung hình")
                .description("Số lượng khung hình chuyển động cho thiết kế ảnh động GIF")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(true)
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
        configRepository.save(frameRangeConfig);

        // 4. Base Configurator Tier: CUSTOM (no presets seeded by default; preset support remains intact in schema/domain)
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .name("Thiết kế tùy chỉnh")
                .description("Tự do cấu hình kích thước, hiệu ứng chuyển động và yêu cầu chi tiết theo ý muốn.")
                .basePrice(new BigDecimal("100000.00"))
                .discountPercent(new BigDecimal("0.00"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(1)
                .isCustom(true)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>())
                .triggerOptions(new HashSet<>())
                .build();

        invariantValidator.validateTier(customTier, null);
        tierRepository.save(customTier);

        log.info("Commission pricing starter blueprint rules and CUSTOM tier seeded successfully.");
    }
}
