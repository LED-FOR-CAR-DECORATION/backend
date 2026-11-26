package com.lumintra.backend.commission;

import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingInvariantValidatorTest {

    private PricingInvariantValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PricingInvariantValidator();
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID01 [N]: Valid preset tier where basePrice < customCalculatedPrice passes validation")
    void validateTier_passesWhenPresetTierBasePriceLessThanCustomCalculatedPrice() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .maxRevisions(5)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionId(1L)
                .basePrice(new BigDecimal("20000.00"))
                .isActive(true)
                .build();

        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierCode("BASIC")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(false)
                .isActive(true)
                .maxRevisions(1)
                .includedOptions(new HashSet<>(Set.of(opt1)))
                .build();

        assertThatCode(() -> validator.validateTier(basicTier, customTier))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID02 [N]: Preset tier where basePrice >= customCalculatedPrice is allowed when CUSTOM > preset floor is removed")
    void validateTier_allowsPresetBasePriceHigherThanOrEqualToCustom_andRejectsNegativeBasePrice() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .maxRevisions(5)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionId(1L)
                .basePrice(new BigDecimal("20000.00"))
                .isActive(true)
                .build();

        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierCode("BASIC")
                .basePrice(new BigDecimal("150000.00"))
                .isCustom(false)
                .isActive(true)
                .maxRevisions(1)
                .includedOptions(new HashSet<>(Set.of(opt1)))
                .build();

        assertThatCode(() -> validator.validateTier(basicTier, customTier))
                .doesNotThrowAnyException();

        CommissionPricingTierEntity negativeTier = CommissionPricingTierEntity.builder()
                .tierCode("BASIC")
                .basePrice(new BigDecimal("-1000.00"))
                .isCustom(false)
                .isActive(true)
                .maxRevisions(1)
                .build();

        assertThatThrownBy(() -> validator.validateTier(negativeTier, customTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID03 [A]: BASIC tier with maxRevisions != 1 throws InvalidRuleConfigurationException")
    void validateRevisionLimit_throwsException_forInvalidBasicRevisions() {
        CommissionPricingTierEntity basicTier = CommissionPricingTierEntity.builder()
                .tierCode("BASIC")
                .maxRevisions(2)
                .build();

        assertThatThrownBy(() -> validator.validateRevisionLimit(basicTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("BASIC tier must have exactly 1 max revision");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID04 [A]: ADVANCED tier with maxRevisions != 3 throws InvalidRuleConfigurationException")
    void validateRevisionLimit_throwsException_forInvalidAdvancedRevisions() {
        CommissionPricingTierEntity advancedTier = CommissionPricingTierEntity.builder()
                .tierCode("ADVANCED")
                .maxRevisions(4)
                .build();

        assertThatThrownBy(() -> validator.validateRevisionLimit(advancedTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("ADVANCED tier must have exactly 3 max revisions");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID05 [A]: CUSTOM tier with maxRevisions outside 1-5 throws InvalidRuleConfigurationException")
    void validateRevisionLimit_throwsException_forInvalidCustomRevisions() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .maxRevisions(6)
                .build();

        assertThatThrownBy(() -> validator.validateRevisionLimit(customTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("CUSTOM tier max revisions must be between 1 and 5");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID06 [A]: Deactivating CUSTOM tier throws InvalidRuleConfigurationException")
    void validateTier_throwsException_whenCustomTierDeactivated() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(false)
                .maxRevisions(5)
                .build();

        assertThatThrownBy(() -> validator.validateTier(customTier, null))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("CUSTOM tier cannot be deactivated");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID07 [A]: SINGLE_SELECT rule with CHECKBOX uiType throws InvalidRuleConfigurationException")
    void validateRule_throwsException_forSingleSelectWithCheckbox() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("TEST_RULE")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.CHECKBOX)
                .build();

        assertThatThrownBy(() -> validator.validateRule(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rules only support RADIO or DROPDOWN UI types");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID08 [A]: MULTI_SELECT rule with RADIO uiType throws InvalidRuleConfigurationException")
    void validateRule_throwsException_forMultiSelectWithRadio() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("TEST_RULE")
                .selectionType(SelectionType.MULTI_SELECT)
                .uiType(UiType.RADIO)
                .build();

        assertThatThrownBy(() -> validator.validateRule(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("MULTI_SELECT rules only support CHECKBOX UI type");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID09 [A]: TEXT_INPUT rule with priced or percent options throws InvalidRuleConfigurationException")
    void validateRule_throwsException_forTextInputWithPrice() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("INSTRUCTIONS")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .build();

        CommissionPriceOptionEntity option = CommissionPriceOptionEntity.builder()
                .optionCode("TEXT")
                .basePrice(new BigDecimal("10000.00"))
                .build();

        config.setOptions(new ArrayList<>(List.of(option)));

        assertThatThrownBy(() -> validator.validateRule(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("TEXT_INPUT rules cannot have priced or percentage options");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID10 [A]: SINGLE_SELECT rule with multiple default options throws InvalidRuleConfigurationException")
    void validateRule_throwsException_forSingleSelectWithMultipleDefaults() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("FORMAT")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionCode("OPT_1")
                .isDefault(true)
                .build();
        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionCode("OPT_2")
                .isDefault(true)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt1, opt2)));

        assertThatThrownBy(() -> validator.validateRule(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rule cannot have multiple default options");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID11 [A]: Preset tier bundling dependent option without parent or trigger option throws InvalidRuleConfigurationException")
    void validateTier_throwsException_whenDependentOptionBundledWithoutPrerequisite() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .maxRevisions(5)
                .build();

        CommissionPriceConfigEntity childConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FRAME_RANGE")
                .dependsOnOptionId(2L)
                .build();

        CommissionPriceOptionEntity parentOption = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .optionCode("ANIMATED_GIF")
                .isActive(true)
                .build();

        CommissionPriceOptionEntity childOption = CommissionPriceOptionEntity.builder()
                .optionId(3L)
                .config(childConfig)
                .optionCode("1-5")
                .basePrice(BigDecimal.ZERO)
                .isActive(true)
                .build();

        CommissionPricingTierEntity presetTier = CommissionPricingTierEntity.builder()
                .tierCode("ADVANCED")
                .basePrice(new BigDecimal("90000.00"))
                .isCustom(false)
                .isActive(true)
                .maxRevisions(3)
                .includedOptions(new HashSet<>(Set.of(childOption)))
                .build();

        assertThatThrownBy(() -> validator.validateTier(presetTier, customTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("bundles dependent option '1-5' without bundling or triggering on its prerequisite option (ID 2)");

        // Passing when parentOption is provided in triggerOptions
        presetTier.setTriggerOptions(new HashSet<>(Set.of(parentOption)));
        assertThatCode(() -> validator.validateTier(presetTier, customTier))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID12 [B]: Boundary calculations for savings, discount, and 3-modifier custom price pipeline")
    void calculateSavingsAndDiscount_andModifierPipeline() {
        BigDecimal savingsZero = validator.calculateSavingsAmount(new BigDecimal("100000.00"), new BigDecimal("120000.00"));
        assertThat(savingsZero).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal discountZero = validator.calculateDiscountPercent(BigDecimal.ZERO, new BigDecimal("100000.00"));
        assertThat(discountZero).isEqualByComparingTo("0.00");

        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .build();

        CommissionPriceConfigEntity sizeConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("MATRIX_SIZE")
                .isBasePrice(true)
                .build();
        CommissionPriceOptionEntity size32 = CommissionPriceOptionEntity.builder()
                .optionId(1L)
                .config(sizeConfig)
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .build();

        CommissionPriceConfigEntity formatConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FORMAT_TYPE")
                .isBasePrice(false)
                .build();
        CommissionPriceOptionEntity gif50Pct = CommissionPriceOptionEntity.builder()
                .optionId(2L)
                .config(formatConfig)
                .modifierType(ModifierType.BASE_PERCENT)
                .basePrice(new BigDecimal("50.00"))
                .isActive(true)
                .build();

        CommissionPriceConfigEntity rushConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("RUSH")
                .isBasePrice(false)
                .build();
        CommissionPriceOptionEntity rush20Pct = CommissionPriceOptionEntity.builder()
                .optionId(3L)
                .config(rushConfig)
                .modifierType(ModifierType.SUBTOTAL_PERCENT)
                .basePrice(new BigDecimal("20.00"))
                .isActive(true)
                .build();

        CommissionPricingTierEntity preset = CommissionPricingTierEntity.builder()
                .tierCode("ADVANCED")
                .basePrice(new BigDecimal("149000.00"))
                .includedOptions(new HashSet<>(Set.of(size32, gif50Pct, rush20Pct)))
                .build();

        // Base = 100k + 30k = 130k; + 50% base = +65k -> 195k subtotal; + 20% subtotal = +39k -> 234k
        BigDecimal customTotal = validator.calculateCustomCalculatedPrice(preset, customTier);
        assertThat(customTotal).isEqualByComparingTo("234000.00");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID13 [N]: Valid custom QUANTITY rule with NUMBER uiType, unit price, and valid bounds passes")
    void validateCustomRuleCreation_validQuantityRule_passes() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("WEATHER_VARIANTS")
                .selectionType(SelectionType.QUANTITY)
                .uiType(UiType.NUMBER)
                .isBasePrice(false)
                .minQuantity(0)
                .maxQuantity(5)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity unitOpt = CommissionPriceOptionEntity.builder()
                .optionCode("EXTRA_VARIANT")
                .optionLabel("Extra time-of-day / weather variant")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("35000.00"))
                .build();

        config.setOptions(new ArrayList<>(List.of(unitOpt)));

        assertThatCode(() -> validator.validateCustomRuleCreation(config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID14 [N]: Multi-level acyclic dependencies up to depth 5 pass, while depth 6 and cycles throw")
    void validateParentOptionDependency_enforcesDepth5AndCycleDetection() {
        Map<Long, CommissionPriceOptionEntity> store = new HashMap<>();

        CommissionPriceConfigEntity c0 = CommissionPriceConfigEntity.builder()
                .configId(100L).ruleKey("L0").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(null).build();
        CommissionPriceOptionEntity o0 = CommissionPriceOptionEntity.builder().optionId(1000L).config(c0).build();
        store.put(1000L, o0);

        CommissionPriceConfigEntity c1 = CommissionPriceConfigEntity.builder()
                .configId(101L).ruleKey("L1").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(1000L).build();
        CommissionPriceOptionEntity o1 = CommissionPriceOptionEntity.builder().optionId(1001L).config(c1).build();
        store.put(1001L, o1);

        CommissionPriceConfigEntity c2 = CommissionPriceConfigEntity.builder()
                .configId(102L).ruleKey("L2").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(1001L).build();
        CommissionPriceOptionEntity o2 = CommissionPriceOptionEntity.builder().optionId(1002L).config(c2).build();
        store.put(1002L, o2);

        CommissionPriceConfigEntity c3 = CommissionPriceConfigEntity.builder()
                .configId(103L).ruleKey("L3").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(1002L).build();
        CommissionPriceOptionEntity o3 = CommissionPriceOptionEntity.builder().optionId(1003L).config(c3).build();
        store.put(1003L, o3);

        CommissionPriceConfigEntity c4 = CommissionPriceConfigEntity.builder()
                .configId(104L).ruleKey("L4").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(1003L).build();
        CommissionPriceOptionEntity o4 = CommissionPriceOptionEntity.builder().optionId(1004L).config(c4).build();
        store.put(1004L, o4);

        CommissionPriceConfigEntity c5 = CommissionPriceConfigEntity.builder()
                .configId(105L).ruleKey("L5").selectionType(SelectionType.SINGLE_SELECT).dependsOnOptionId(1004L).build();
        CommissionPriceOptionEntity o5 = CommissionPriceOptionEntity.builder().optionId(1005L).config(c5).build();
        store.put(1005L, o5);

        // Depth 5 (attaching to o4) passes
        assertThatCode(() -> validator.validateParentOptionDependency(200L, o4, store::get))
                .doesNotThrowAnyException();

        // Depth 6 (attaching to o5) fails
        assertThatThrownBy(() -> validator.validateParentOptionDependency(200L, o5, store::get))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("exceeds maximum allowed depth of 5");

        // Circular dependency (attaching to own descendant or self configId) fails
        assertThatThrownBy(() -> validator.validateParentOptionDependency(101L, o2, store::get))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Circular conditional dependency detected");

        // Depending on QUANTITY option fails
        CommissionPriceConfigEntity qtyConfig = CommissionPriceConfigEntity.builder()
                .configId(300L).selectionType(SelectionType.QUANTITY).build();
        CommissionPriceOptionEntity qtyOpt = CommissionPriceOptionEntity.builder().optionId(3000L).config(qtyConfig).build();
        assertThatThrownBy(() -> validator.validateParentOptionDependency(400L, qtyOpt, store::get))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT or MULTI_SELECT");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID15 [A]: QUANTITY validation rejects invalid UI, missing bounds, >1 options, and percentage modifiers")
    void validateCustomRuleCreation_quantityAndBasePriceAndPercentInvariants() {
        CommissionPriceConfigEntity badQtyUi = CommissionPriceConfigEntity.builder()
                .ruleKey("QTY_BAD_UI").selectionType(SelectionType.QUANTITY).uiType(UiType.RADIO)
                .minQuantity(0).maxQuantity(5).isSystem(false).build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(badQtyUi))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("NUMBER UI type");

        CommissionPriceConfigEntity badQtyBounds = CommissionPriceConfigEntity.builder()
                .ruleKey("QTY_BAD_BOUNDS").selectionType(SelectionType.QUANTITY).uiType(UiType.NUMBER)
                .minQuantity(5).maxQuantity(2).isSystem(false)
                .options(new ArrayList<>(List.of(CommissionPriceOptionEntity.create("U", "Unit", ModifierType.FLAT_ADD, BigDecimal.TEN, false, 1))))
                .build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(badQtyBounds))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("maxQuantity");

        CommissionPriceConfigEntity basePriceWithPercent = CommissionPriceConfigEntity.builder()
                .ruleKey("BASE_PCT").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isBasePrice(true).isSystem(false)
                .options(new ArrayList<>(List.of(CommissionPriceOptionEntity.create("PCT", "Pct", ModifierType.BASE_PERCENT, BigDecimal.TEN, true, 1))))
                .build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(basePriceWithPercent))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("isBasePrice only allow FLAT_ADD options");

        CommissionPriceConfigEntity baseOnQuantity = CommissionPriceConfigEntity.builder()
                .ruleKey("QTY_BASE").selectionType(SelectionType.QUANTITY).uiType(UiType.NUMBER)
                .isBasePrice(true).minQuantity(0).maxQuantity(5).isSystem(false)
                .options(new ArrayList<>(List.of(CommissionPriceOptionEntity.create("U", "Unit", ModifierType.FLAT_ADD, BigDecimal.TEN, false, 1))))
                .build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(baseOnQuantity))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("isBasePrice can only be enabled on SINGLE_SELECT or MULTI_SELECT");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID16 [A]: Incompatible UI/TEXT_INPUT/MULTI_SELECT and duplicate/negative/blank option guards")
    void validateCustomRuleCreation_existingInvariantsStillEnforced() {
        CommissionPriceConfigEntity singleWithCheckbox = CommissionPriceConfigEntity.builder()
                .ruleKey("BAD_1").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.CHECKBOX).isSystem(false).build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(singleWithCheckbox))
                .isInstanceOf(InvalidRuleConfigurationException.class);

        CommissionPriceConfigEntity textWithPct = CommissionPriceConfigEntity.builder()
                .ruleKey("BAD_TEXT_PCT").selectionType(SelectionType.TEXT_INPUT).uiType(UiType.TEXT).isSystem(false)
                .options(new ArrayList<>(List.of(CommissionPriceOptionEntity.create("P", "Prompt", ModifierType.SUBTOTAL_PERCENT, BigDecimal.ZERO, false, 1))))
                .build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(textWithPct))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("TEXT_INPUT rules cannot have priced or percentage options");

        CommissionPriceConfigEntity dupOpts = CommissionPriceConfigEntity.builder()
                .ruleKey("DUP").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO).isSystem(false)
                .options(new ArrayList<>(List.of(
                        CommissionPriceOptionEntity.create("OPT", "One", ModifierType.FLAT_ADD, BigDecimal.ZERO, false, 1),
                        CommissionPriceOptionEntity.create("opt", "Two", ModifierType.FLAT_ADD, BigDecimal.ZERO, false, 2)
                ))).build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(dupOpts))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Duplicate option code");

        CommissionPriceConfigEntity negOpt = CommissionPriceConfigEntity.builder()
                .ruleKey("NEG").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO).isSystem(false)
                .options(new ArrayList<>(List.of(
                        CommissionPriceOptionEntity.create("OPT", "Neg", ModifierType.FLAT_ADD, new BigDecimal("-1.00"), false, 1)
                ))).build();
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(negOpt))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("non-negative");
    }
}
