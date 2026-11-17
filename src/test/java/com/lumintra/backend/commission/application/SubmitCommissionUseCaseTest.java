package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.CommissionAttachmentItemRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.SelectedOptionItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.application.service.StaffAutoAssignmentService;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.media.domain.service.MediaUrlValidator;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionRuleSelectionException;
import com.lumintra.backend.shared.exception.commission.PresetSelectionMismatchException;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitCommissionUseCaseTest {

    @Mock
    private CommissionPricingTierRepository tierRepository;

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionAttachmentRepository attachmentRepository;

    @Mock
    private StaffAutoAssignmentService staffAutoAssignmentService;

    @Mock
    private MediaUrlValidator mediaUrlValidator;

    private PricingInvariantValidator pricingInvariantValidator;
    private CommissionRequestMapper commissionRequestMapper;
    private ObjectMapper objectMapper;
    private SubmitCommissionUseCase useCase;

    private CommissionPriceConfigEntity formatTypeConfig;
    private CommissionPriceOptionEntity staticImageOption;
    private CommissionPriceOptionEntity animatedGifOption;

    private CommissionPriceConfigEntity matrixSizeConfig;
    private CommissionPriceOptionEntity size16Option;
    private CommissionPriceOptionEntity size32Option;

    private CommissionPriceConfigEntity frameRangeConfig;
    private CommissionPriceOptionEntity frame1To5Option;
    private CommissionPriceOptionEntity frame6To15Option;

    private CommissionPricingTierEntity basicTier;
    private CommissionPricingTierEntity advancedTier;
    private CommissionPricingTierEntity customTier;

    @BeforeEach
    void setUp() {
        pricingInvariantValidator = new PricingInvariantValidator();
        commissionRequestMapper = new CommissionRequestMapper();
        objectMapper = new ObjectMapper();
        useCase = new SubmitCommissionUseCase(
                tierRepository,
                configRepository,
                commissionRequestRepository,
                attachmentRepository,
                staffAutoAssignmentService,
                mediaUrlValidator,
                pricingInvariantValidator,
                commissionRequestMapper,
                objectMapper
        );

        formatTypeConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .name("Định dạng hiển thị")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .options(new ArrayList<>())
                .build();

        staticImageOption = CommissionPriceOptionEntity.builder()
                .optionId(10L)
                .config(formatTypeConfig)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Ảnh tĩnh (PNG)")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        formatTypeConfig.getOptions().add(staticImageOption);

        animatedGifOption = CommissionPriceOptionEntity.builder()
                .optionId(11L)
                .config(formatTypeConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Ảnh động (GIF) (+50% Base)")
                .modifierType(ModifierType.BASE_PERCENT)
                .basePrice(new BigDecimal("50.00"))
                .isActive(true)
                .build();
        formatTypeConfig.getOptions().add(animatedGifOption);

        matrixSizeConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("MATRIX_SIZE")
                .name("Kích thước ma trận")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(true)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .options(new ArrayList<>())
                .build();

        size16Option = CommissionPriceOptionEntity.builder()
                .optionId(20L)
                .config(matrixSizeConfig)
                .optionCode("16x16")
                .optionLabel("16 x 16 Pixels")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        matrixSizeConfig.getOptions().add(size16Option);

        size32Option = CommissionPriceOptionEntity.builder()
                .optionId(21L)
                .config(matrixSizeConfig)
                .optionCode("32x32")
                .optionLabel("32 x 32 Pixels")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .build();
        matrixSizeConfig.getOptions().add(size32Option);

        frameRangeConfig = CommissionPriceConfigEntity.builder()
                .configId(3L)
                .ruleKey("FRAME_RANGE")
                .name("Số khung hình")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .dependsOnOptionId(11L)
                .options(new ArrayList<>())
                .build();

        frame1To5Option = CommissionPriceOptionEntity.builder()
                .optionId(30L)
                .config(frameRangeConfig)
                .optionCode("1-5")
                .optionLabel("1-5 Khung hình")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        frameRangeConfig.getOptions().add(frame1To5Option);

        frame6To15Option = CommissionPriceOptionEntity.builder()
                .optionId(31L)
                .config(frameRangeConfig)
                .optionCode("6-15")
                .optionLabel("6-15 Khung hình")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .build();
        frameRangeConfig.getOptions().add(frame6To15Option);

        basicTier = CommissionPricingTierEntity.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .name("Gói Cơ Bản")
                .basePrice(new BigDecimal("79000.00"))
                .discountPercent(new BigDecimal("21.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .includedOptions(new HashSet<>(Set.of(staticImageOption, size16Option)))
                .triggerOptions(new HashSet<>())
                .build();

        advancedTier = CommissionPricingTierEntity.builder()
                .tierId(2L)
                .tierCode("ADVANCED")
                .name("Gói Nâng Cao")
                .basePrice(new BigDecimal("149000.00"))
                .discountPercent(new BigDecimal("23.59"))
                .maxRevisions(3)
                .isCustom(false)
                .isActive(true)
                .includedOptions(new HashSet<>(Set.of(animatedGifOption, size32Option, frame1To5Option)))
                .triggerOptions(new HashSet<>())
                .build();

        customTier = CommissionPricingTierEntity.builder()
                .tierId(3L)
                .tierCode("CUSTOM")
                .name("Gói Tùy Chỉnh")
                .basePrice(new BigDecimal("100000.00"))
                .discountPercent(BigDecimal.ZERO)
                .maxRevisions(5)
                .isCustom(true)
                .isActive(true)
                .includedOptions(new HashSet<>())
                .triggerOptions(new HashSet<>())
                .build();
    }

    private void mockDefaultRules() {
        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig));
    }

    private void stubSaveCommission(long id) {
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(id);
                    return entity;
                });
    }

    @Test
    @DisplayName("CMS-03 UTCID01 [N]: Submit commission with ADVANCED preset via unified selectedOptions succeeds")
    void submitCommission_presetAdvanced_success_returnsCreated() {
        Long customerId = 42L;
        mockDefaultRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("ADVANCED")).thenReturn(Optional.of(advancedTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(3L);
        stubSaveCommission(501L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Cyberpunk Shiba",
                "Shiba in neon rain",
                "ADVANCED",
                List.of(
                        new SelectedOptionItem(1L, List.of(11L), null, null),
                        new SelectedOptionItem(2L, List.of(21L), null, null),
                        new SelectedOptionItem(3L, List.of(30L), null, null)
                ),
                "32x32",
                List.of()
        );

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response.commissionId()).isEqualTo(501L);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.tierCode()).isEqualTo("ADVANCED");
        assertThat(response.targetMatrixDimensions()).isEqualTo("32x32");
        assertThat(response.initialPrice()).isEqualByComparingTo("149000.00");
        assertThat(response.totalPrice()).isEqualByComparingTo("149000.00");
        assertThat(response.maxRevisions()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(CommissionStatus.PENDING_REVIEW);

        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        CommissionRequestEntity saved = captor.getValue();
        assertThat(saved.getAssignedStaffId()).isEqualTo(3L);
        assertThat(saved.getPriceBreakdownJson()).contains("ADVANCED").contains("\"includedInPreset\":true");
    }

    @Test
    @DisplayName("CMS-03 UTCID02 [N]: Submit BASIC preset auto-derives targetMatrixDimensions and allows optional add-on")
    void submitCommission_presetBasicWithAddonAndTriggers_success() {
        CommissionPriceConfigEntity rushConfig = CommissionPriceConfigEntity.builder()
                .configId(4L)
                .ruleKey("RUSH_DELIVERY")
                .name("Giao hàng nhanh")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isBasePrice(false)
                .isRequired(false)
                .isSystem(false)
                .isActive(true)
                .options(new ArrayList<>())
                .build();
        CommissionPriceOptionEntity rush20 = CommissionPriceOptionEntity.builder()
                .optionId(40L)
                .config(rushConfig)
                .optionCode("RUSH_24H")
                .optionLabel("Trong 24h (+20% Subtotal)")
                .modifierType(ModifierType.SUBTOTAL_PERCENT)
                .basePrice(new BigDecimal("20.00"))
                .isActive(true)
                .build();
        rushConfig.getOptions().add(rush20);

        basicTier.setTriggerOptions(Set.of(staticImageOption));

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig, rushConfig));
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(3L);
        stubSaveCommission(502L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Pixel Apple",
                "Red pixel apple",
                "BASIC",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null),
                        new SelectedOptionItem(4L, List.of(40L), null, null)
                ),
                null,
                List.of()
        );

        CustomerCommissionDetailResponse response = useCase.execute(42L, request);

        // Preset base 79,000 + 20% SUBTOTAL_PERCENT (15,800) = 94,800.00
        assertThat(response.targetMatrixDimensions()).isEqualTo("16x16");
        assertThat(response.initialPrice()).isEqualByComparingTo("94800.00");
        assertThat(response.totalPrice()).isEqualByComparingTo("94800.00");
    }

    @Test
    @DisplayName("CMS-03 UTCID03 [N]: CUSTOM mode computes 4-stage pipeline with all 4 primitives and 3 modifiers and snapshots cleanly")
    void submitCommission_customAllPrimitivesAndModifiers_calculatesAndSnapshotsCorrectly() throws Exception {
        CommissionPriceConfigEntity variantConfig = CommissionPriceConfigEntity.builder()
                .configId(4L)
                .ruleKey("WEATHER_VARIANTS")
                .name("Biến thể thời tiết")
                .selectionType(SelectionType.QUANTITY)
                .uiType(UiType.NUMBER)
                .minQuantity(0)
                .maxQuantity(5)
                .isBasePrice(false)
                .isRequired(false)
                .isSystem(false)
                .isActive(true)
                .options(new ArrayList<>())
                .build();
        CommissionPriceOptionEntity variantOpt = CommissionPriceOptionEntity.builder()
                .optionId(40L)
                .config(variantConfig)
                .optionCode("EXTRA_VARIANT")
                .optionLabel("Mỗi biến thể")
                .modifierType(ModifierType.FLAT_ADD)
                .basePrice(new BigDecimal("35000.00"))
                .isActive(true)
                .build();
        variantConfig.getOptions().add(variantOpt);

        CommissionPriceConfigEntity addonsConfig = CommissionPriceConfigEntity.builder()
                .configId(5L)
                .ruleKey("LICENSE_RIGHTS")
                .name("Quyền sử dụng")
                .selectionType(SelectionType.MULTI_SELECT)
                .uiType(UiType.CHECKBOX)
                .isBasePrice(false)
                .isRequired(false)
                .isSystem(false)
                .isActive(true)
                .options(new ArrayList<>())
                .build();
        CommissionPriceOptionEntity commercialOpt = CommissionPriceOptionEntity.builder()
                .optionId(50L)
                .config(addonsConfig)
                .optionCode("COMMERCIAL")
                .optionLabel("Thương mại (+20% Subtotal)")
                .modifierType(ModifierType.SUBTOTAL_PERCENT)
                .basePrice(new BigDecimal("20.00"))
                .isActive(true)
                .build();
        addonsConfig.getOptions().add(commercialOpt);

        CommissionPriceConfigEntity noteConfig = CommissionPriceConfigEntity.builder()
                .configId(6L)
                .ruleKey("PALETTE_HEX")
                .name("Ghi chú bảng màu")
                .selectionType(SelectionType.TEXT_INPUT)
                .uiType(UiType.TEXT)
                .isBasePrice(false)
                .isRequired(false)
                .isSystem(false)
                .isActive(true)
                .options(new ArrayList<>())
                .build();

        when(configRepository.findActiveWithActiveOptions()).thenReturn(
                List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig, variantConfig, addonsConfig, noteConfig)
        );
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(4L);
        stubSaveCommission(503L);

        // Math:
        // Base = 100k (CUSTOM) + 30k (MATRIX_SIZE 32x32, isBasePrice=true) = 130,000.00
        // + BASE_PERCENT (ANIMATED_GIF 50% of 130k) = +65,000.00
        // + non-base FLAT_ADD (6-15 frames 30k) + QUANTITY (2 * 35k = 70k) = +100,000.00 -> RunningSubtotal = 295,000.00
        // + SUBTOTAL_PERCENT (COMMERCIAL 20% of 295k) = +59,000.00
        // Final Total = 354,000.00
        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Custom Cyber City",
                "Dense parallax cityscape",
                "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(11L), null, null),
                        new SelectedOptionItem(2L, List.of(21L), null, null),
                        new SelectedOptionItem(3L, List.of(31L), null, null),
                        new SelectedOptionItem(4L, List.of(), 2, null),
                        new SelectedOptionItem(5L, List.of(50L), null, null),
                        new SelectedOptionItem(6L, List.of(), null, "#00FFCC / #FF00AA")
                ),
                "32x32",
                List.of()
        );

        CustomerCommissionDetailResponse response = useCase.execute(42L, request);

        assertThat(response.initialPrice()).isEqualByComparingTo("354000.00");
        assertThat(response.totalPrice()).isEqualByComparingTo("354000.00");
        assertThat(response.maxRevisions()).isEqualTo(5);

        JsonNode json = objectMapper.readTree(response.priceBreakdownJson());
        assertThat(json.get("computedBaseBucket").decimalValue()).isEqualByComparingTo("130000.00");
        assertThat(json.get("computedInitialPrice").decimalValue()).isEqualByComparingTo("354000.00");
        assertThat(json.get("selections")).hasSize(6);
        assertThat(json.get("selections").get(3).get("quantity").asInt()).isEqualTo(2);
        assertThat(json.get("selections").get(3).get("computedLineAmount").decimalValue()).isEqualByComparingTo("70000.00");
        assertThat(json.get("selections").get(5).get("textValue").asText()).isEqualTo("#00FFCC / #FF00AA");
    }

    @Test
    @DisplayName("CMS-03 UTCID04 [N]: Multi-level dependency chain up to depth 5 resolves cleanly")
    void submitCommission_multiLevelDependencyUpToDepth5_succeeds() {
        CommissionPriceConfigEntity root = matrixSizeConfig; // L0
        CommissionPriceConfigEntity l1 = CommissionPriceConfigEntity.builder()
                .configId(11L).ruleKey("L1").name("L1").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isActive(true).dependsOnOptionId(20L).options(new ArrayList<>()).build();
        CommissionPriceOptionEntity o1 = CommissionPriceOptionEntity.builder()
                .optionId(101L).config(l1).optionCode("O1").optionLabel("O1").basePrice(BigDecimal.ZERO).isActive(true).build();
        l1.getOptions().add(o1);

        CommissionPriceConfigEntity l2 = CommissionPriceConfigEntity.builder()
                .configId(12L).ruleKey("L2").name("L2").selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isActive(true).dependsOnOptionId(101L).isRequired(true).options(new ArrayList<>()).build();
        CommissionPriceOptionEntity o2 = CommissionPriceOptionEntity.builder()
                .optionId(102L).config(l2).optionCode("O2").optionLabel("O2").basePrice(new BigDecimal("10000.00")).isActive(true).build();
        l2.getOptions().add(o2);

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, root, frameRangeConfig, l1, l2));
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));
        stubSaveCommission(504L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Nested", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null),
                        new SelectedOptionItem(11L, List.of(101L), null, null),
                        new SelectedOptionItem(12L, List.of(102L), null, null)
                ),
                null,
                List.of()
        );

        CustomerCommissionDetailResponse response = useCase.execute(42L, request);
        assertThat(response.initialPrice()).isEqualByComparingTo("110000.00");
    }

    @Test
    @DisplayName("CMS-03 UTCID05 [N]: Reference attachments are validated and persisted in order; no staff falls back to null")
    void submitCommission_attachmentsAndNoStaffFallback_succeeds() {
        mockDefaultRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(null);
        stubSaveCommission(505L);

        when(attachmentRepository.save(any(CommissionAttachmentEntity.class)))
                .thenAnswer(inv -> {
                    CommissionAttachmentEntity att = inv.getArgument(0);
                    att.setAttachmentId(700L + att.getDisplayOrder());
                    return att;
                });

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Pixel Tree", "Bonsai", "BASIC",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null)
                ),
                null,
                List.of(
                        new CommissionAttachmentItemRequest("https://cdn.lumintra.com/commissions/ref1.png", "Palette 1"),
                        new CommissionAttachmentItemRequest("https://cdn.lumintra.com/commissions/ref2.png", "Palette 2")
                )
        );

        CustomerCommissionDetailResponse response = useCase.execute(42L, request);

        assertThat(response.attachments()).hasSize(2);
        assertThat(response.attachments().get(0).displayOrder()).isEqualTo(0);
        assertThat(response.attachments().get(1).displayOrder()).isEqualTo(1);
        verify(mediaUrlValidator).validateMediaUrl("https://cdn.lumintra.com/commissions/ref1.png");
        verify(mediaUrlValidator).validateMediaUrl("https://cdn.lumintra.com/commissions/ref2.png");

        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedStaffId()).isNull();
    }

    @Test
    @DisplayName("CMS-03 UTCID06 [B]: QUANTITY rule accepts minQuantity=0 with quantity=0 and contributes 0.00")
    void submitCommission_quantityZeroWithinBounds_contributesZero() {
        CommissionPriceConfigEntity variantConfig = CommissionPriceConfigEntity.builder()
                .configId(4L).ruleKey("VARIANTS").name("Variants")
                .selectionType(SelectionType.QUANTITY).uiType(UiType.NUMBER)
                .minQuantity(0).maxQuantity(5).isRequired(true).isActive(true)
                .options(new ArrayList<>()).build();
        CommissionPriceOptionEntity variantOpt = CommissionPriceOptionEntity.builder()
                .optionId(40L).config(variantConfig).optionCode("UNIT").optionLabel("Unit")
                .modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("25000.00")).isActive(true).build();
        variantConfig.getOptions().add(variantOpt);

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, variantConfig));
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));
        stubSaveCommission(506L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Zero Quantity", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null),
                        new SelectedOptionItem(4L, List.of(), 0, null)
                ),
                null,
                List.of()
        );

        CustomerCommissionDetailResponse response = useCase.execute(42L, request);
        assertThat(response.initialPrice()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("CMS-03 UTCID07 [A]: Missing or inactive tier throws CommissionTierNotFoundException")
    void submitCommission_tierNotFound_throwsException() {
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("UNKNOWN")).thenReturn(Optional.empty());

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Test", "Desc", "UNKNOWN",
                List.of(new SelectedOptionItem(1L, List.of(10L), null, null)),
                null,
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(42L, request))
                .isInstanceOf(CommissionTierNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID08 [A]: Preset selection mismatch or missing trigger option throws PresetSelectionMismatchException")
    void submitCommission_presetMismatchAndTriggerMismatch_throwException() {
        mockDefaultRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));

        // Wrong bundled MATRIX_SIZE (32x32 instead of 16x16)
        SubmitCommissionRequest wrongBundle = new SubmitCommissionRequest(
                "Wrong Bundle", "Desc", "BASIC",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(21L), null, null)
                ),
                null,
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, wrongBundle))
                .isInstanceOf(PresetSelectionMismatchException.class)
                .hasMessageContaining("BASIC");

        // Missing triggerOption
        basicTier.setTriggerOptions(Set.of(size32Option));
        SubmitCommissionRequest missingTrigger = new SubmitCommissionRequest(
                "Missing Trigger", "Desc", "BASIC",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null)
                ),
                null,
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, missingTrigger))
                .isInstanceOf(PresetSelectionMismatchException.class)
                .hasMessageContaining("trigger");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID09 [A]: Eligible required child rule missing or ineligible child rule selected throws InvalidCommissionRuleSelectionException")
    void submitCommission_dependencyAndRequiredGuards_throwException() {
        mockDefaultRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));

        // Eligible required child (FRAME_RANGE, isRequired=true) missing when ANIMATED_GIF selected
        SubmitCommissionRequest missingRequiredChild = new SubmitCommissionRequest(
                "Missing Child", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(11L), null, null),
                        new SelectedOptionItem(2L, List.of(21L), null, null)
                ),
                null,
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, missingRequiredChild))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("Required rule 'FRAME_RANGE'");

        // Ineligible child selected when STATIC_IMAGE selected
        SubmitCommissionRequest ineligibleChild = new SubmitCommissionRequest(
                "Ineligible Child", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(21L), null, null),
                        new SelectedOptionItem(3L, List.of(30L), null, null)
                ),
                null,
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, ineligibleChild))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("Rule 'FRAME_RANGE' is not eligible");
    }

    @Test
    @DisplayName("CMS-03 UTCID10 [A]: QUANTITY out of bounds, targetMatrixDimensions mismatch, and untrusted URL throw appropriate exceptions")
    void submitCommission_quantityBoundsAndMatrixMismatchAndBadUrl_throwExceptions() {
        CommissionPriceConfigEntity variantConfig = CommissionPriceConfigEntity.builder()
                .configId(4L).ruleKey("VARIANTS").name("Variants")
                .selectionType(SelectionType.QUANTITY).uiType(UiType.NUMBER)
                .minQuantity(1).maxQuantity(3).isActive(true)
                .options(new ArrayList<>()).build();
        CommissionPriceOptionEntity variantOpt = CommissionPriceOptionEntity.builder()
                .optionId(40L).config(variantConfig).optionCode("UNIT").optionLabel("Unit")
                .modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("25000.00")).isActive(true).build();
        variantConfig.getOptions().add(variantOpt);

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, variantConfig));
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));

        SubmitCommissionRequest outOfBounds = new SubmitCommissionRequest(
                "Bad Qty", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null),
                        new SelectedOptionItem(4L, List.of(), 5, null)
                ),
                null,
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, outOfBounds))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("between 1 and 3");

        SubmitCommissionRequest matrixMismatch = new SubmitCommissionRequest(
                "Bad Matrix", "Desc", "CUSTOM",
                List.of(
                        new SelectedOptionItem(1L, List.of(10L), null, null),
                        new SelectedOptionItem(2L, List.of(20L), null, null)
                ),
                "64x64",
                List.of()
        );
        assertThatThrownBy(() -> useCase.execute(42L, matrixMismatch))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("targetMatrixDimensions must match selected MATRIX_SIZE: 16x16");

        doThrow(new InvalidMediaUrlException("Invalid domain"))
                .when(mediaUrlValidator).validateMediaUrl("https://evil.com/x.png");
        SubmitCommissionRequest badUrl = new SubmitCommissionRequest(
                "Bad Url", "Desc", "CUSTOM",
                List.of(),
                null,
                List.of(new CommissionAttachmentItemRequest("https://evil.com/x.png", "Bad"))
        );
        assertThatThrownBy(() -> useCase.execute(42L, badUrl))
                .isInstanceOf(InvalidMediaUrlException.class);
    }
}
