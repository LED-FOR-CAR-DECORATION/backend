package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.PricingRuleResponse;
import com.lumintra.backend.commission.api.response.PricingTierSummaryResponse;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionPricingMapperTest {

    private final CommissionPricingMapper mapper = new CommissionPricingMapper(new PricingInvariantValidator());

    @Test
    @DisplayName("CMS-01 Mapper UTCID01 [N]: toTierSummaryResponse maps preset tier, modifier calculation, triggerOptionIds, and ordered bundledOptionIds")
    void toTierSummaryResponse_mapsPresetTierWithOrderedBundledAndTriggerIds() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierId(3L)
                .tierCode("CUSTOM")
                .name("Custom")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .build();

        CommissionPriceConfigEntity formatConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .displayOrder(1)
                .build();
        CommissionPriceConfigEntity matrixConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("MATRIX_SIZE")
                .isBasePrice(true)
                .displayOrder(2)
                .build();
        CommissionPriceConfigEntity subjectConfig = CommissionPriceConfigEntity.builder()
                .configId(3L)
                .ruleKey("ART_SUBJECT")
                .displayOrder(0)
                .build();

        CommissionPriceOptionEntity matrixOpt = CommissionPriceOptionEntity.builder()
                .optionId(20L)
                .config(matrixConfig)
                .optionCode("32x32")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .displayOrder(1)
                .build();
        CommissionPriceOptionEntity gifOpt = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .config(formatConfig)
                .optionCode("ANIMATED_GIF")
                .modifierType(ModifierType.BASE_PERCENT)
                .basePrice(new BigDecimal("50.00"))
                .isActive(true)
                .displayOrder(1)
                .build();
        CommissionPriceOptionEntity charTrigger = CommissionPriceOptionEntity.builder()
                .optionId(5L)
                .config(subjectConfig)
                .optionCode("CHARACTER_ART")
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPricingTierEntity advTier = CommissionPricingTierEntity.builder()
                .tierId(2L)
                .tierCode("ADVANCED")
                .name("Advanced")
                .description("Advanced package")
                .basePrice(new BigDecimal("149000.00"))
                .maxRevisions(3)
                .isCustom(false)
                .isActive(true)
                .displayOrder(2)
                .triggerOptions(Set.of(charTrigger))
                .includedOptions(Set.of(matrixOpt, gifOpt))
                .build();

        PricingTierSummaryResponse summary = mapper.toTierSummaryResponse(advTier, customTier);

        assertThat(summary).isNotNull();
        assertThat(summary.tierCode()).isEqualTo("ADVANCED");
        // 100k base + 30k matrix (130k base) + 50% of 130k (65k) = 195k
        assertThat(summary.customCalculatedPrice()).isEqualByComparingTo("195000.00");
        assertThat(summary.savingsAmount()).isEqualByComparingTo("46000.00");
        assertThat(summary.triggerOptionIds()).containsExactly(5L);
        assertThat(summary.bundledOptionIds()).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("CMS-01 Mapper UTCID02 [B]: Handles null inputs, quantity bounds, isBasePrice, and missing dependency lookup safely")
    void toPricingRuleResponse_handlesQuantityAndUnresolvedDependency() {
        assertThat(mapper.toPricingRuleResponse(null, Map.of())).isNull();
        assertThat(mapper.toTierSummaryResponse(null, null)).isNull();
        assertThat(mapper.toPricingOptionResponse(null)).isNull();

        CommissionPriceConfigEntity qtyConfig = CommissionPriceConfigEntity.builder()
                .configId(4L)
                .ruleKey("VARIANTS")
                .name("Time-of-Day Variants")
                .selectionType(SelectionType.QUANTITY)
                .uiType(UiType.NUMBER)
                .isBasePrice(false)
                .minQuantity(0)
                .maxQuantity(5)
                .dependsOnOptionId(999L)
                .options(List.of(CommissionPriceOptionEntity.builder()
                        .optionId(50L)
                        .optionCode("EXTRA_VARIANT")
                        .optionLabel("Extra variant")
                        .modifierType(ModifierType.FLAT_ADD)
                        .basePrice(new BigDecimal("30000.00"))
                        .isActive(true)
                        .displayOrder(1)
                        .build()))
                .build();

        PricingRuleResponse response = mapper.toPricingRuleResponse(qtyConfig, Map.of());
        assertThat(response.selectionType()).isEqualTo(SelectionType.QUANTITY);
        assertThat(response.uiType()).isEqualTo(UiType.NUMBER);
        assertThat(response.isBasePrice()).isFalse();
        assertThat(response.minQuantity()).isEqualTo(0);
        assertThat(response.maxQuantity()).isEqualTo(5);
        assertThat(response.dependsOnOptionId()).isEqualTo(999L);
        assertThat(response.dependsOnRuleKey()).isNull();
        assertThat(response.options().get(0).modifierType()).isEqualTo(ModifierType.FLAT_ADD);
    }
}
