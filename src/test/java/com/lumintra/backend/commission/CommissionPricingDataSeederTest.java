package com.lumintra.backend.commission;

import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.bootstrap.CommissionPricingDataSeeder;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionPricingDataSeederTest {

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionPriceOptionRepository optionRepository;

    @Mock
    private CommissionPricingTierRepository tierRepository;

    @Mock
    private PricingInvariantValidator invariantValidator;

    @InjectMocks
    private CommissionPricingDataSeeder seeder;

    @Test
    @DisplayName("CMS-01 Seeder UTCID01 [N]: First run on empty database seeds system starter blueprint rules (with isBasePrice and BASE_PERCENT) and presets")
    void run_seedsSystemRulesAndTiers_whenDatabaseEmpty() {
        when(configRepository.count()).thenReturn(0L);

        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            if ("FORMAT_TYPE".equals(entity.getRuleKey())) {
                entity.setConfigId(1L);
                long optId = 1L;
                for (CommissionPriceOptionEntity opt : entity.getOptions()) {
                    opt.setOptionId(optId++);
                }
            } else if ("MATRIX_SIZE".equals(entity.getRuleKey())) {
                entity.setConfigId(2L);
                long optId = 10L;
                for (CommissionPriceOptionEntity opt : entity.getOptions()) {
                    opt.setOptionId(optId++);
                }
            } else if ("FRAME_RANGE".equals(entity.getRuleKey())) {
                entity.setConfigId(3L);
                long optId = 20L;
                for (CommissionPriceOptionEntity opt : entity.getOptions()) {
                    opt.setOptionId(optId++);
                }
            }
            return entity;
        });

        when(tierRepository.save(any(CommissionPricingTierEntity.class))).thenAnswer(invocation -> {
            CommissionPricingTierEntity entity = invocation.getArgument(0);
            if (entity.getTierId() == null) {
                entity.setTierId(100L);
            }
            return entity;
        });

        seeder.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<CommissionPriceConfigEntity> configCaptor =
                ArgumentCaptor.forClass(CommissionPriceConfigEntity.class);
        verify(configRepository, times(3)).save(configCaptor.capture());

        List<CommissionPriceConfigEntity> savedConfigs = configCaptor.getAllValues();
        assertThat(savedConfigs).extracting(CommissionPriceConfigEntity::getRuleKey)
                .containsExactly("FORMAT_TYPE", "MATRIX_SIZE", "FRAME_RANGE");

        CommissionPriceConfigEntity formatType = savedConfigs.get(0);
        CommissionPriceOptionEntity gifOption = formatType.getOptions().stream()
                .filter(o -> "ANIMATED_GIF".equals(o.getOptionCode()))
                .findFirst()
                .orElseThrow();
        assertThat(gifOption.resolvedModifierType()).isEqualTo(ModifierType.BASE_PERCENT);
        assertThat(gifOption.getBasePrice()).isEqualByComparingTo("50.00");

        CommissionPriceConfigEntity matrixSize = savedConfigs.get(1);
        assertThat(matrixSize.isBasePrice()).isTrue();

        CommissionPriceConfigEntity frameRange = savedConfigs.get(2);
        assertThat(frameRange.getDependsOnOptionId()).isEqualTo(2L);

        ArgumentCaptor<CommissionPricingTierEntity> tierCaptor =
                ArgumentCaptor.forClass(CommissionPricingTierEntity.class);
        verify(tierRepository, times(3)).save(tierCaptor.capture());

        List<CommissionPricingTierEntity> savedTiers = tierCaptor.getAllValues();
        assertThat(savedTiers).extracting(CommissionPricingTierEntity::getTierCode)
                .containsExactly("CUSTOM", "BASIC", "ADVANCED");

        verify(invariantValidator, times(3)).validateRule(any());
        verify(invariantValidator, times(3)).validateTier(any(), any());
    }

    @Test
    @DisplayName("CMS-01 Seeder UTCID02 [N]: Subsequent run on already seeded database skips seeding idempotently")
    void run_skipsSeeding_whenDatabaseAlreadyPopulated() {
        when(configRepository.count()).thenReturn(3L);
        when(tierRepository.count()).thenReturn(3L);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(configRepository, never()).save(any());
        verify(tierRepository, never()).save(any());
        verifyNoInteractions(invariantValidator);
    }
}
