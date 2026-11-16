package com.lumintra.backend.commission;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import com.lumintra.backend.shared.exception.commission.PricingTierInvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                .basePrice(new BigDecimal("100000.00")) // 100k < 100k + 20k = 120k
                .isCustom(false)
                .isActive(true)
                .maxRevisions(1)
                .includedOptions(new HashSet<>(Set.of(opt1)))
                .build();

        assertThatCode(() -> validator.validateTier(basicTier, customTier))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID02 [A]: Preset tier where basePrice >= customCalculatedPrice throws PricingTierInvariantViolationException")
    void validateTier_throwsException_whenBasePriceViolatesPresetDiscountInvariant() {
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
                .basePrice(new BigDecimal("120000.00")) // 120k >= 100k + 20k = 120k (VIOLATION)
                .isCustom(false)
                .isActive(true)
                .maxRevisions(1)
                .includedOptions(new HashSet<>(Set.of(opt1)))
                .build();

        assertThatThrownBy(() -> validator.validateTier(basicTier, customTier))
                .isInstanceOf(PricingTierInvariantViolationException.class)
                .hasMessageContaining("Pricing invariant violated for tier 'BASIC'");
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
    @DisplayName("CMS-01 Validator UTCID09 [A]: TEXT_INPUT rule with priced options throws InvalidRuleConfigurationException")
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
                .hasMessageContaining("TEXT_INPUT rules cannot have priced options");
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
    @DisplayName("CMS-01 Validator UTCID11 [A]: Preset tier bundling dependent option without parent option throws InvalidRuleConfigurationException")
    void validateTier_throwsException_whenDependentOptionBundledWithoutPrerequisite() {
        CommissionPricingTierEntity customTier = CommissionPricingTierEntity.builder()
                .tierCode("CUSTOM")
                .basePrice(new BigDecimal("100000.00"))
                .isCustom(true)
                .isActive(true)
                .maxRevisions(5)
                .build();

        CommissionPriceConfigEntity parentConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FORMAT_TYPE")
                .build();

        CommissionPriceConfigEntity childConfig = CommissionPriceConfigEntity.builder()
                .ruleKey("FRAME_RANGE")
                .dependsOnOptionId(2L) // Depends on option ID 2
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
                .basePrice(new BigDecimal("90000.00")) // 90k < 100k
                .isCustom(false)
                .isActive(true)
                .maxRevisions(3)
                .includedOptions(new HashSet<>(Set.of(childOption))) // Missing parent option ID 2!
                .build();

        assertThatThrownBy(() -> validator.validateTier(presetTier, customTier))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("bundles dependent option '1-5' without bundling its prerequisite option (ID 2)");
    }

    @Test
    @DisplayName("CMS-01 Validator UTCID12 [B]: Boundary calculations for savings amount and discount percent")
    void calculateSavingsAndDiscount_boundaryValues() {
        // When custom price <= tier price -> savings = 0
        BigDecimal savingsZero = validator.calculateSavingsAmount(new BigDecimal("100000.00"), new BigDecimal("120000.00"));
        assertThat(savingsZero).isEqualByComparingTo(BigDecimal.ZERO);

        // When savings = 0 -> discount = 0.00%
        BigDecimal discountZero = validator.calculateDiscountPercent(BigDecimal.ZERO, new BigDecimal("100000.00"));
        assertThat(discountZero).isEqualByComparingTo("0.00");

        // Normal savings & discount
        BigDecimal savings = validator.calculateSavingsAmount(new BigDecimal("180000.00"), new BigDecimal("149000.00"));
        assertThat(savings).isEqualByComparingTo("31000.00");

        BigDecimal discount = validator.calculateDiscountPercent(savings, new BigDecimal("180000.00"));
        assertThat(discount).isEqualByComparingTo("17.22");
    }

    // ============================================================
    // CMS-30 validateCustomRuleCreation Tests
    // ============================================================

    @Test
    @DisplayName("CMS-30 Validator UTCID13 [N]: Valid custom single select rule with 1 default option passes")
    void validateCustomRuleCreation_validSingleSelect_passes() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("BG_STYLE")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionCode("BG_A")
                .optionLabel("Nền A")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .build();

        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionCode("BG_B")
                .optionLabel("Nền B")
                .basePrice(new BigDecimal("10000.00"))
                .isDefault(false)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt1, opt2)));

        assertThatCode(() -> validator.validateCustomRuleCreation(config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID14 [N]: Valid custom multi select rule with multiple default options passes")
    void validateCustomRuleCreation_validMultiSelect_passes() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("EXTRA_ELEMENTS")
                .selectionType(SelectionType.MULTI_SELECT)
                .uiType(UiType.CHECKBOX)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionCode("ELEM_LIGHTING")
                .optionLabel("Hiệu ứng ánh sáng")
                .basePrice(new BigDecimal("20000.00"))
                .isDefault(true)
                .build();

        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionCode("ELEM_PARTICLES")
                .optionLabel("Hiệu ứng hạt bụi")
                .basePrice(new BigDecimal("30000.00"))
                .isDefault(true)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt1, opt2)));

        assertThatCode(() -> validator.validateCustomRuleCreation(config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID15 [N]: Valid TEXT_INPUT rule with 0 options passes")
    void validateCustomRuleCreation_validTextInputWithNoOptions_passes() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("SPECIAL_REQUEST")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .isSystem(false)
                .options(new ArrayList<>())
                .build();

        assertThatCode(() -> validator.validateCustomRuleCreation(config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID16 [N]: Valid TEXT_INPUT rule with zero-priced placeholder option passes")
    void validateCustomRuleCreation_validTextInputWithZeroPricedOption_passes() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("SPECIAL_REQUEST")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt = CommissionPriceOptionEntity.builder()
                .optionCode("TEXT_PLACEHOLDER")
                .optionLabel("Nhập văn bản")
                .basePrice(BigDecimal.ZERO)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt)));

        assertThatCode(() -> validator.validateCustomRuleCreation(config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID17 [A]: Null config throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_nullConfig_throws() {
        assertThatThrownBy(() -> validator.validateCustomRuleCreation(null))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Config entity cannot be null.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID18 [A]: isSystem = true throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_isSystemTrue_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("FORBIDDEN_SYSTEM")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(true) // Violation: custom rules cannot be system rules
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Custom pricing rules must have isSystem set to false.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID19 [A]: Incompatible UI type (SINGLE_SELECT + CHECKBOX) throws")
    void validateCustomRuleCreation_singleSelectWithCheckboxUi_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("INCOMPATIBLE_1")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.CHECKBOX)
                .isSystem(false)
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rules only support RADIO or DROPDOWN UI types.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID20 [A]: Incompatible UI type (MULTI_SELECT + RADIO) throws")
    void validateCustomRuleCreation_multiSelectWithRadioUi_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("INCOMPATIBLE_2")
                .selectionType(SelectionType.MULTI_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("MULTI_SELECT rules only support CHECKBOX UI type.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID21 [A]: Incompatible UI type (TEXT_INPUT + DROPDOWN) throws")
    void validateCustomRuleCreation_textInputWithDropdownUi_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("INCOMPATIBLE_3")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.DROPDOWN)
                .isSystem(false)
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("TEXT_INPUT rules only support TEXT UI type.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID22 [A]: SINGLE_SELECT with 0 options throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_singleSelectWithNoOptions_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("EMPTY_OPTIONS_SS")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .options(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rules must have at least one option.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID23 [A]: MULTI_SELECT with 0 options throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_multiSelectWithNoOptions_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("EMPTY_OPTIONS_MS")
                .selectionType(SelectionType.MULTI_SELECT)
                .uiType(UiType.CHECKBOX)
                .isSystem(false)
                .options(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("MULTI_SELECT rules must have at least one option.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID24 [A]: TEXT_INPUT with priced option (>0.00) throws")
    void validateCustomRuleCreation_textInputWithPricedOption_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("PRICED_TEXT")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt = CommissionPriceOptionEntity.builder()
                .optionCode("PROMPT_FEE")
                .optionLabel("Phí gợi ý")
                .basePrice(new BigDecimal("50000.00")) // Violation!
                .build();

        config.setOptions(new ArrayList<>(List.of(opt)));

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("TEXT_INPUT rules cannot have priced options.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID25 [A]: SINGLE_SELECT with multiple default options throws")
    void validateCustomRuleCreation_singleSelectWithMultipleDefaults_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("MULTI_DEFAULT_SS")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionCode("OPT_1")
                .optionLabel("Tùy chọn 1")
                .isDefault(true)
                .build();

        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionCode("OPT_2")
                .optionLabel("Tùy chọn 2")
                .isDefault(true)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt1, opt2)));

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rule cannot have multiple default options.");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID26 [A]: Duplicate option codes (case-insensitive) within rule throws")
    void validateCustomRuleCreation_duplicateOptionCodesCaseInsensitive_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("DUPLICATE_OPTS")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt1 = CommissionPriceOptionEntity.builder()
                .optionCode("BG_SHADOW")
                .optionLabel("Bóng đổ 1")
                .basePrice(BigDecimal.ZERO)
                .build();

        CommissionPriceOptionEntity opt2 = CommissionPriceOptionEntity.builder()
                .optionCode("bg_shadow") // Case-insensitive duplicate!
                .optionLabel("Bóng đổ 2")
                .basePrice(BigDecimal.ZERO)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt1, opt2)));

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Duplicate option code found within rule: bg_shadow");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID27 [A]: Negative option base price throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_negativeOptionBasePrice_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("NEGATIVE_PRICE_RULE")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt = CommissionPriceOptionEntity.builder()
                .optionCode("OPT_DISCOUNT")
                .optionLabel("Tùy chọn âm tiền")
                .basePrice(new BigDecimal("-10000.00")) // Violation!
                .build();

        config.setOptions(new ArrayList<>(List.of(opt)));

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Option price must be non-negative for option: OPT_DISCOUNT");
    }

    @Test
    @DisplayName("CMS-30 Validator UTCID28 [A]: Blank option code throws InvalidRuleConfigurationException")
    void validateCustomRuleCreation_blankOptionCode_throws() {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey("BLANK_OPT_CODE")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isSystem(false)
                .build();

        CommissionPriceOptionEntity opt = CommissionPriceOptionEntity.builder()
                .optionCode("   ") // Blank
                .optionLabel("Nhãn hợp lệ")
                .basePrice(BigDecimal.ZERO)
                .build();

        config.setOptions(new ArrayList<>(List.of(opt)));

        assertThatThrownBy(() -> validator.validateCustomRuleCreation(config))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Option code cannot be blank.");
    }
}
