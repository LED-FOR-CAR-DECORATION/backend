package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetManagementPricingRuleDetailUseCaseTest {

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionPriceOptionRepository optionRepository;

    private GetManagementPricingRuleDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        PricingInvariantValidator validator = new PricingInvariantValidator();
        CommissionPricingMapper mapper = new CommissionPricingMapper(validator);
        useCase = new GetManagementPricingRuleDetailUseCase(configRepository, optionRepository, mapper);
    }

    @Test
    @DisplayName("CMS-29 RuleDetail UTCID01 [N]: Returns full rule details including active/inactive options and dependency metadata")
    void execute_returnsFullRuleDetailWithDependency() {
        CommissionPriceConfigEntity parentConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .name("Format Type")
                .build();

        CommissionPriceOptionEntity parentOption = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .config(parentConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Animated GIF")
                .build();

        CommissionPriceConfigEntity frameConfig = CommissionPriceConfigEntity.builder()
                .configId(20L)
                .ruleKey("FRAME_RANGE")
                .name("Frame Range")
                .description("Select frame count")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.DROPDOWN)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(2)
                .dependsOnOptionId(2L)
                .build();

        CommissionPriceOptionEntity frameOpt = CommissionPriceOptionEntity.builder()
                .optionId(30L)
                .config(frameConfig)
                .optionCode("FRAMES_1_15")
                .optionLabel("1-15 Frames")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();
        frameConfig.setOptions(List.of(frameOpt));

        when(configRepository.findById(20L)).thenReturn(Optional.of(frameConfig));
        when(optionRepository.findById(2L)).thenReturn(Optional.of(parentOption));

        ManagementPricingRuleResponse response = useCase.execute(20L);

        assertThat(response.configId()).isEqualTo(20L);
        assertThat(response.configCode()).isEqualTo("FRAME_RANGE");
        assertThat(response.dependsOnOptionId()).isEqualTo(2L);
        assertThat(response.dependsOnRuleKey()).isEqualTo("FORMAT_TYPE");
        assertThat(response.dependsOnOptionCode()).isEqualTo("ANIMATED_GIF");
        assertThat(response.options()).hasSize(1);
    }

    @Test
    @DisplayName("CMS-29 RuleDetail UTCID02 [A]: Throws CommissionRuleNotFoundException when ruleId does not exist")
    void execute_ruleNotFound_throwsException() {
        when(configRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L))
                .isInstanceOf(CommissionRuleNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("CMS-29 RuleDetail UTCID03 [B]: Rejects null or non-positive ruleId with InvalidRuleConfigurationException")
    void execute_invalidRuleId_throwsException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidRuleConfigurationException.class);
        assertThatThrownBy(() -> useCase.execute(-5L))
                .isInstanceOf(InvalidRuleConfigurationException.class);
    }
}
