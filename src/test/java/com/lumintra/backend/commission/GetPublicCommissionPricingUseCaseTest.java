package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.application.GetPublicCommissionPricingUseCase;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPublicCommissionPricingUseCaseTest {

    @Mock
    private CommissionPricingTierRepository tierRepository;

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionPriceOptionRepository optionRepository;

    private GetPublicCommissionPricingUseCase useCase;

    @BeforeEach
    void setUp() {
        PricingInvariantValidator validator = new PricingInvariantValidator();
        CommissionPricingMapper mapper = new CommissionPricingMapper(validator);
        useCase = new GetPublicCommissionPricingUseCase(tierRepository, configRepository, optionRepository, mapper);
    }

    @Test
    @DisplayName("CMS-01 UseCase UTCID01 [N]: Normal execution returns active tier summaries, bundle savings, and rules")
    void execute_returnsActiveTiersAndRules() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierId(3L)
                .tierCode("CUSTOM")
                .name("Thiết Kế Tùy Chỉnh (Custom Design)")
                .description("Tự do cấu hình")
                .basePrice(new BigDecimal("100000.00"))
                .discountPercent(BigDecimal.ZERO)
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(5)
                .isCustom(true)
                .isActive(true)
                .displayOrder(3)
                .includedOptions(new HashSet<>())
                .build();

        CommissionPriceConfigEntity formatConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .name("Format Type")
                .description("Select image format")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity staticOption = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .config(formatConfig)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Static PNG")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity gifOption = CommissionPriceOptionEntity.builder()
                .optionId(11L)
                .config(formatConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Animated GIF")
                .basePrice(new BigDecimal("50000.00"))
                .isDefault(false)
                .isActive(true)
                .displayOrder(2)
                .build();

        formatConfig.setOptions(new ArrayList<>(List.of(staticOption, gifOption)));

        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .name("Gói Cơ Bản (Basic Package)")
                .description("Gói tĩnh tiêu chuẩn")
                .basePrice(new BigDecimal("79000.00"))
                .discountPercent(new BigDecimal("21.00"))
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>(Set.of(staticOption)))
                .build();

        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(basicTier, customTier));
        when(configRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(formatConfig));
        when(optionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(staticOption, gifOption));

        PublicCommissionPricingResponse response = useCase.execute();

        assertThat(response).isNotNull();
        assertThat(response.tiers()).hasSize(2);

        var basicSummary = response.tiers().get(0);
        assertThat(basicSummary.tierCode()).isEqualTo("BASIC");
        assertThat(basicSummary.basePrice()).isEqualByComparingTo("79000.00");
        assertThat(basicSummary.customCalculatedPrice()).isEqualByComparingTo("100000.00");
        assertThat(basicSummary.savingsAmount()).isEqualByComparingTo("21000.00");
        assertThat(basicSummary.bundledOptionIds()).containsExactly(10L);

        var customSummary = response.tiers().get(1);
        assertThat(customSummary.tierCode()).isEqualTo("CUSTOM");
        assertThat(customSummary.isCustom()).isTrue();
        assertThat(customSummary.savingsAmount()).isEqualByComparingTo("0.00");

        assertThat(response.rules()).hasSize(1);
        assertThat(response.rules().get(0).ruleKey()).isEqualTo("FORMAT_TYPE");
        assertThat(response.rules().get(0).options()).hasSize(2);
    }

    @Test
    @DisplayName("CMS-01 UseCase UTCID02 [A]: Missing CUSTOM tier throws CommissionTierNotFoundException")
    void execute_throwsException_whenCustomTierNotFound() {
        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .name("Basic Package")
                .basePrice(new BigDecimal("79000.00"))
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .build();

        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(basicTier));

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(CommissionTierNotFoundException.class)
                .hasMessageContaining("Active CUSTOM tier configuration not found.");
    }

    @Test
    @DisplayName("CMS-01 UseCase UTCID03 [N]: Inactive options inside configs are filtered out from response")
    void execute_filtersInactiveOptions() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierId(3L)
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>())
                .build();

        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("MATRIX_SIZE")
                .name("Matrix Size")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity activeOpt = CommissionPriceOptionEntity.builder()
                .optionId(1L)
                .config(config)
                .optionCode("32x32")
                .optionLabel("32x32")
                .basePrice(BigDecimal.ZERO)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity inactiveOpt = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .config(config)
                .optionCode("128x128")
                .optionLabel("128x128")
                .basePrice(new BigDecimal("100000.00"))
                .isActive(false)
                .displayOrder(2)
                .build();

        config.setOptions(new ArrayList<>(List.of(activeOpt, inactiveOpt)));

        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(customTier));
        when(configRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(config));
        when(optionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(activeOpt));

        PublicCommissionPricingResponse response = useCase.execute();

        assertThat(response.rules()).hasSize(1);
        assertThat(response.rules().get(0).options()).hasSize(1);
        assertThat(response.rules().get(0).options().get(0).optionCode()).isEqualTo("32x32");
    }

    @Test
    @DisplayName("CMS-01 UseCase UTCID04 [N]: Conditional rule dependency populates dependsOnRuleKey and dependsOnOptionCode")
    void execute_resolvesConditionalRuleDependency() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierId(3L)
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(new HashSet<>())
                .build();

        CommissionPriceConfigEntity parentConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .name("Format Type")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity gifOption = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .config(parentConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Animated GIF")
                .basePrice(new BigDecimal("50000.00"))
                .isActive(true)
                .displayOrder(2)
                .build();

        parentConfig.setOptions(new ArrayList<>(List.of(gifOption)));

        CommissionPriceConfigEntity childConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("FRAME_RANGE")
                .name("Frame Count")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(false)
                .isSystem(true)
                .isActive(true)
                .displayOrder(2)
                .dependsOnOptionId(2L)
                .build();

        CommissionPriceOptionEntity frameOption = CommissionPriceOptionEntity.builder()
                .optionId(3L)
                .config(childConfig)
                .optionCode("1-5")
                .optionLabel("1-5 Frames")
                .basePrice(BigDecimal.ZERO)
                .isActive(true)
                .displayOrder(1)
                .build();

        childConfig.setOptions(new ArrayList<>(List.of(frameOption)));

        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(customTier));
        when(configRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(parentConfig, childConfig));
        when(optionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(gifOption, frameOption));

        PublicCommissionPricingResponse response = useCase.execute();

        var childRule = response.rules().stream()
                .filter(r -> "FRAME_RANGE".equals(r.ruleKey()))
                .findFirst()
                .orElseThrow();

        assertThat(childRule.dependsOnOptionId()).isEqualTo(2L);
        assertThat(childRule.dependsOnRuleKey()).isEqualTo("FORMAT_TYPE");
        assertThat(childRule.dependsOnOptionCode()).isEqualTo("ANIMATED_GIF");
    }

    @Test
    @DisplayName("CMS-01 UseCase UTCID05 [B]: Boundary case when no preset tiers or custom rules exist")
    void execute_handlesBoundaryEmptyCollections() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(null)
                .build();

        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(customTier));
        when(configRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());
        when(optionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());

        PublicCommissionPricingResponse response = useCase.execute();

        assertThat(response.tiers()).hasSize(1);
        assertThat(response.rules()).isEmpty();
    }
}
