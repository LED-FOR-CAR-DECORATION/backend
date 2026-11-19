package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdateCommissionPresetsRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateCommissionPresetsUseCaseTest {

    private CommissionPricingTierRepository tierRepository;
    private UpdateCommissionPresetsUseCase useCase;

    private final List<CommissionPricingTierEntity> tiers = new ArrayList<>();
    private final Map<Long, CommissionPriceOptionEntity> options = new HashMap<>();
    private CommissionPricingTierEntity customTier;

    // Rule tree: FORMAT (single) -> o1 STATIC, o2 ANIMATED; FRAME_RANGE (single, depends on o2) -> o3, o4;
    // FRAME_EFFECTS (multi, depends on o3) -> o5, o6; QUANTITY rule -> o7; inactive option o8 in FORMAT.
    @BeforeEach
    void setUp() {
        tierRepository = mock(CommissionPricingTierRepository.class);
        CommissionPriceOptionRepository optionRepository = mock(CommissionPriceOptionRepository.class);
        PricingInvariantValidator validator = new PricingInvariantValidator();
        useCase = new UpdateCommissionPresetsUseCase(tierRepository, optionRepository, validator,
                new CommissionPricingMapper(validator));

        when(tierRepository.findAll()).thenAnswer(inv -> new ArrayList<>(tiers));
        when(optionRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            List<CommissionPriceOptionEntity> found = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (options.containsKey(id)) found.add(options.get(id));
            }
            return found;
        });

        CommissionPriceConfigEntity format = rule(1L, "FORMAT_TYPE", SelectionType.SINGLE_SELECT, null);
        option(1L, format, true);
        option(2L, format, true);
        option(8L, format, false);
        CommissionPriceConfigEntity frames = rule(2L, "FRAME_RANGE", SelectionType.SINGLE_SELECT, 2L);
        option(3L, frames, true);
        option(4L, frames, true);
        CommissionPriceConfigEntity effects = rule(3L, "FRAME_EFFECTS", SelectionType.MULTI_SELECT, 3L);
        option(5L, effects, true);
        option(6L, effects, true);
        CommissionPriceConfigEntity quantity = rule(4L, "TEXT_LINES", SelectionType.QUANTITY, null);
        option(7L, quantity, true);

        customTier = CommissionPricingTierEntity.builder()
                .tierId(1L).tierCode("CUSTOM").name("Custom").basePrice(new BigDecimal("100000"))
                .maxRevisions(5).isCustom(true).isActive(true).displayOrder(1).build();
        tiers.add(customTier);
    }

    private static CommissionPriceConfigEntity rule(long id, String key, SelectionType type, Long dependsOn) {
        UiType ui = switch (type) {
            case SINGLE_SELECT -> UiType.RADIO;
            case MULTI_SELECT -> UiType.CHECKBOX;
            case QUANTITY -> UiType.NUMBER;
            case TEXT_INPUT -> UiType.TEXT;
        };
        return CommissionPriceConfigEntity.builder().configId(id).ruleKey(key).name(key)
                .selectionType(type).uiType(ui).isActive(true).dependsOnOptionId(dependsOn).build();
    }

    private void option(long id, CommissionPriceConfigEntity rule, boolean active) {
        options.put(id, CommissionPriceOptionEntity.builder().optionId(id).optionCode("OPT_" + id)
                .optionLabel("Option " + id).config(rule).basePrice(new BigDecimal("10000"))
                .isActive(active).build());
    }

    private static UpdateCommissionPresetsRequest.Preset.PresetBuilder preset(String code, int revisions, Long... included) {
        return UpdateCommissionPresetsRequest.Preset.builder()
                .tierCode(code).tierName(code + " package").basePrice(new BigDecimal("150000"))
                .maxRevisions(revisions).includedOptionIds(Arrays.asList(included));
    }

    private static UpdateCommissionPresetsRequest.Preset.PresetBuilder custom() {
        return UpdateCommissionPresetsRequest.Preset.builder()
                .tierCode("CUSTOM").tierName("Custom design").basePrice(new BigDecimal("120000")).maxRevisions(3);
    }

    private List<ManagementPricingTierResponse> run(UpdateCommissionPresetsRequest.Preset... presets) {
        return useCase.execute(new UpdateCommissionPresetsRequest(List.of(presets)));
    }

    private void assertRejected(String message, UpdateCommissionPresetsRequest.Preset... presets) {
        assertThatThrownBy(() -> run(presets))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining(message);
        verify(tierRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<CommissionPricingTierEntity> saved() {
        ArgumentCaptor<Collection<CommissionPricingTierEntity>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(tierRepository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private CommissionPricingTierEntity existingPreset(String code, int revisions) {
        CommissionPricingTierEntity tier = CommissionPricingTierEntity.builder()
                .tierId(10L + tiers.size()).tierCode(code).name(code).basePrice(new BigDecimal("90000"))
                .maxRevisions(revisions).isActive(true).displayOrder(5)
                .includedOptions(new HashSet<>(List.of(options.get(1L)))).build();
        tiers.add(tier);
        return tier;
    }

    @Nested
    class CustomBaseMode {

        @Test
        void updatesCustomFieldsAndReturnsEveryTier() {
            existingPreset("BASIC", 1);

            List<ManagementPricingTierResponse> result = run(custom().description(" Free configuration ")
                    .extraRevisionFeePercent(new BigDecimal("15")).displayOrder(0).build());

            assertThat(customTier.getName()).isEqualTo("Custom design");
            assertThat(customTier.getDescription()).isEqualTo("Free configuration");
            assertThat(customTier.getBasePrice()).isEqualByComparingTo("120000");
            assertThat(customTier.getMaxRevisions()).isEqualTo(3);
            assertThat(customTier.getExtraRevisionFeePercent()).isEqualByComparingTo("15");
            assertThat(customTier.isActive()).isTrue();
            assertThat(saved()).containsExactly(customTier);
            assertThat(result).extracting(ManagementPricingTierResponse::tierCode).containsExactly("CUSTOM", "BASIC");
        }

        @Test
        void cannotBeDeactivated() {
            assertRejected("CUSTOM tier cannot be deactivated", custom().isActive(false).build());
        }

        @Test
        void cannotBundleOrTriggerOnOptions() {
            assertRejected("cannot bundle options", custom().includedOptionIds(List.of(1L)).build());
            assertRejected("cannot bundle options", custom().triggerOptionIds(List.of(1L)).build());
        }

        @Test
        void cannotCarryADiscount() {
            assertRejected("cannot have a bundle discount", custom().discountPercent(new BigDecimal("5")).build());
        }

        @Test
        void revisionsMustStayBetweenOneAndFive() {
            // @Max(5) guards the API; the validator is the second line of defence.
            assertRejected("between 1 and 5", custom().maxRevisions(6).build());
        }

        @Test
        void isCreatedWhenMissing() {
            tiers.clear();
            run(custom().build());
            CommissionPricingTierEntity created = saved().get(0);
            assertThat(created.isCustom()).isTrue();
            assertThat(created.getTierCode()).isEqualTo("CUSTOM");
        }

        @Test
        void priceIsNotForcedAbovePresets() {
            run(custom().basePrice(BigDecimal.ZERO).build(),
                    preset("BASIC", 1, 1L).basePrice(new BigDecimal("500000")).build());
            assertThat(customTier.getBasePrice()).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Presets {

        @Test
        void createsPresetWithDefaults() {
            run(preset("basic", 1, 1L).build());

            CommissionPricingTierEntity created = saved().get(0);
            assertThat(created.getTierCode()).isEqualTo("BASIC");
            assertThat(created.isCustom()).isFalse();
            assertThat(created.isActive()).isTrue();
            assertThat(created.getDiscountPercent()).isEqualByComparingTo("0");
            assertThat(created.getExtraRevisionFeePercent()).isEqualByComparingTo("10");
            assertThat(created.getDisplayOrder()).isZero();
            assertThat(created.getIncludedOptions()).containsExactly(options.get(1L));
            assertThat(created.getTriggerOptions()).isEmpty();
        }

        @Test
        void replacesExistingPresetConfiguration() {
            CommissionPricingTierEntity advanced = existingPreset("ADVANCED", 3);

            run(preset("Advanced", 3, 2L, 3L).discountPercent(new BigDecimal("12.5"))
                    .triggerOptionIds(List.of(2L)).displayOrder(2).build());

            assertThat(saved()).containsExactly(advanced);
            assertThat(advanced.getIncludedOptions()).extracting(CommissionPriceOptionEntity::getOptionId)
                    .containsExactlyInAnyOrder(2L, 3L);
            assertThat(advanced.getTriggerOptions()).extracting(CommissionPriceOptionEntity::getOptionId)
                    .containsExactly(2L);
            assertThat(advanced.getDiscountPercent()).isEqualByComparingTo("12.5");
            assertThat(advanced.getDisplayOrder()).isEqualTo(2);
        }

        @Test
        void canBeDeactivated() {
            CommissionPricingTierEntity basic = existingPreset("BASIC", 1);
            run(preset("BASIC", 1, 1L).isActive(false).build());
            assertThat(basic.isActive()).isFalse();
        }

        @Test
        void inactivePresetMayKeepInactiveOptions() {
            run(preset("BASIC", 1, 8L).isActive(false).build());
            assertThat(saved().get(0).getIncludedOptions()).containsExactly(options.get(8L));
        }

        @Test
        void allowsOtherPresetCodesWithinOneToFiveRevisions() {
            run(preset("PREMIUM", 5, 1L).build());
            assertThat(saved().get(0).getMaxRevisions()).isEqualTo(5);
        }

        @Test
        void enforcesFixedRevisionQuotas() {
            assertRejected("BASIC tier must have exactly 1", preset("BASIC", 2, 1L).build());
            assertRejected("ADVANCED tier must have exactly 3", preset("ADVANCED", 1, 1L).build());
        }

        @Test
        void rejectsDuplicateCodes() {
            assertRejected("listed more than once", preset("BASIC", 1, 1L).build(), preset("basic", 1, 2L).build());
        }

        @Test
        void rejectsReservedCustomCodeForAPreset() {
            tiers.clear();
            existingPreset("CUSTOM", 1).setCustom(false);
            assertRejected("reserved", custom().build());
        }

        @Test
        void rejectsUnknownOption() {
            assertRejected("Option with ID 99 does not exist", preset("BASIC", 1, 99L).build());
        }

        @Test
        void rejectsInactiveBundledOrTriggerOption() {
            assertRejected("inactive option ID 8", preset("BASIC", 1, 8L).build());
            assertRejected("inactive option ID 8", preset("BASIC", 1, 1L).triggerOptionIds(List.of(8L)).build());
        }

        @Test
        void rejectsOptionOfInactiveRule() {
            options.get(1L).getConfig().setActive(false);
            assertRejected("inactive option ID 1", preset("BASIC", 1, 1L).build());
        }

        @Test
        void rejectsBundlingNonChoiceRuleOptions() {
            assertRejected("SINGLE_SELECT or MULTI_SELECT", preset("BASIC", 1, 7L).build());
        }

        @Test
        void rejectsTwoOptionsOfOneSingleSelectRule() {
            assertRejected("more than one option of single-select rule 'FORMAT_TYPE'", preset("BASIC", 1, 1L, 2L).build());
        }

        @Test
        void allowsSeveralOptionsOfOneMultiSelectRule() {
            run(preset("ADVANCED", 3, 2L, 3L, 5L, 6L).build());
            assertThat(saved().get(0).getIncludedOptions()).hasSize(4);
        }

        @Test
        void emptyBundleAndTriggersAreAllowed() {
            run(preset("BASIC", 1).includedOptionIds(null).build());
            CommissionPricingTierEntity created = saved().get(0);
            assertThat(created.getIncludedOptions()).isEmpty();
            assertThat(created.getTriggerOptions()).isEmpty();
        }
    }

    @Nested
    class MultiLevelDependencies {

        @Test
        void rejectsChildWithoutDirectParent() {
            assertRejected("without bundling or triggering on its prerequisite option (ID 2)",
                    preset("ADVANCED", 3, 3L).build());
        }

        @Test
        void rejectsGrandchildWhoseGrandparentIsMissing() {
            // o5 needs o3 (bundled) which needs o2 (missing).
            assertRejected("prerequisite option (ID 2)", preset("ADVANCED", 3, 3L, 5L).build());
        }

        @Test
        void acceptsFullyBundledChain() {
            run(preset("ADVANCED", 3, 2L, 3L, 5L).build());
            assertThat(saved()).hasSize(1);
        }

        @Test
        void acceptsChainCompletedByTriggers() {
            run(preset("ADVANCED", 3, 5L).triggerOptionIds(List.of(2L, 3L)).build());
            assertThat(saved().get(0).getTriggerOptions()).hasSize(2);
        }

        @Test
        void inactivePresetSkipsDependencyCheck() {
            run(preset("ADVANCED", 3, 5L).isActive(false).build());
            assertThat(saved().get(0).isActive()).isFalse();
        }

        @Test
        void rejectsChainDeeperThanFiveLevels() {
            // Six chained single-select rules on top of o2: r10 -> r11 -> ... -> r15.
            Long parent = 2L;
            List<Long> chain = new ArrayList<>(List.of(2L));
            for (long i = 0; i < 6; i++) {
                long optionId = 100 + i;
                option(optionId, rule(10 + i, "LEVEL_" + i, SelectionType.SINGLE_SELECT, parent), true);
                chain.add(optionId);
                parent = optionId;
            }
            assertRejected("exceeds the maximum depth of 5",
                    preset("ADVANCED", 3, chain.stream().filter(Objects::nonNull).toArray(Long[]::new)).build());
        }
    }
}
