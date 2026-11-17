package com.lumintra.backend.commission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.bootstrap.CommissionFlowDataSeeder;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionFlowDataSeederTest {

    @Mock
    private CommissionPriceConfigRepository configRepository;

    @Mock
    private CommissionPricingTierRepository tierRepository;

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionAttachmentRepository attachmentRepository;

    @Mock
    private CommissionSubmissionRepository submissionRepository;

    @Mock
    private CommissionMessageRepository messageRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private EmployeeLookupService employeeLookupService;

    @Mock
    private PaymentSessionPort paymentSessionPort;

    private ObjectMapper objectMapper;
    private CommissionFlowDataSeeder seeder;

    private CommissionPriceConfigEntity formatTypeConfig;
    private CommissionPriceConfigEntity matrixSizeConfig;
    private CommissionPriceConfigEntity frameRangeConfig;
    private CommissionPricingTierEntity customTier;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        seeder = new CommissionFlowDataSeeder(
                configRepository,
                tierRepository,
                commissionRequestRepository,
                attachmentRepository,
                submissionRepository,
                messageRepository,
                new PricingInvariantValidator(),
                customerLookupService,
                employeeLookupService,
                paymentSessionPort,
                objectMapper
        );
        ReflectionTestUtils.setField(seeder, "customerEmail", "customer@lumintra.com");
        ReflectionTestUtils.setField(seeder, "staffEmail", "staff@lumintra.com");
        ReflectionTestUtils.setField(seeder, "designerEmail", "designer@lumintra.com");

        formatTypeConfig = CommissionPriceConfigEntity.builder()
                .configId(1L).ruleKey("FORMAT_TYPE").name("Format")
                .selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isSystem(true).isRequired(true).isActive(true).options(new ArrayList<>()).build();
        formatTypeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(1L).optionCode("STATIC_IMAGE").optionLabel("Static")
                .modifierType(ModifierType.FLAT_ADD).basePrice(BigDecimal.ZERO).isDefault(true).isActive(true).build());
        CommissionPriceOptionEntity gifOpt = CommissionPriceOptionEntity.builder()
                .optionId(2L).optionCode("ANIMATED_GIF").optionLabel("Animated GIF")
                .modifierType(ModifierType.BASE_PERCENT).basePrice(new BigDecimal("50.00")).isActive(true).build();
        formatTypeConfig.addOption(gifOpt);

        matrixSizeConfig = CommissionPriceConfigEntity.builder()
                .configId(2L).ruleKey("MATRIX_SIZE").name("Matrix Size")
                .selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isBasePrice(true).isSystem(true).isRequired(true).isActive(true).options(new ArrayList<>()).build();
        matrixSizeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(3L).optionCode("32x32").optionLabel("32x32").modifierType(ModifierType.FLAT_ADD).basePrice(BigDecimal.ZERO).isDefault(true).isActive(true).build());
        matrixSizeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(4L).optionCode("32x64").optionLabel("32x64").modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("30000.00")).isActive(true).build());
        matrixSizeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(5L).optionCode("64x64").optionLabel("64x64").modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("60000.00")).isActive(true).build());

        frameRangeConfig = CommissionPriceConfigEntity.builder()
                .configId(3L).ruleKey("FRAME_RANGE").name("Frames")
                .selectionType(SelectionType.SINGLE_SELECT).uiType(UiType.RADIO)
                .isSystem(true).isRequired(true).isActive(true).dependsOnOptionId(2L).options(new ArrayList<>()).build();
        frameRangeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(6L).optionCode("1-5").optionLabel("1-5").modifierType(ModifierType.FLAT_ADD).basePrice(BigDecimal.ZERO).isDefault(true).isActive(true).build());
        frameRangeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(7L).optionCode("6-15").optionLabel("6-15").modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("30000.00")).isActive(true).build());
        frameRangeConfig.addOption(CommissionPriceOptionEntity.builder()
                .optionId(8L).optionCode("16-30").optionLabel("16-30").modifierType(ModifierType.FLAT_ADD).basePrice(new BigDecimal("60000.00")).isActive(true).build());

        customTier = CommissionPricingTierEntity.builder()
                .tierId(1L).tierCode("CUSTOM").name("Thiết Kế Tùy Chỉnh")
                .basePrice(new BigDecimal("100000.00")).discountPercent(BigDecimal.ZERO)
                .maxRevisions(5).isCustom(true).isActive(true)
                .includedOptions(new HashSet<>()).triggerOptions(new HashSet<>()).build();
    }

    @Test
    @DisplayName("Flow 3 Seeder UTCID01 [N]: Seeds 4 custom pricing rules and 10 end-to-end commission tickets across lifecycle states")
    void run_seedsCustomRulesAndAllFlow3Scenarios() throws Exception {
        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig));
        when(configRepository.existsByRuleKeyIgnoreCase(anyString())).thenReturn(false);

        AtomicLong nextConfigId = new AtomicLong(10L);
        AtomicLong nextOptionId = new AtomicLong(100L);
        when(configRepository.save(any(CommissionPriceConfigEntity.class))).thenAnswer(inv -> {
            CommissionPriceConfigEntity cfg = inv.getArgument(0);
            if (cfg.getConfigId() == null) {
                cfg.setConfigId(nextConfigId.getAndIncrement());
            }
            if (cfg.getOptions() != null) {
                for (CommissionPriceOptionEntity opt : cfg.getOptions()) {
                    if (opt.getOptionId() == null) {
                        opt.setOptionId(nextOptionId.getAndIncrement());
                    }
                }
            }
            return cfg;
        });

        when(commissionRequestRepository.count()).thenReturn(0L);
        when(tierRepository.findByIsCustomTrueAndIsActiveTrue()).thenReturn(Optional.of(customTier));
        when(customerLookupService.findProfileByEmail("customer@lumintra.com"))
                .thenReturn(Optional.of(new CustomerProfileSnapshot(42L, "Test Customer", "0901234567", "customer@lumintra.com")));
        when(employeeLookupService.findActiveStaffEmployeeIds()).thenReturn(List.of(12L));
        when(employeeLookupService.findProfilesByEmployeeIds(List.of(12L)))
                .thenReturn(Map.of(12L, new EmployeeProfileSnapshot(12L, "Test Staff", "staff@lumintra.com")));
        when(employeeLookupService.findActiveDesigners())
                .thenReturn(List.of(new EmployeeProfileSnapshot(15L, "Test Designer", "designer@lumintra.com")));

        AtomicLong nextCommissionId = new AtomicLong(200L);
        List<CommissionRequestEntity> createdCommissions = new ArrayList<>();
        when(commissionRequestRepository.saveAndFlush(any(CommissionRequestEntity.class))).thenAnswer(inv -> {
            CommissionRequestEntity req = inv.getArgument(0);
            if (req.getCommissionId() == null) {
                req.setCommissionId(nextCommissionId.getAndIncrement());
                createdCommissions.add(req);
            }
            return req;
        });
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicLong nextSubmissionId = new AtomicLong(300L);
        when(submissionRepository.saveAndFlush(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> {
            CommissionSubmissionEntity sub = inv.getArgument(0);
            if (sub.getSubmissionId() == null) {
                sub.setSubmissionId(nextSubmissionId.getAndIncrement());
            }
            return sub;
        });

        AtomicLong nextMessageId = new AtomicLong(400L);
        when(messageRepository.saveAndFlush(any(CommissionMessageEntity.class))).thenAnswer(inv -> {
            CommissionMessageEntity msg = inv.getArgument(0);
            if (msg.getMessageId() == null) {
                msg.setMessageId(nextMessageId.getAndIncrement());
            }
            return msg;
        });

        when(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class))).thenAnswer(inv -> {
            CreatePaymentSessionCommand cmd = inv.getArgument(0);
            return PaymentSessionResponse.builder()
                    .paymentCode("LMTCMS" + cmd.targetId() + "SEED9999")
                    .targetType(cmd.targetType())
                    .targetId(cmd.targetId())
                    .amount(cmd.amount())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        });

        seeder.run(new DefaultApplicationArguments());

        // 4 custom pricing rules seeded
        ArgumentCaptor<CommissionPriceConfigEntity> ruleCaptor = ArgumentCaptor.forClass(CommissionPriceConfigEntity.class);
        verify(configRepository, times(4)).save(ruleCaptor.capture());
        assertThat(ruleCaptor.getAllValues())
                .extracting(CommissionPriceConfigEntity::getRuleKey)
                .containsExactly(
                        CommissionFlowDataSeeder.RULE_ADDON_EXTRAS,
                        CommissionFlowDataSeeder.RULE_EXTRA_VARIANTS,
                        CommissionFlowDataSeeder.RULE_PALETTE_NOTES,
                        CommissionFlowDataSeeder.RULE_GIF_EXPORT_PROFILE
                );
        assertThat(ruleCaptor.getAllValues()).allSatisfy(rule -> assertThat(rule.isSystem()).isFalse());
        assertThat(ruleCaptor.getAllValues().get(3).getDependsOnOptionId()).isEqualTo(2L);

        // 10 Flow 3 commission requests seeded across statuses
        assertThat(createdCommissions).hasSize(10);
        assertThat(createdCommissions)
                .extracting(CommissionRequestEntity::getStatus)
                .containsExactly(
                        CommissionStatus.PENDING_REVIEW,
                        CommissionStatus.PENDING_REVIEW,
                        CommissionStatus.PENDING_PAYMENT,
                        CommissionStatus.PAID,
                        CommissionStatus.IN_PROGRESS,
                        CommissionStatus.INTERNAL_REVIEW,
                        CommissionStatus.INTERNAL_REVISION_REQUIRED,
                        CommissionStatus.PREVIEW_DELIVERED,
                        CommissionStatus.COMPLETED,
                        CommissionStatus.CANCELLED
                );

        // Verify computed 4-stage pipeline price & snapshot JSON for PENDING_REVIEW (#1):
        // Base = 100k + 30k (32x64) = 130k; +50% BASE (65k) + 25% BASE (32.5k) + 30k (6-15) + 35k (1 variant) = 292.5k;
        // +20% SUBTOTAL (58.5k) = 351,000.00
        CommissionRequestEntity shiba = createdCommissions.get(0);
        assertThat(shiba.getInitialPrice()).isEqualByComparingTo("351000.00");
        assertThat(shiba.getAssignedStaffId()).isEqualTo(12L);
        JsonNode shibaJson = objectMapper.readTree(shiba.getPriceBreakdownJson());
        assertThat(shibaJson.get("computedBaseBucket").decimalValue()).isEqualByComparingTo("130000.00");
        assertThat(shibaJson.get("computedInitialPrice").decimalValue()).isEqualByComparingTo("351000.00");
        assertThat(shibaJson.get("selections")).hasSize(6);

        // Verify PENDING_PAYMENT has active SePay session wired
        CommissionRequestEntity pendingPayment = createdCommissions.get(2);
        ArgumentCaptor<CreatePaymentSessionCommand> paymentCmdCaptor = ArgumentCaptor.forClass(CreatePaymentSessionCommand.class);
        verify(paymentSessionPort, times(1)).createSession(paymentCmdCaptor.capture());
        assertThat(paymentCmdCaptor.getValue().targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(paymentCmdCaptor.getValue().customerId()).isEqualTo(42L);
        assertThat(pendingPayment.getActivePaymentCode()).isEqualTo("LMTCMS" + pendingPayment.getCommissionId() + "SEED9999");

        // Verify attachments, submissions (v1 pending QA, v1 rejected, v2 approved, v1 accepted), and messages
        verify(attachmentRepository, times(7)).save(any(CommissionAttachmentEntity.class));

        ArgumentCaptor<CommissionSubmissionEntity> subCaptor = ArgumentCaptor.forClass(CommissionSubmissionEntity.class);
        verify(submissionRepository, times(5)).saveAndFlush(subCaptor.capture());
        assertThat(subCaptor.getAllValues())
                .extracting(CommissionSubmissionEntity::getStatus)
                .containsExactly(
                        CommissionSubmissionStatus.PENDING_STAFF_REVIEW,
                        CommissionSubmissionStatus.STAFF_REJECTED,
                        CommissionSubmissionStatus.STAFF_REJECTED,
                        CommissionSubmissionStatus.STAFF_APPROVED,
                        CommissionSubmissionStatus.CUSTOMER_ACCEPTED
                );

        CommissionRequestEntity completed = createdCommissions.get(8);
        assertThat(completed.getApprovedSubmissionId()).isEqualTo(subCaptor.getAllValues().get(4).getSubmissionId());

        CommissionRequestEntity cancelled = createdCommissions.get(9);
        assertThat(cancelled.getRejectionReason()).isNotBlank();

        ArgumentCaptor<CommissionMessageEntity> msgCaptor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(messageRepository, atLeast(10)).saveAndFlush(msgCaptor.capture());
        assertThat(msgCaptor.getAllValues())
                .anyMatch(m -> m.getMessageType() == CommissionMessageType.DELIVERABLE_CARD
                        && m.getSenderRole() == CommissionMessageSenderRole.STAFF
                        && m.getMetadataJson() != null
                        && m.getMetadataJson().contains("submissionId"));
    }

    @Test
    @DisplayName("Flow 3 Seeder UTCID02 [N]: Subsequent run skips existing custom rules and commission tickets idempotently")
    void run_skipsWhenAlreadyPopulated() {
        CommissionPriceConfigEntity existingAddon = CommissionPriceConfigEntity.builder()
                .configId(10L).ruleKey(CommissionFlowDataSeeder.RULE_ADDON_EXTRAS).options(List.of()).build();
        CommissionPriceConfigEntity existingVariants = CommissionPriceConfigEntity.builder()
                .configId(11L).ruleKey(CommissionFlowDataSeeder.RULE_EXTRA_VARIANTS).options(List.of()).build();
        CommissionPriceConfigEntity existingNotes = CommissionPriceConfigEntity.builder()
                .configId(12L).ruleKey(CommissionFlowDataSeeder.RULE_PALETTE_NOTES).options(List.of()).build();
        CommissionPriceConfigEntity existingGif = CommissionPriceConfigEntity.builder()
                .configId(13L).ruleKey(CommissionFlowDataSeeder.RULE_GIF_EXPORT_PROFILE).options(List.of()).build();

        when(configRepository.findActiveWithActiveOptions())
                .thenReturn(List.of(formatTypeConfig, matrixSizeConfig, frameRangeConfig,
                        existingAddon, existingVariants, existingNotes, existingGif));
        when(tierRepository.findByIsCustomTrueAndIsActiveTrue()).thenReturn(Optional.of(customTier));
        when(commissionRequestRepository.count()).thenReturn(10L);

        seeder.run(new DefaultApplicationArguments());

        verify(configRepository, never()).save(any());
        verify(commissionRequestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(paymentSessionPort, submissionRepository, attachmentRepository, messageRepository);
    }

    @Test
    @DisplayName("Flow 3 Seeder UTCID03 [B]: Skips gracefully when base pricing is missing")
    void run_skipsGracefullyWhenPrerequisitesMissing() {
        when(configRepository.findActiveWithActiveOptions()).thenReturn(List.of());
        when(tierRepository.findByIsCustomTrueAndIsActiveTrue()).thenReturn(Optional.empty());
        when(tierRepository.findByTierCode("CUSTOM")).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        verify(configRepository, never()).save(any());
        verify(commissionRequestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(paymentSessionPort, customerLookupService);
    }
}
