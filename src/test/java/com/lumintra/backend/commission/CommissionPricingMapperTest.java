package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.PricingRuleResponse;
import com.lumintra.backend.commission.api.response.PricingTierSummaryResponse;
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
    @DisplayName("CMS-01 Mapper UTCID01 [N]: toTierSummaryResponse maps preset tier and orders active bundledOptionIds")
    void toTierSummaryResponse_mapsPresetTierWithOrderedBundledIds() {
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
                .displayOrder(2)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionId(20L)
                .config(matrixConfig)
                .optionCode("32x32")
                .basePrice(new BigDecimal("20000.00"))
                .isActive(true)
                .displayOrder(1)
                .build();
        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .config(formatConfig)
                .optionCode("STATIC_IMAGE")
                .basePrice(BigDecimal.ZERO)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .name("Basic")
                .description("Basic package")
                .basePrice(new BigDecimal("79000.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(Set.of(opt1, opt2))
                .build();

        PricingTierSummaryResponse summary = mapper.toTierSummaryResponse(basicTier, customTier);

        assertThat(summary).isNotNull();
        assertThat(summary.tierCode()).isEqualTo("BASIC");
        assertThat(summary.customCalculatedPrice()).isEqualByComparingTo("120000.00");
        assertThat(summary.savingsAmount()).isEqualByComparingTo("41000.00");
        assertThat(summary.bundledOptionIds()).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("CMS-01 Mapper UTCID02 [B]: toPricingRuleResponse handles null input and missing dependency lookup safely")
    void toPricingRuleResponse_handlesNullAndUnresolvedDependency() {
        assertThat(mapper.toPricingRuleResponse(null, Map.of())).isNull();
        assertThat(mapper.toTierSummaryResponse(null, null)).isNull();
        assertThat(mapper.toPricingOptionResponse(null)).isNull();

        CommissionPriceConfigEntity dependentConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("FRAME_RANGE")
                .name("Frame Range")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .dependsOnOptionId(999L)
                .options(List.of())
                .build();

        PricingRuleResponse response = mapper.toPricingRuleResponse(dependentConfig, Map.of());
        assertThat(response.dependsOnOptionId()).isEqualTo(999L);
        assertThat(response.dependsOnRuleKey()).isNull();
        assertThat(response.dependsOnOptionCode()).isNull();
    }
}
