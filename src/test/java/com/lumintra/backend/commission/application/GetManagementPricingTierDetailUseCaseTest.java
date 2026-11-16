package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetManagementPricingTierDetailUseCaseTest {

    @Mock
    private CommissionPricingTierRepository tierRepository;

    private GetManagementPricingTierDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        PricingInvariantValidator validator = new PricingInvariantValidator();
        CommissionPricingMapper mapper = new CommissionPricingMapper(validator);
        useCase = new GetManagementPricingTierDetailUseCase(tierRepository, mapper);
    }

    @Test
    @DisplayName("CMS-29 TierDetail UTCID01 [N]: Returns full tier details including bundled options and discount calculation")
    void execute_returnsFullTierDetail() {
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
                .name("Format Type")
                .displayOrder(1)
                .build();

        CommissionPriceOptionEntity staticOpt = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .config(formatConfig)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Static PNG")
                .basePrice(BigDecimal.ZERO)
                .isActive(true)
                .displayOrder(1)
                .build();

        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .name("Basic Package")
                .description("Standard basic tier")
                .basePrice(new BigDecimal("79000.00"))
                .maxRevisions(1)
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(Set.of(staticOpt))
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(basicTier));
        when(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(basicTier, customTier));

        ManagementPricingTierResponse response = useCase.execute(1L);

        assertThat(response.tierId()).isEqualTo(1L);
        assertThat(response.tierCode()).isEqualTo("BASIC");
        assertThat(response.customCalculatedPrice()).isEqualByComparingTo("100000.00");
        assertThat(response.savingsAmount()).isEqualByComparingTo("21000.00");
        assertThat(response.discountPercent()).isEqualByComparingTo("21.00");
        assertThat(response.includedOptions()).hasSize(1);
        assertThat(response.includedOptionIds()).containsExactly(10L);
    }

    @Test
    @DisplayName("CMS-29 TierDetail UTCID02 [A]: Throws CommissionTierNotFoundException when tierId does not exist")
    void execute_tierNotFound_throwsException() {
        when(tierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L))
                .isInstanceOf(CommissionTierNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("CMS-29 TierDetail UTCID03 [B]: Rejects null or non-positive tierId with InvalidRuleConfigurationException")
    void execute_invalidTierId_throwsException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidRuleConfigurationException.class);
        assertThatThrownBy(() -> useCase.execute(0L))
                .isInstanceOf(InvalidRuleConfigurationException.class);
    }
}
