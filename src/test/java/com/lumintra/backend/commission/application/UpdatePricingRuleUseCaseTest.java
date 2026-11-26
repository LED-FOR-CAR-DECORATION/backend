package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
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
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import com.lumintra.backend.shared.exception.commission.PricingRuleInUseException;
import com.lumintra.backend.shared.exception.commission.SystemRuleLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatePricingRuleUseCaseTest {

    private CommissionPriceConfigRepository configRepository;
    private CommissionPriceOptionRepository optionRepository;
    private CommissionPricingTierRepository tierRepository;
    private GetManagementPricingRuleDetailUseCase detail;
    private UpdatePricingRuleUseCase useCase;

    /** Options reachable through optionRepository.findById, and rules depending on an option id. */
    private final Map<Long, CommissionPriceOptionEntity> optionStore = new HashMap<>();
    private final Map<Long, List<CommissionPriceConfigEntity>> dependents = new HashMap<>();

    @BeforeEach
    void setUp() {
        configRepository = mock(CommissionPriceConfigRepository.class);
        optionRepository = mock(CommissionPriceOptionRepository.class);
        tierRepository = mock(CommissionPricingTierRepository.class);
        detail = mock(GetManagementPricingRuleDetailUseCase.class);
        useCase = new UpdatePricingRuleUseCase(configRepository, optionRepository, tierRepository,
                new PricingInvariantValidator(), detail);
        when(optionRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(optionStore.get(inv.<Long>getArgument(0))));
        when(configRepository.findAllByIsActiveTrueAndDependsOnOptionIdIn(anyCollection()))
                .thenAnswer(inv -> {
                    Collection<Long> ids = inv.getArgument(0);
                    List<CommissionPriceConfigEntity> result = new ArrayList<>();
                    ids.forEach(id -> result.addAll(dependents.getOrDefault(id, List.of())));
                    return result;
                });
    }

    // ---------------------------------------------------------------- fixtures

    private CommissionPriceConfigEntity rule(long id, String key, boolean system, SelectionType sel, UiType ui) {
        CommissionPriceConfigEntity rule = CommissionPriceConfigEntity.builder()
                .configId(id).ruleKey(key).name(key).isSystem(system).isRequired(system)
                .selectionType(sel).uiType(ui).build();
        when(configRepository.findById(id)).thenReturn(Optional.of(rule));
        return rule;
    }

    private CommissionPriceOptionEntity option(CommissionPriceConfigEntity rule, long id, String code,
                                               ModifierType type, String price) {
        CommissionPriceOptionEntity option = CommissionPriceOptionEntity.create(
                code, code, type, new BigDecimal(price), false, (int) id);
        option.setOptionId(id);
        rule.addOption(option);
        optionStore.put(id, option);
        return option;
    }

    private CommissionPriceConfigEntity matrixRule() {
        CommissionPriceConfigEntity rule = rule(1L, "MATRIX_SIZE", true, SelectionType.SINGLE_SELECT, UiType.RADIO);
        rule.setBasePrice(true);
        option(rule, 9L, "32x32", ModifierType.FLAT_ADD, "0");
        option(rule, 10L, "16x16", ModifierType.FLAT_ADD, "0");
        return rule;
    }

    private CommissionPriceConfigEntity formatRule() {
        CommissionPriceConfigEntity rule = rule(2L, "FORMAT_TYPE", true, SelectionType.SINGLE_SELECT, UiType.RADIO);
        option(rule, 21L, "STATIC_IMAGE", ModifierType.FLAT_ADD, "0");
        option(rule, 22L, "ANIMATED_GIF", ModifierType.BASE_PERCENT, "50");
        return rule;
    }

    private CommissionPriceConfigEntity customRule(long id) {
        CommissionPriceConfigEntity rule = rule(id, "CUSTOM_" + id, false, SelectionType.SINGLE_SELECT, UiType.RADIO);
        option(rule, id * 10 + 1, "A", ModifierType.FLAT_ADD, "10000");
        option(rule, id * 10 + 2, "B", ModifierType.FLAT_ADD, "20000");
        return rule;
    }

    private CommissionPricingTierEntity preset(Set<CommissionPriceOptionEntity> included,
                                               Set<CommissionPriceOptionEntity> triggers) {
        CommissionPricingTierEntity tier = CommissionPricingTierEntity.builder()
                .tierId(5L).tierCode("BASIC").maxRevisions(1).basePrice(new BigDecimal("79000"))
                .includedOptions(included).triggerOptions(triggers).build();
        when(tierRepository.findActiveWithIncludedOptions()).thenReturn(List.of(tier));
        return tier;
    }

    private static UpdatePricingRuleRequest.UpdatePricingRuleRequestBuilder patch() {
        return UpdatePricingRuleRequest.builder();
    }

    // ---------------------------------------------------------------- AC01

    @Test
    void unknownRuleIsNotFound() {
        when(configRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(99L, patch().configName("x").build()))
                .isInstanceOf(CommissionRuleNotFoundException.class);
    }

    // ---------------------------------------------------------------- AC02 system rules

    @Nested
    class SystemRules {

        @Test
        void updatesMatrixRuleAppendsAndRemovesNonCoreOptionWithoutChangingIdentifiers() {
            CommissionPriceConfigEntity rule = matrixRule();
            CommissionPriceOptionEntity existing = optionStore.get(9L);
            CommissionPriceOptionEntity removed = optionStore.get(10L);

            useCase.execute(1L, patch()
                    .configName("LED size").description("Choose a matrix size")
                    .options(List.of(new UpdatePricingRuleRequest.OptionUpdate(9L, "32 × 32 LED", BigDecimal.valueOf(50_000))))
                    .newOptions(List.of(new UpdatePricingRuleRequest.NewOption("32 × 64 LED", BigDecimal.valueOf(100_000), "32X64")))
                    .removedOptionIds(List.of(10L))
                    .build());

            assertThat(rule.getName()).isEqualTo("LED size");
            assertThat(rule.isRequired()).isTrue();
            assertThat(rule.getOptions()).filteredOn(CommissionPriceOptionEntity::isActive)
                    .extracting(CommissionPriceOptionEntity::getOptionCode).containsExactly("32x32", "32x64");
            assertThat(removed.isActive()).isFalse();
            assertThat(removed.getOptionId()).isEqualTo(10L);
            assertThat(existing.getOptionId()).isEqualTo(9L);
            assertThat(existing.getBasePrice()).isEqualByComparingTo("50000");
            assertThat(rule.getOptions().getLast().getConfig()).isSameAs(rule);
            verify(configRepository).flush();
            verify(detail).execute(1L);
        }

        @Test
        void editsDisplayOrderModifierTypeAndOptionDisplayOrder() {
            CommissionPriceConfigEntity rule = formatRule();

            useCase.execute(2L, patch().displayOrder(7)
                    .configCode("FORMAT_TYPE").selectionType(SelectionType.SINGLE_SELECT).isRequired(true)
                    .options(List.of(UpdatePricingRuleRequest.OptionUpdate.builder().optionId(22L)
                            .modifierType(ModifierType.SUBTOTAL_PERCENT).basePrice(new BigDecimal("40"))
                            .displayOrder(5).isActive(true).build()))
                    .build());

            CommissionPriceOptionEntity gif = optionStore.get(22L);
            assertThat(rule.getDisplayOrder()).isEqualTo(7);
            assertThat(gif.getModifierType()).isEqualTo(ModifierType.SUBTOTAL_PERCENT);
            assertThat(gif.getBasePrice()).isEqualByComparingTo("40");
            assertThat(gif.getDisplayOrder()).isEqualTo(5);
            assertThat(gif.getOptionLabel()).isEqualTo("ANIMATED_GIF");
        }

        @Test
        void rejectsChangingConfigCodeOrSelectionType() {
            formatRule();
            assertThatThrownBy(() -> useCase.execute(2L, patch().configCode("FORMAT").build()))
                    .isInstanceOf(SystemRuleLockedException.class);
            assertThatThrownBy(() -> useCase.execute(2L, patch().selectionType(SelectionType.MULTI_SELECT).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
        }

        @Test
        void rejectsCustomOnlyFields() {
            formatRule();
            assertThatThrownBy(() -> useCase.execute(2L, patch().isRequired(false).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
            assertThatThrownBy(() -> useCase.execute(2L, patch().uiType(UiType.DROPDOWN).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
            assertThatThrownBy(() -> useCase.execute(2L, patch().dependsOnOptionId(9L).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
        }

        @Test
        void rejectsDeactivatingTheRule() {
            CommissionPriceConfigEntity rule = formatRule();
            assertThatThrownBy(() -> useCase.execute(2L, patch().isActive(false).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
            assertThatThrownBy(() -> useCase.delete(2L)).isInstanceOf(SystemRuleLockedException.class);
            assertThat(rule.isActive()).isTrue();
        }

        @Test
        void rejectsRemovingOrDeactivatingCoreOptions() {
            matrixRule();
            assertThatThrownBy(() -> useCase.execute(1L, patch().removedOptionIds(List.of(9L)).build()))
                    .isInstanceOf(SystemRuleLockedException.class)
                    .hasMessageContaining("32x32");
            assertThatThrownBy(() -> useCase.execute(1L, patch().options(List.of(
                    UpdatePricingRuleRequest.OptionUpdate.builder().optionId(9L).isActive(false).build())).build()))
                    .isInstanceOf(SystemRuleLockedException.class);
            assertThat(optionStore.get(9L).isActive()).isTrue();
        }

        @Test
        void rejectsPercentModifierOnBaseBucketRule() {
            matrixRule();
            assertThatThrownBy(() -> useCase.execute(1L, patch().options(List.of(
                    UpdatePricingRuleRequest.OptionUpdate.builder().optionId(10L)
                            .modifierType(ModifierType.BASE_PERCENT).build())).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
        }
    }

    // ---------------------------------------------------------------- AC03 custom rules

    @Nested
    class CustomRules {

        @Test
        void updatesCustomFields() {
            CommissionPriceConfigEntity rule = customRule(3L);

            useCase.execute(3L, patch().configName(" Background ").description("  ")
                    .uiType(UiType.DROPDOWN).isBasePrice(true).isRequired(true).displayOrder(9).build());

            assertThat(rule.getName()).isEqualTo("Background");
            assertThat(rule.getDescription()).isNull();
            assertThat(rule.getUiType()).isEqualTo(UiType.DROPDOWN);
            assertThat(rule.isBasePrice()).isTrue();
            assertThat(rule.isRequired()).isTrue();
            assertThat(rule.getDisplayOrder()).isEqualTo(9);
        }

        @Test
        void updatesQuantityBounds() {
            CommissionPriceConfigEntity rule = rule(4L, "EXTRA_TEXT", false, SelectionType.QUANTITY, UiType.NUMBER);
            rule.setMinQuantity(0);
            rule.setMaxQuantity(3);
            option(rule, 41L, "UNIT", ModifierType.FLAT_ADD, "5000");

            useCase.execute(4L, patch().minQuantity(1).maxQuantity(10).build());

            assertThat(rule.getMinQuantity()).isEqualTo(1);
            assertThat(rule.getMaxQuantity()).isEqualTo(10);
        }

        @Test
        void rejectsInvertedQuantityBounds() {
            CommissionPriceConfigEntity rule = rule(4L, "EXTRA_TEXT", false, SelectionType.QUANTITY, UiType.NUMBER);
            rule.setMinQuantity(0);
            rule.setMaxQuantity(3);
            option(rule, 41L, "UNIT", ModifierType.FLAT_ADD, "5000");

            assertThatThrownBy(() -> useCase.execute(4L, patch().minQuantity(5).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
        }

        @Test
        void rejectsIncompatibleUiTypeAndImmutableFields() {
            customRule(3L);
            assertThatThrownBy(() -> useCase.execute(3L, patch().uiType(UiType.CHECKBOX).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
            assertThatThrownBy(() -> useCase.execute(3L, patch().configCode("OTHER").build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
            assertThatThrownBy(() -> useCase.execute(3L, patch().selectionType(SelectionType.MULTI_SELECT).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
            assertThatThrownBy(() -> useCase.execute(3L, patch().configName("  ").build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
        }

        @Test
        void appendsPercentOptionAndSoftDeactivatesUnusedOption() {
            CommissionPriceConfigEntity rule = customRule(3L);

            useCase.execute(3L, patch()
                    .newOptions(List.of(new UpdatePricingRuleRequest.NewOption("Premium", new BigDecimal("15"), null,
                            ModifierType.SUBTOTAL_PERCENT)))
                    .options(List.of(UpdatePricingRuleRequest.OptionUpdate.builder().optionId(32L).isActive(false).build()))
                    .build());

            assertThat(rule.getOptions()).hasSize(3);
            assertThat(rule.getOptions().getLast().getModifierType()).isEqualTo(ModifierType.SUBTOTAL_PERCENT);
            assertThat(optionStore.get(32L).isActive()).isFalse();
        }

        @Test
        void rejectsDeactivatingTheLastActiveOption() {
            customRule(3L);
            assertThatThrownBy(() -> useCase.execute(3L, patch().removedOptionIds(List.of(31L, 32L)).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("ít nhất một phương án");
        }

        @Test
        void setsMultiLevelDependency() {
            CommissionPriceConfigEntity grandParent = customRule(5L);
            CommissionPriceConfigEntity parent = customRule(6L);
            parent.setDependsOnOptionId(51L);
            CommissionPriceConfigEntity rule = customRule(3L);

            useCase.execute(3L, patch().dependsOnOptionId(61L).build());

            assertThat(rule.getDependsOnOptionId()).isEqualTo(61L);
            assertThat(grandParent.isActive()).isTrue();
        }

        @Test
        void rejectsSelfDependency() {
            CommissionPriceConfigEntity rule = customRule(3L);
            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(31L).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("Circular");
            assertThat(rule.getDependsOnOptionId()).isNull();
        }

        @Test
        void rejectsDependencyOnDescendant() {
            CommissionPriceConfigEntity rule = customRule(3L);
            CommissionPriceConfigEntity child = customRule(7L);
            child.setDependsOnOptionId(31L);
            dependents.put(31L, List.of(child));

            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(71L).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("Circular");
            assertThat(rule.getDependsOnOptionId()).isNull();
        }

        @Test
        void allowsDepthFiveButRejectsDepthSixCountingDescendants() {
            Long parentOption = null;
            for (long id = 11; id <= 15; id++) {
                CommissionPriceConfigEntity ancestor = customRule(id);
                ancestor.setDependsOnOptionId(parentOption);
                parentOption = id * 10 + 1;
            }
            CommissionPriceConfigEntity rule = customRule(3L);
            useCase.execute(3L, patch().dependsOnOptionId(parentOption).build());
            assertThat(rule.getDependsOnOptionId()).isEqualTo(151L);

            rule.setDependsOnOptionId(null);
            CommissionPriceConfigEntity child = customRule(8L);
            child.setDependsOnOptionId(31L);
            dependents.put(31L, List.of(child));
            Long deepest = parentOption;
            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(deepest).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("depth");
        }

        @Test
        void rejectsInactiveParentAndConflictingDependencyFlags() {
            CommissionPriceConfigEntity parent = customRule(6L);
            optionStore.get(61L).setActive(false);
            customRule(3L);

            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(61L).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("not active");
            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(62L).clearDependsOnOption(true).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);
            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(999L).build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class)
                    .hasMessageContaining("does not exist");
            assertThat(parent.isActive()).isTrue();
        }

        @Test
        void clearsDependency() {
            customRule(6L);
            CommissionPriceConfigEntity rule = customRule(3L);
            rule.setDependsOnOptionId(61L);

            useCase.execute(3L, patch().clearDependsOnOption(true).build());

            assertThat(rule.getDependsOnOptionId()).isNull();
        }

        @Test
        void deactivatesAndReactivatesUnusedRule() {
            CommissionPriceConfigEntity rule = customRule(3L);

            useCase.execute(3L, patch().isActive(false).build());
            assertThat(rule.isActive()).isFalse();

            assertThatThrownBy(() -> useCase.execute(3L, patch().configName("Again").build()))
                    .isInstanceOf(InvalidRuleConfigurationException.class);

            useCase.execute(3L, patch().isActive(true).build());
            assertThat(rule.isActive()).isTrue();
        }

        @Test
        void deleteSoftDeactivatesUnusedRule() {
            CommissionPriceConfigEntity rule = customRule(3L);
            useCase.delete(3L);
            assertThat(rule.isActive()).isFalse();
            assertThat(rule.getOptions()).allMatch(CommissionPriceOptionEntity::isActive);
        }
    }

    // ---------------------------------------------------------------- AC04 preset integrity

    @Nested
    class PresetIntegrity {

        @Test
        void rejectsDeactivatingOptionBundledByActivePreset() {
            customRule(3L);
            preset(Set.of(optionStore.get(31L)), Set.of());

            assertThatThrownBy(() -> useCase.execute(3L, patch().removedOptionIds(List.of(31L)).build()))
                    .isInstanceOf(PricingRuleInUseException.class);
            assertThat(optionStore.get(31L).isActive()).isTrue();
        }

        @Test
        void rejectsDeactivatingOptionThatTriggersActivePreset() {
            customRule(3L);
            CommissionPriceConfigEntity other = customRule(6L);
            preset(Set.of(optionStore.get(61L)), Set.of(optionStore.get(32L)));

            assertThatThrownBy(() -> useCase.execute(3L, patch().options(List.of(
                    UpdatePricingRuleRequest.OptionUpdate.builder().optionId(32L).isActive(false).build())).build()))
                    .isInstanceOf(PricingRuleInUseException.class);
            assertThat(other.isActive()).isTrue();
        }

        @Test
        void rejectsDeactivatingOptionAnotherActiveRuleDependsOn() {
            customRule(3L);
            when(configRepository.existsByIsActiveTrueAndDependsOnOptionIdIn(Set.of(31L))).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(3L, patch().removedOptionIds(List.of(31L)).build()))
                    .isInstanceOf(PricingRuleInUseException.class);
        }

        @Test
        void rejectsDeactivatingRuleUsedByActivePreset() {
            CommissionPriceConfigEntity rule = customRule(3L);
            preset(Set.of(optionStore.get(32L)), Set.of());

            assertThatThrownBy(() -> useCase.execute(3L, patch().isActive(false).build()))
                    .isInstanceOf(PricingRuleInUseException.class);
            assertThatThrownBy(() -> useCase.delete(3L)).isInstanceOf(PricingRuleInUseException.class);
            assertThat(rule.isActive()).isTrue();
        }

        @Test
        void rejectsDependencyThatLeavesPresetWithoutPrerequisite() {
            customRule(6L);
            CommissionPriceConfigEntity rule = customRule(3L);
            preset(Set.of(optionStore.get(31L)), Set.of());

            assertThatThrownBy(() -> useCase.execute(3L, patch().dependsOnOptionId(61L).build()))
                    .isInstanceOf(PricingRuleInUseException.class);
        }

        @Test
        void allowsDependencyWhenPresetTriggersOnPrerequisite() {
            customRule(6L);
            CommissionPriceConfigEntity rule = customRule(3L);
            preset(Set.of(optionStore.get(31L)), Set.of(optionStore.get(61L)));

            useCase.execute(3L, patch().dependsOnOptionId(61L).build());

            assertThat(rule.getDependsOnOptionId()).isEqualTo(61L);
        }

        @Test
        void bundledOptionPriceCanChangeFreelyWithoutPriceFloor() {
            customRule(3L);
            preset(Set.of(optionStore.get(31L)), Set.of());

            useCase.execute(3L, patch().options(List.of(
                    new UpdatePricingRuleRequest.OptionUpdate(31L, "A", new BigDecimal("999999")))).build());

            assertThat(optionStore.get(31L).getBasePrice()).isEqualByComparingTo("999999");
            verify(tierRepository, never()).findActiveWithIncludedOptions();
        }
    }
}
