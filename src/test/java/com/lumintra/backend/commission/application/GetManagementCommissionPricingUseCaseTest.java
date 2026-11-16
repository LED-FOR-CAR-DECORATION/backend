package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleSummaryResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierSummaryResponse;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetManagementCommissionPricingUseCaseTest {

    @Mock
    private CommissionPricingTierRepository tierRepository;

    @Mock
    private CommissionPriceConfigRepository configRepository;

    private GetManagementCommissionPricingUseCase useCase;

    private CommissionPricingTierEntity customTier;
    private CommissionPricingTierEntity basicTier;
    private CommissionPricingTierEntity inactivePresetTier;

    private CommissionPriceConfigEntity formatConfig;
    private CommissionPriceConfigEntity animationFramesConfig;
    private CommissionPriceConfigEntity inactiveCustomConfig;

    private CommissionPriceOptionEntity staticOption;
    private CommissionPriceOptionEntity animatedOption;
    private CommissionPriceOptionEntity frameRangeOption;
    private CommissionPriceOptionEntity deprecatedOption;

    @BeforeEach
    void setUp() {
        PricingInvariantValidator validator = new PricingInvariantValidator();
        CommissionPricingMapper mapper = new CommissionPricingMapper(validator);
        useCase = new GetManagementCommissionPricingUseCase(tierRepository, configRepository, mapper);

        formatConfig = CommissionPriceConfigEntity.builder()
                .configId(10L)
                .ruleKey("FORMAT_TYPE")
                .name("Định dạng hiển thị (Format Type)")
                .description("Chọn định dạng ảnh tĩnh hoặc ảnh động GIF")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .dependsOnOptionId(null)
                .build();

        animationFramesConfig = CommissionPriceConfigEntity.builder()
                .configId(20L)
                .ruleKey("FRAME_RANGE")
                .name("Số lượng frame (Frame Range)")
                .description("Chọn số khung hình chuyển động")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.DROPDOWN)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(2)
                .dependsOnOptionId(2L)
                .build();

        inactiveCustomConfig = CommissionPriceConfigEntity.builder()
                .configId(30L)
                .ruleKey("GIFT_WRAP")
                .name("Gói quà tặng (Gift Wrap)")
                .description("Tùy chọn đóng gói quà tặng")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(false)
                .isSystem(false)
                .isActive(false)
                .displayOrder(3)
                .options(new ArrayList<>())
                .build();

        staticOption = CommissionPriceOptionEntity.builder()
                .optionId(1L)
                .config(formatConfig)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Ảnh tĩnh (PNG)")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        animatedOption = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .config(formatConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Ảnh động (GIF)")
                .basePrice(new BigDecimal("50000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        deprecatedOption = CommissionPriceOptionEntity.builder()
                .optionId(3L)
                .config(formatConfig)
                .optionCode("DEPRECATED_OPT")
                .optionLabel("Tùy chọn cũ")
                .basePrice(new BigDecimal("10000.00"))
                .isDefault(false)
                .isActive(false)
                .displayOrder(3)
                .build();

        frameRangeOption = CommissionPriceOptionEntity.builder()
                .optionId(4L)
                .config(animationFramesConfig)
                .optionCode("FRAMES_1_5")
                .optionLabel("1 - 5 frames")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        formatConfig.setOptions(new ArrayList<>(List.of(staticOption, animatedOption, deprecatedOption)));
        animationFramesConfig.setOptions(new ArrayList<>(List.of(frameRangeOption)));

        customTier = CommissionPricingTierEntity.builder()
                .tierId(100L)
                .tierCode("CUSTOM")
                .name("Gói Tùy Biến (Custom Package)")
                .description("Tùy biến hoàn toàn cấu hình")
                .basePrice(new BigDecimal("100000.00"))
                .discountPercent(BigDecimal.ZERO)
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(3)
                .isCustom(true)
                .isActive(true)
                .displayOrder(3)
                .includedOptions(new HashSet<>())
                .build();

        basicTier = CommissionPricingTierEntity.builder()
                .tierId(101L)
                .tierCode("BASIC")
                .name("Gói Cơ Bản (Basic Package)")
                .description("Gói thiết kế tĩnh tiêu chuẩn")
                .basePrice(new BigDecimal("79000.00"))
                .discountPercent(new BigDecimal("21.00"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>(List.of(staticOption)))
                .build();

        inactivePresetTier = CommissionPricingTierEntity.builder()
                .tierId(102L)
                .tierCode("DEPRECATED_PRESET")
                .name("Gói Ngừng Áp Dụng")
                .description("Gói cũ")
                .basePrice(new BigDecimal("60000.00"))
                .discountPercent(BigDecimal.ZERO)
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(false)
                .displayOrder(4)
                .includedOptions(new HashSet<>(List.of(deprecatedOption)))
                .build();
    }

    @Test
    @DisplayName("CMS-29 UseCase UTCID01 [N]: Returns summary configuration including active and inactive tiers and rules")
    void execute_returnsAllActiveAndInactiveEntities() {
        when(tierRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(basicTier, customTier, inactivePresetTier));
        when(configRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(formatConfig, animationFramesConfig, inactiveCustomConfig));

        ManagementCommissionPricingResponse response = useCase.execute();

        assertThat(response).isNotNull();
        assertThat(response.tiers()).hasSize(3);
        assertThat(response.rules()).hasSize(3);

        ManagementPricingTierSummaryResponse basicResp = response.tiers().stream()
                .filter(t -> "BASIC".equals(t.tierCode())).findFirst().orElseThrow();
        assertThat(basicResp.isActive()).isTrue();
        assertThat(basicResp.isCustom()).isFalse();
        assertThat(basicResp.includedOptionCount()).isEqualTo(1);

        ManagementPricingTierSummaryResponse customResp = response.tiers().stream()
                .filter(t -> "CUSTOM".equals(t.tierCode())).findFirst().orElseThrow();
        assertThat(customResp.isActive()).isTrue();
        assertThat(customResp.isCustom()).isTrue();
        assertThat(customResp.includedOptionCount()).isZero();

        ManagementPricingTierSummaryResponse inactiveResp = response.tiers().stream()
                .filter(t -> "DEPRECATED_PRESET".equals(t.tierCode())).findFirst().orElseThrow();
        assertThat(inactiveResp.isActive()).isFalse();

        ManagementPricingRuleSummaryResponse formatRule = response.rules().stream()
                .filter(r -> "FORMAT_TYPE".equals(r.configCode())).findFirst().orElseThrow();
        assertThat(formatRule.isSystem()).isTrue();
        assertThat(formatRule.isActive()).isTrue();
        assertThat(formatRule.optionCount()).isEqualTo(3);

        ManagementPricingRuleSummaryResponse inactiveRule = response.rules().stream()
                .filter(r -> "GIFT_WRAP".equals(r.configCode())).findFirst().orElseThrow();
        assertThat(inactiveRule.isSystem()).isFalse();
        assertThat(inactiveRule.isActive()).isFalse();
        assertThat(inactiveRule.optionCount()).isZero();
    }

    @Test
    @DisplayName("CMS-29 UseCase UTCID02 [N]: Calculates bundled custom price and savings for preset tiers using active CUSTOM tier")
    void execute_calculatesCustomPriceAndSavingsForPresetTiers() {
        when(tierRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(basicTier, customTier));
        when(configRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(formatConfig));

        ManagementCommissionPricingResponse response = useCase.execute();

        ManagementPricingTierSummaryResponse basicResp = response.tiers().stream()
                .filter(t -> "BASIC".equals(t.tierCode())).findFirst().orElseThrow();

        assertThat(basicResp.customCalculatedPrice()).isEqualByComparingTo("100000.00");
        assertThat(basicResp.savingsAmount()).isEqualByComparingTo("21000.00");
        assertThat(basicResp.includedOptionCount()).isEqualTo(1);

        ManagementPricingTierSummaryResponse customResp = response.tiers().stream()
                .filter(t -> "CUSTOM".equals(t.tierCode())).findFirst().orElseThrow();
        assertThat(customResp.customCalculatedPrice()).isEqualByComparingTo("100000.00");
        assertThat(customResp.savingsAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("CMS-29 UseCase UTCID03 [B]: Gracefully handles missing active CUSTOM tier without throwing 404")
    void execute_missingActiveCustomTier_fallsBackGracefully() {
        when(tierRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(basicTier));
        when(configRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(formatConfig));

        ManagementCommissionPricingResponse response = useCase.execute();

        assertThat(response.tiers()).hasSize(1);
        ManagementPricingTierSummaryResponse basicResp = response.tiers().get(0);
        assertThat(basicResp.customCalculatedPrice()).isEqualByComparingTo("79000.00");
        assertThat(basicResp.savingsAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("CMS-29 UseCase UTCID04 [B]: Empty tiers and empty configs list in database returns empty collections")
    void execute_emptyDatabase_returnsEmptyResponse() {
        when(tierRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(Collections.emptyList());
        when(configRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(Collections.emptyList());

        ManagementCommissionPricingResponse response = useCase.execute();

        assertThat(response).isNotNull();
        assertThat(response.tiers()).isEmpty();
        assertThat(response.rules()).isEmpty();
    }
}
