package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.CommissionAttachmentItemRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.SelectedOptionItem;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.Step1SelectionItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.application.service.StaffAutoAssignmentService;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    private CommissionRequestMapper commissionRequestMapper;
    private ObjectMapper objectMapper;
    private SubmitCommissionUseCase useCase;

    // Fixture entities
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
        commissionRequestMapper = new CommissionRequestMapper();
        objectMapper = new ObjectMapper();
        useCase = new SubmitCommissionUseCase(
                tierRepository,
                configRepository,
                commissionRequestRepository,
                attachmentRepository,
                staffAutoAssignmentService,
                mediaUrlValidator,
                commissionRequestMapper,
                objectMapper
        );

        // Build Format Type rule
        formatTypeConfig = CommissionPriceConfigEntity.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .name("Định dạng hiển thị")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
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
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        formatTypeConfig.getOptions().add(staticImageOption);

        animatedGifOption = CommissionPriceOptionEntity.builder()
                .optionId(11L)
                .config(formatTypeConfig)
                .optionCode("ANIMATED_GIF")
                .optionLabel("Ảnh động (GIF)")
                .basePrice(new BigDecimal("50000.00"))
                .isActive(true)
                .build();
        formatTypeConfig.getOptions().add(animatedGifOption);

        // Build Matrix Size rule
        matrixSizeConfig = CommissionPriceConfigEntity.builder()
                .configId(2L)
                .ruleKey("MATRIX_SIZE")
                .name("Kích thước ma trận")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
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
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        matrixSizeConfig.getOptions().add(size16Option);

        size32Option = CommissionPriceOptionEntity.builder()
                .optionId(21L)
                .config(matrixSizeConfig)
                .optionCode("32x32")
                .optionLabel("32 x 32 Pixels")
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .build();
        matrixSizeConfig.getOptions().add(size32Option);

        // Build Frame Range rule (conditional on animatedGifOption 11L)
        frameRangeConfig = CommissionPriceConfigEntity.builder()
                .configId(3L)
                .ruleKey("FRAME_RANGE")
                .name("Số khung hình")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(false)
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
                .basePrice(new BigDecimal("0.00"))
                .isActive(true)
                .build();
        frameRangeConfig.getOptions().add(frame1To5Option);

        frame6To15Option = CommissionPriceOptionEntity.builder()
                .optionId(31L)
                .config(frameRangeConfig)
                .optionCode("6-15")
                .optionLabel("6-15 Khung hình")
                .basePrice(new BigDecimal("30000.00"))
                .isActive(true)
                .build();
        frameRangeConfig.getOptions().add(frame6To15Option);

        // Build Tiers
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
                .build();

        advancedTier = CommissionPricingTierEntity.builder()
                .tierId(2L)
                .tierCode("ADVANCED")
                .name("Gói Nâng Cao")
                .basePrice(new BigDecimal("149000.00"))
                .discountPercent(new BigDecimal("17.22"))
                .maxRevisions(3)
                .isCustom(false)
                .isActive(true)
                .includedOptions(new HashSet<>(Set.of(animatedGifOption, size32Option, frame1To5Option)))
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
                .build();
    }

    private void mockActiveRules() {
        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig));
    }

    @Test
    @DisplayName("CMS-03 UTCID01 [N]: Submit commission with ADVANCED preset succeeds with 201 detail response")
    void submitCommission_presetAdvanced_success_returnsCreated() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("ADVANCED")).thenReturn(Optional.of(advancedTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(101L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Cyberpunk Shiba",
                "Shiba in neon rain",
                "ADVANCED",
                List.of(
                        new Step1SelectionItem(1L, 11L), // ANIMATED_GIF
                        new Step1SelectionItem(2L, 21L), // 32x32
                        new Step1SelectionItem(3L, 30L)  // 1-5 frames
                ),
                List.of(),
                "32x32",
                List.of()
        );

        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(501L);
                    return entity;
                });

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response).isNotNull();
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
        assertThat(saved.getAssignedStaffId()).isEqualTo(101L);
        assertThat(saved.getPriceBreakdownJson()).contains("ADVANCED");
    }

    @Test
    @DisplayName("CMS-03 UTCID02 [N]: Submit commission with BASIC preset auto-derives targetMatrixDimensions")
    void submitCommission_presetBasic_success_autoDerivesTargetMatrixDimensions() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(102L);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Pixel Apple",
                "Red pixel apple on black background",
                "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L), // STATIC_IMAGE
                        new Step1SelectionItem(2L, 20L)  // 16x16
                ),
                List.of(),
                null, // Omitted targetMatrixDimensions
                List.of()
        );

        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(502L);
                    return entity;
                });

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(502L);
        assertThat(response.targetMatrixDimensions()).isEqualTo("16x16");
        assertThat(response.initialPrice()).isEqualByComparingTo("79000.00");
    }

    @Test
    @DisplayName("CMS-03 UTCID03 [N]: Submit commission with CUSTOM tier calculates full price recursively")
    void submitCommission_customTier_calculatesCorrectPrice() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(103L);

        // Custom selection: ANIMATED_GIF (50k) + 32x32 (30k) + Frame 6-15 (30k)
        // Base tier: 100k
        // Expected initialPrice: 100k + 50k + 30k + 30k = 210,000.00
        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Custom Space Ship",
                "Thruster animation on 32x32 matrix",
                "CUSTOM",
                List.of(
                        new Step1SelectionItem(1L, 11L), // ANIMATED_GIF (+50k)
                        new Step1SelectionItem(2L, 21L), // 32x32 (+30k)
                        new Step1SelectionItem(3L, 31L)  // 6-15 frames (+30k)
                ),
                List.of(),
                "32x32",
                List.of()
        );

        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(503L);
                    return entity;
                });

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response).isNotNull();
        assertThat(response.initialPrice()).isEqualByComparingTo("210000.00");
        assertThat(response.totalPrice()).isEqualByComparingTo("210000.00");
        assertThat(response.maxRevisions()).isEqualTo(5);
    }

    @Test
    @DisplayName("CMS-03 UTCID04 [N]: Submit commission with reference attachments persists each attachment")
    void submitCommission_withAttachments_persistsAttachments() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(101L);

        List<CommissionAttachmentItemRequest> attachments = List.of(
                new CommissionAttachmentItemRequest("https://cdn.lumintra.com/commissions/ref1.png", "Palette 1"),
                new CommissionAttachmentItemRequest("https://cdn.lumintra.com/commissions/ref2.png", "Palette 2")
        );

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Pixel Tree",
                "Bonsai pixel tree",
                "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L),
                        new Step1SelectionItem(2L, 20L)
                ),
                List.of(),
                "16x16",
                attachments
        );

        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(504L);
                    return entity;
                });

        when(attachmentRepository.save(any(CommissionAttachmentEntity.class)))
                .thenAnswer(inv -> {
                    CommissionAttachmentEntity att = inv.getArgument(0);
                    att.setAttachmentId(700L + att.getDisplayOrder());
                    return att;
                });

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response).isNotNull();
        assertThat(response.attachments()).hasSize(2);
        assertThat(response.attachments().get(0).displayOrder()).isEqualTo(0);
        assertThat(response.attachments().get(1).displayOrder()).isEqualTo(1);
        verify(mediaUrlValidator).validateMediaUrl("https://cdn.lumintra.com/commissions/ref1.png");
        verify(mediaUrlValidator).validateMediaUrl("https://cdn.lumintra.com/commissions/ref2.png");
    }

    @Test
    @DisplayName("CMS-03 UTCID05 [B]: Submit commission when no staff available sets assignedStaffId to null")
    void submitCommission_noStaffAvailable_setsAssignedStaffNull() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));
        when(staffAutoAssignmentService.assignNextAvailableStaff()).thenReturn(null);

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Pixel Mountain",
                "Mountain sunset",
                "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L),
                        new Step1SelectionItem(2L, 20L)
                ),
                List.of(),
                "16x16",
                List.of()
        );

        when(commissionRequestRepository.save(any(CommissionRequestEntity.class)))
                .thenAnswer(inv -> {
                    CommissionRequestEntity entity = inv.getArgument(0);
                    entity.setCommissionId(505L);
                    return entity;
                });

        CustomerCommissionDetailResponse response = useCase.execute(customerId, request);

        assertThat(response).isNotNull();
        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedStaffId()).isNull();
    }

    @Test
    @DisplayName("CMS-03 UTCID06 [A]: Inactive or missing tier throws CommissionTierNotFoundException")
    void submitCommission_tierNotFound_throwsCommissionTierNotFoundException() {
        Long customerId = 42L;
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("UNKNOWN")).thenReturn(Optional.empty());

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Test", "Desc", "UNKNOWN",
                List.of(new Step1SelectionItem(1L, 10L)), List.of(), null, List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(CommissionTierNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID07 [A]: Preset selection mismatch throws PresetSelectionMismatchException")
    void submitCommission_presetMismatch_throwsPresetSelectionMismatchException() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));

        // User picked BASIC tier but submitted 32x32 instead of 16x16
        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Mismatch", "Desc", "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L), // STATIC_IMAGE
                        new Step1SelectionItem(2L, 21L)  // 32x32 (BASIC requires 20L: 16x16)
                ),
                List.of(),
                "32x32",
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(PresetSelectionMismatchException.class)
                .hasMessageContaining("BASIC");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID08 [A]: ANIMATED_GIF missing FRAME_RANGE throws InvalidCommissionRuleSelectionException")
    void submitCommission_animatedGifMissingFrameRange_throwsInvalidCommissionRuleSelectionException() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "GIF Without Frames", "Desc", "CUSTOM",
                List.of(
                        new Step1SelectionItem(1L, 11L), // ANIMATED_GIF
                        new Step1SelectionItem(2L, 21L)  // 32x32 (Missing FRAME_RANGE)
                ),
                List.of(),
                "32x32",
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("FRAME_RANGE selection is required when FORMAT_TYPE is ANIMATED_GIF");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID09 [A]: STATIC_IMAGE containing FRAME_RANGE throws InvalidCommissionRuleSelectionException")
    void submitCommission_staticImageWithFrameRange_throwsInvalidCommissionRuleSelectionException() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Static With Frames", "Desc", "CUSTOM",
                List.of(
                        new Step1SelectionItem(1L, 10L), // STATIC_IMAGE
                        new Step1SelectionItem(2L, 21L), // 32x32
                        new Step1SelectionItem(3L, 30L)  // Prohibited FRAME_RANGE
                ),
                List.of(),
                "32x32",
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("FRAME_RANGE cannot be selected when FORMAT_TYPE is STATIC_IMAGE");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID10 [A]: Target matrix dimensions mismatch throws InvalidCommissionRuleSelectionException")
    void submitCommission_targetMatrixMismatch_throwsInvalidCommissionRuleSelectionException() {
        Long customerId = 42L;
        mockActiveRules();
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("BASIC")).thenReturn(Optional.of(basicTier));

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Matrix Mismatch", "Desc", "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L), // STATIC_IMAGE
                        new Step1SelectionItem(2L, 20L)  // 16x16
                ),
                List.of(),
                "64x64", // Sent 64x64 while selected is 16x16
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("targetMatrixDimensions must match selected MATRIX_SIZE: 16x16");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID11 [A]: Untrusted attachment URL throws InvalidMediaUrlException")
    void submitCommission_untrustedMediaUrl_throwsInvalidMediaUrlException() {
        Long customerId = 42L;
        doThrow(new InvalidMediaUrlException("Invalid domain"))
                .when(mediaUrlValidator).validateMediaUrl("https://evil.com/malicious.png");

        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Malicious URL", "Desc", "BASIC",
                List.of(
                        new Step1SelectionItem(1L, 10L),
                        new Step1SelectionItem(2L, 20L)
                ),
                List.of(),
                "16x16",
                List.of(new CommissionAttachmentItemRequest("https://evil.com/malicious.png", "Evil"))
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(InvalidMediaUrlException.class);

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-03 UTCID12 [A]: Custom tier with ineligible dependent rule selection throws InvalidCommissionRuleSelectionException")
    void submitCommission_customDependentRuleMissingPrerequisite_throwsInvalidCommissionRuleSelectionException() {
        Long customerId = 42L;

        // Create an active Step-2 config dependent on option 999 (not selected)
        CommissionPriceConfigEntity dependentConfig = CommissionPriceConfigEntity.builder()
                .configId(4L)
                .ruleKey("ANIMATION_SPEED")
                .name("Tốc độ chuyển động")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(false)
                .isSystem(false)
                .isActive(true)
                .dependsOnOptionId(11L) // depends on ANIMATED_GIF (11L)
                .options(new ArrayList<>())
                .build();

        CommissionPriceOptionEntity speedFast = CommissionPriceOptionEntity.builder()
                .optionId(40L)
                .config(dependentConfig)
                .optionCode("FAST")
                .optionLabel("Nhanh")
                .basePrice(new BigDecimal("10000.00"))
                .isActive(true)
                .build();
        dependentConfig.getOptions().add(speedFast);

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig, dependentConfig));
        when(tierRepository.findActiveWithIncludedOptionsByTierCode("CUSTOM")).thenReturn(Optional.of(customTier));

        // User picked STATIC_IMAGE (10L), so ANIMATED_GIF (11L) is NOT selected.
        // But user provided selection for ANIMATION_SPEED (which depends on 11L)
        SubmitCommissionRequest request = new SubmitCommissionRequest(
                "Dependent Rule Ineligible", "Desc", "CUSTOM",
                List.of(
                        new Step1SelectionItem(1L, 10L), // STATIC_IMAGE
                        new Step1SelectionItem(2L, 20L)  // 16x16
                ),
                List.of(
                        new SelectedOptionItem(4L, List.of(40L), null)
                ),
                "16x16",
                List.of()
        );

        assertThatThrownBy(() -> useCase.execute(customerId, request))
                .isInstanceOf(InvalidCommissionRuleSelectionException.class)
                .hasMessageContaining("Rule 'ANIMATION_SPEED' is not eligible");

        verify(commissionRequestRepository, never()).save(any());
    }
}
