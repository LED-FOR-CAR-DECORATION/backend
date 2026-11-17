package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatePricingRuleUseCaseTest {

    @Test
    void updatesDefaultMatrixRuleAndAppendsAnOptionWithoutChangingExistingIdentifiers() {
        var repository = mock(CommissionPriceConfigRepository.class);
        var detail = mock(GetManagementPricingRuleDetailUseCase.class);
        var rule = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("MATRIX_SIZE")
                .name("Matrix size")
                .isSystem(true)
                .isRequired(true)
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();
        var existing = CommissionPriceOptionEntity.create("32x32", "Small", null, BigDecimal.ZERO, true, 1);
        existing.setOptionId(9L);
        rule.addOption(existing);
        when(repository.findById(1L)).thenReturn(Optional.of(rule));

        var update = new UpdatePricingRuleRequest("LED size", "Choose a matrix size", false,
                List.of(new UpdatePricingRuleRequest.OptionUpdate(9L, "32 × 32 LED", BigDecimal.valueOf(50_000))),
                List.of(new UpdatePricingRuleRequest.NewOption("32 × 64 LED", BigDecimal.valueOf(100_000), "32X64")));
        new UpdatePricingRuleUseCase(repository, new PricingInvariantValidator(), detail).execute(1L, update);

        assertThat(rule.getName()).isEqualTo("LED size");
        assertThat(rule.isRequired()).isTrue();
        assertThat(rule.getOptions()).extracting(CommissionPriceOptionEntity::getOptionCode).containsExactly("32x32", "32x64");
        assertThat(existing.getOptionId()).isEqualTo(9L);
        assertThat(existing.getBasePrice()).isEqualByComparingTo("50000");
        assertThat(rule.getOptions().get(1).getConfig()).isSameAs(rule);
        verify(repository).flush();
        verify(detail).execute(1L);
    }
}
