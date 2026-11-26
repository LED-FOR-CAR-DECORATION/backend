package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.CreatePricingOptionRequest;
import com.lumintra.backend.commission.api.request.CreatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.shared.exception.commission.DuplicateCommissionRuleException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateCustomPricingRuleUseCaseTest {

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionPriceOptionRepository optionRepository;

    @Spy
    private PricingInvariantValidator invariantValidator = new PricingInvariantValidator();

    private CreateCustomPricingRuleUseCase useCase;

    @BeforeEach
    void setUp() {
        CommissionPricingMapper mapper = new CommissionPricingMapper(invariantValidator);
        useCase = new CreateCustomPricingRuleUseCase(configRepository, optionRepository, invariantValidator, mapper);
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID01 [N]: Successfully creates custom single select pricing rule with isBasePrice and ModifierType")
    void execute_validSingleSelectRule_persistsAndReturnsResponse() {
        CreatePricingOptionRequest opt1 = CreatePricingOptionRequest.builder()
                .optionCode("BG_FLAT")
                .optionLabel("Nền đơn sắc")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .displayOrder(1)
                .build();

        CreatePricingOptionRequest opt2 = CreatePricingOptionRequest.builder()
                .optionCode("BG_SCENIC")
                .optionLabel("Nền phong cảnh")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("70000.00"))
                .isDefault(false)
                .displayOrder(2)
                .build();

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("BACKGROUND_TYPE")
                .configName("Loại hình nền")
                .description("Chọn nền mỹ thuật")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(true)
                .isRequired(true)
                .displayOrder(5)
                .dependsOnOptionId(null)
                .options(List.of(opt1, opt2))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("BACKGROUND_TYPE")).thenReturn(false);
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            entity.setConfigId(101L);
            long optionIdSeq = 201L;
            for (CommissionPriceOptionEntity option : entity.getOptions()) {
                option.setOptionId(optionIdSeq++);
            }
            return entity;
        });

        ManagementPricingRuleResponse response = useCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.configId()).isEqualTo(101L);
        assertThat(response.configCode()).isEqualTo("BACKGROUND_TYPE");
        assertThat(response.isBasePrice()).isTrue();
        assertThat(response.isRequired()).isTrue();
        assertThat(response.isSystem()).isFalse();
        assertThat(response.isActive()).isTrue();
        assertThat(response.options()).hasSize(2);
        assertThat(response.options().get(0).modifierType()).isEqualTo(ModifierType.FLAT_ADD);
        assertThat(response.options().get(1).basePrice()).isEqualByComparingTo("70000.00");

        verify(invariantValidator).validateCustomRuleCreation(any(CommissionPriceConfigEntity.class));
        verify(configRepository).save(any(CommissionPriceConfigEntity.class));
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID02 [N]: Successfully creates rule with multi-level (depth 2) conditional dependency")
    void execute_validMultiLevelConditionalDependency_resolvesParentAndReturnsResponse() {
        CreatePricingOptionRequest opt = CreatePricingOptionRequest.builder()
                .optionCode("HALF_BODY")
                .optionLabel("Half-body View")
                .modifierType(ModifierType.BASE_PERCENT)
                .basePrice(new BigDecimal("25.00"))
                .isDefault(true)
                .displayOrder(1)
                .build();

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("BODY_SCOPE")
                .configName("Phạm vi cơ thể")
                .description("Chọn phạm vi hiển thị nhân vật")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .displayOrder(6)
                .dependsOnOptionId(20L)
                .options(List.of(opt))
                .build();

        CommissionPriceConfigEntity level0Config = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("ART_SUBJECT")
                .selectionType(SelectionType.SINGLE_SELECT)
                .dependsOnOptionId(null)
                .build();

        CommissionPriceOptionEntity level0Option = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .optionCode("CHARACTER_ART")
                .config(level0Config)
                .build();

        CommissionPriceConfigEntity level1Config = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("CHARACTER_VIEW")
                .selectionType(SelectionType.SINGLE_SELECT)
                .dependsOnOptionId(10L)
                .build();

        CommissionPriceOptionEntity level1Option = CommissionPriceOptionEntity.builder()
                .optionId(20L)
                .optionCode("BODY_VIEW")
                .config(level1Config)
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("BODY_SCOPE")).thenReturn(false);
        when(optionRepository.findById(20L)).thenReturn(Optional.of(level1Option));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(level0Option));
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            entity.setConfigId(102L);
            return entity;
        });

        ManagementPricingRuleResponse response = useCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.dependsOnOptionId()).isEqualTo(20L);
        assertThat(response.dependsOnRuleKey()).isEqualTo("CHARACTER_VIEW");
        assertThat(response.dependsOnOptionCode()).isEqualTo("BODY_VIEW");
        assertThat(response.options().get(0).modifierType()).isEqualTo(ModifierType.BASE_PERCENT);
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID03 [N]: Successfully creates QUANTITY stepper rule with bounds and unit rate")
    void execute_validQuantityStepperRule_persistsAndReturnsResponse() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("TIME_WEATHER_VARIANTS")
                .configName("Số lượng biến thể thời tiết / thời gian")
                .description("Thêm các biến thể ngày/đêm/mưa")
                .selectionType(SelectionType.QUANTITY)
                .uiType(UiType.NUMBER)
                .minQuantity(0)
                .maxQuantity(4)
                .isRequired(false)
                .displayOrder(7)
                .options(List.of(CreatePricingOptionRequest.builder()
                        .optionCode("EXTRA_VARIANT")
                        .optionLabel("Biến thể bổ sung")
                        .modifierType(ModifierType.FLAT_ADD)
                        .basePrice(new BigDecimal("35000.00"))
                        .displayOrder(1)
                        .build()))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("TIME_WEATHER_VARIANTS")).thenReturn(false);
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            entity.setConfigId(104L);
            return entity;
        });

        ManagementPricingRuleResponse response = useCase.execute(request);

        assertThat(response.selectionType()).isEqualTo(SelectionType.QUANTITY);
        assertThat(response.uiType()).isEqualTo(UiType.NUMBER);
        assertThat(response.minQuantity()).isEqualTo(0);
        assertThat(response.maxQuantity()).isEqualTo(4);
        assertThat(response.options()).hasSize(1);
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID04 [N]: Successfully creates TEXT_INPUT rule without options")
    void execute_validTextInputRuleWithNoOptions_persistsAndReturnsResponse() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("CUSTOM_NOTE")
                .configName("Ghi chú tùy chỉnh")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .isRequired(false)
                .displayOrder(10)
                .options(List.of())
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("CUSTOM_NOTE")).thenReturn(false);
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            entity.setConfigId(103L);
            return entity;
        });

        ManagementPricingRuleResponse response = useCase.execute(request);

        assertThat(response.configCode()).isEqualTo("CUSTOM_NOTE");
        assertThat(response.selectionType()).isEqualTo(SelectionType.TEXT_INPUT);
        assertThat(response.uiType()).isEqualTo(UiType.TEXT);
        assertThat(response.options()).isEmpty();
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID05 [A]: Duplicate rule code throws DuplicateCommissionRuleException")
    void execute_duplicateRuleCode_throwsDuplicateCommissionRuleException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("FORMAT_TYPE")
                .configName("Định dạng")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("FORMAT_TYPE")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(DuplicateCommissionRuleException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID06 [A]: Dependency depth exceeding 5 levels throws InvalidRuleConfigurationException")
    void execute_dependencyDepthExceeds5_throwsInvalidRuleConfigurationException() {
        for (int level = 0; level <= 5; level++) {
            long optId = 1000L + level;
            long cfgId = 100L + level;
            Long prevOptId = level == 0 ? null : (1000L + level - 1);

            CommissionPriceConfigEntity cfg = CommissionPriceConfigEntity.builder()
                    .configId(cfgId)
                    .ruleKey("L" + level)
                    .selectionType(SelectionType.SINGLE_SELECT)
                    .dependsOnOptionId(prevOptId)
                    .build();
            CommissionPriceOptionEntity opt = CommissionPriceOptionEntity.builder()
                    .optionId(optId)
                    .optionCode("O" + level)
                    .config(cfg)
                    .build();
            if (level == 5 || level < 5) {
                lenient().when(optionRepository.findById(optId)).thenReturn(Optional.of(opt));
            }
        }

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("DEPTH_6_RULE")
                .configName("Too Deep Rule")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .dependsOnOptionId(1005L)
                .options(List.of(CreatePricingOptionRequest.builder()
                        .optionCode("OPT")
                        .optionLabel("Opt")
                        .basePrice(BigDecimal.ZERO)
                        .build()))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("DEPTH_6_RULE")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("exceeds maximum allowed depth of 5");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID07 [A]: Null request, blank rule code, or missing parent option throws InvalidRuleConfigurationException")
    void execute_invalidInputs_throwInvalidRuleConfigurationException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidRuleConfigurationException.class);

        CreatePricingRuleRequest blankCode = CreatePricingRuleRequest.builder()
                .configCode("   ").configName("Name").selectionType(SelectionType.TEXT_INPUT).uiType(UiType.TEXT).build();
        assertThatThrownBy(() -> useCase.execute(blankCode))
                .isInstanceOf(InvalidRuleConfigurationException.class);

        CreatePricingRuleRequest missingParent = CreatePricingRuleRequest.builder()
                .configCode("SUB_RULE").configName("Sub").selectionType(SelectionType.TEXT_INPUT).uiType(UiType.TEXT)
                .dependsOnOptionId(9999L).build();
        when(configRepository.existsByRuleKeyIgnoreCase("SUB_RULE")).thenReturn(false);
        when(optionRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(missingParent))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID08 [B]: Input trimming and defaulting of optional fields and ModifierType")
    void execute_trimsInputsAndAppliesDefaultValues() {
        CreatePricingOptionRequest opt = CreatePricingOptionRequest.builder()
                .optionCode("  OPT_TRIM  ")
                .optionLabel("  Nhãn tùy chọn  ")
                .modifierType(null)
                .basePrice(null)
                .isDefault(null)
                .displayOrder(null)
                .build();

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("  RULE_TRIM  ")
                .configName("  Tên quy tắc  ")
                .description("  Mô tả  ")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(null)
                .isRequired(null)
                .displayOrder(null)
                .options(List.of(opt))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("RULE_TRIM")).thenReturn(false);
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(request);

        ArgumentCaptor<CommissionPriceConfigEntity> captor = ArgumentCaptor.forClass(CommissionPriceConfigEntity.class);
        verify(configRepository).save(captor.capture());
        CommissionPriceConfigEntity savedEntity = captor.getValue();

        assertThat(savedEntity.getRuleKey()).isEqualTo("RULE_TRIM");
        assertThat(savedEntity.isBasePrice()).isFalse();
        assertThat(savedEntity.isSystem()).isFalse();

        CommissionPriceOptionEntity savedOption = savedEntity.getOptions().get(0);
        assertThat(savedOption.getOptionCode()).isEqualTo("OPT_TRIM");
        assertThat(savedOption.resolvedModifierType()).isEqualTo(ModifierType.FLAT_ADD);
        assertThat(savedOption.getBasePrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
