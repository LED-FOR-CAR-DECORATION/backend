package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.CreatePricingOptionRequest;
import com.lumintra.backend.commission.api.request.CreatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
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
    @DisplayName("CMS-30 UseCase UTCID01 [N]: Successfully creates custom single select pricing rule")
    void execute_validSingleSelectRule_persistsAndReturnsResponse() {
        CreatePricingOptionRequest opt1 = CreatePricingOptionRequest.builder()
                .optionCode("BG_FLAT")
                .optionLabel("Nền đơn sắc")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .displayOrder(1)
                .build();

        CreatePricingOptionRequest opt2 = CreatePricingOptionRequest.builder()
                .optionCode("BG_SCENIC")
                .optionLabel("Nền phong cảnh")
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
        assertThat(response.configName()).isEqualTo("Loại hình nền");
        assertThat(response.selectionType()).isEqualTo(SelectionType.SINGLE_SELECT);
        assertThat(response.uiType()).isEqualTo(UiType.RADIO);
        assertThat(response.isRequired()).isTrue();
        assertThat(response.isSystem()).isFalse();
        assertThat(response.isActive()).isTrue();
        assertThat(response.displayOrder()).isEqualTo(5);
        assertThat(response.dependsOnOptionId()).isNull();
        assertThat(response.dependsOnRuleKey()).isNull();
        assertThat(response.dependsOnOptionCode()).isNull();

        assertThat(response.options()).hasSize(2);
        assertThat(response.options().get(0).optionCode()).isEqualTo("BG_FLAT");
        assertThat(response.options().get(0).isDefault()).isTrue();
        assertThat(response.options().get(1).optionCode()).isEqualTo("BG_SCENIC");
        assertThat(response.options().get(1).basePrice()).isEqualByComparingTo(new BigDecimal("70000.00"));

        verify(invariantValidator).validateCustomRuleCreation(any(CommissionPriceConfigEntity.class));
        verify(configRepository).save(any(CommissionPriceConfigEntity.class));
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID02 [N]: Successfully creates rule with 1-level conditional parent dependency")
    void execute_validRuleWithConditionalDependency_resolvesParentAndReturnsResponse() {
        CreatePricingOptionRequest opt = CreatePricingOptionRequest.builder()
                .optionCode("PET_CAT")
                .optionLabel("Thú cưng: Mèo")
                .basePrice(new BigDecimal("30000.00"))
                .isDefault(false)
                .displayOrder(1)
                .build();

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("PET_SPECIES")
                .configName("Loài thú cưng")
                .description("Chọn loài thú cưng kèm theo")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.DROPDOWN)
                .isRequired(false)
                .displayOrder(6)
                .dependsOnOptionId(15L)
                .options(List.of(opt))
                .build();

        CommissionPriceConfigEntity parentConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("INCLUDE_PET")
                .dependsOnOptionId(null)
                .build();

        CommissionPriceOptionEntity parentOption = CommissionPriceOptionEntity.builder()
                .optionId(15L)
                .optionCode("PET_YES")
                .config(parentConfig)
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("PET_SPECIES")).thenReturn(false);
        when(optionRepository.findById(15L)).thenReturn(Optional.of(parentOption));
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(invocation -> {
            CommissionPriceConfigEntity entity = invocation.getArgument(0);
            entity.setConfigId(102L);
            return entity;
        });

        ManagementPricingRuleResponse response = useCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.dependsOnOptionId()).isEqualTo(15L);
        assertThat(response.dependsOnRuleKey()).isEqualTo("INCLUDE_PET");
        assertThat(response.dependsOnOptionCode()).isEqualTo("PET_YES");
        assertThat(response.isSystem()).isFalse();

        verify(optionRepository).findById(15L);
        verify(configRepository).save(any(CommissionPriceConfigEntity.class));
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID03 [N]: Successfully creates TEXT_INPUT rule without options")
    void execute_validTextInputRuleWithNoOptions_persistsAndReturnsResponse() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("CUSTOM_NOTE")
                .configName("Ghi chú tùy chỉnh")
                .description("Nhập ghi chú chi tiết")
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

        assertThat(response).isNotNull();
        assertThat(response.configCode()).isEqualTo("CUSTOM_NOTE");
        assertThat(response.selectionType()).isEqualTo(SelectionType.TEXT_INPUT);
        assertThat(response.uiType()).isEqualTo(UiType.TEXT);
        assertThat(response.options()).isEmpty();
        assertThat(response.isSystem()).isFalse();

        verify(invariantValidator).validateCustomRuleCreation(any(CommissionPriceConfigEntity.class));
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID04 [A]: Duplicate rule code throws DuplicateCommissionRuleException")
    void execute_duplicateRuleCode_throwsDuplicateCommissionRuleException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("FORMAT_TYPE")
                .configName("Định dạng")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("FORMAT_TYPE")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(DuplicateCommissionRuleException.class)
                .hasMessageContaining("Commission pricing rule with code 'FORMAT_TYPE' already exists.");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID05 [A]: Duplicate rule code case-insensitive match throws DuplicateCommissionRuleException")
    void execute_duplicateRuleCodeCaseInsensitive_throwsDuplicateCommissionRuleException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("format_type")
                .configName("Định dạng")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("format_type")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(DuplicateCommissionRuleException.class)
                .hasMessageContaining("Commission pricing rule with code 'format_type' already exists.");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID06 [A]: Null request throws InvalidRuleConfigurationException")
    void execute_nullRequest_throwsInvalidRuleConfigurationException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("CreatePricingRuleRequest cannot be null.");
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID07 [A]: Blank configCode throws InvalidRuleConfigurationException")
    void execute_blankConfigCode_throwsInvalidRuleConfigurationException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("   ")
                .configName("Tên")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .build();

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Rule code is required.");
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID08 [A]: Parent option not found in database throws InvalidRuleConfigurationException")
    void execute_parentOptionNotFound_throwsInvalidRuleConfigurationException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("SUB_OPTION_RULE")
                .configName("Quy tắc phụ thuộc")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .dependsOnOptionId(9999L)
                .options(List.of(CreatePricingOptionRequest.builder()
                        .optionCode("SUB_OPT_1")
                        .optionLabel("Tùy chọn 1")
                        .basePrice(BigDecimal.ZERO)
                        .build()))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("SUB_OPTION_RULE")).thenReturn(false);
        when(optionRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Parent option with ID 9999 does not exist.");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID09 [A]: Multi-level dependency chain (>1 level) throws InvalidRuleConfigurationException")
    void execute_multiLevelDependencyDetected_throwsInvalidRuleConfigurationException() {
        CommissionPriceConfigEntity parentConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("LEVEL_1_RULE")
                .dependsOnOptionId(10L)
                .build();

        CommissionPriceOptionEntity parentOption = CommissionPriceOptionEntity.builder()
                .optionId(20L)
                .optionCode("LEVEL_1_OPT")
                .config(parentConfig)
                .build();

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("LEVEL_2_RULE")
                .configName("Quy tắc cấp 2")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .dependsOnOptionId(20L)
                .options(List.of(CreatePricingOptionRequest.builder()
                        .optionCode("L2_OPT")
                        .optionLabel("Tùy chọn cấp 2")
                        .basePrice(BigDecimal.ZERO)
                        .build()))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("LEVEL_2_RULE")).thenReturn(false);
        when(optionRepository.findById(20L)).thenReturn(Optional.of(parentOption));

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Multi-level conditional dependencies are not supported.");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID10 [B]: Invariant violation thrown by validator prevents save")
    void execute_invariantViolation_propagatesException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .configCode("INVALID_COMBINATION")
                .configName("Quy tắc không hợp lệ")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.CHECKBOX)
                .options(List.of(CreatePricingOptionRequest.builder()
                        .optionCode("OPT_1")
                        .optionLabel("Opt 1")
                        .basePrice(BigDecimal.ZERO)
                        .build()))
                .build();

        when(configRepository.existsByRuleKeyIgnoreCase("INVALID_COMBINATION")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("SINGLE_SELECT rules only support RADIO or DROPDOWN UI types.");

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-30 UseCase UTCID11 [B]: Input trimming and defaulting of optional fields")
    void execute_trimsInputsAndAppliesDefaultValues() {
        CreatePricingOptionRequest opt = CreatePricingOptionRequest.builder()
                .optionCode("  OPT_TRIM  ")
                .optionLabel("  Nhãn tùy chọn  ")
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
        assertThat(savedEntity.getName()).isEqualTo("Tên quy tắc");
        assertThat(savedEntity.isSystem()).isFalse();
        assertThat(savedEntity.isActive()).isTrue();
        assertThat(savedEntity.isRequired()).isFalse();
        assertThat(savedEntity.getDisplayOrder()).isEqualTo(0);

        CommissionPriceOptionEntity savedOption = savedEntity.getOptions().get(0);
        assertThat(savedOption.getOptionCode()).isEqualTo("OPT_TRIM");
        assertThat(savedOption.getOptionLabel()).isEqualTo("Nhãn tùy chọn");
        assertThat(savedOption.getBasePrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedOption.isDefault()).isFalse();
        assertThat(savedOption.getDisplayOrder()).isEqualTo(0);
    }
}
