package com.lumintra.backend.commission.infrastructure.bootstrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.application.ApproveCommissionRequestUseCase;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.OptionSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.RuleSelectionSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator.ModifierCalculationResult;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator.ModifierLineInput;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Seeds Flow 3 (Commission) custom pricing rules and end-to-end sample
 * commission tickets
 * across every testable lifecycle status, payment session, attachment, draft
 * review, deliverable,
 * and chat room state. Runs after IAM ({@code @Order(1)}) and base pricing
 * ({@code @Order(5)}).
 */
@Component
@Order(6)
@RequiredArgsConstructor
public class CommissionFlowDataSeeder implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(CommissionFlowDataSeeder.class);

        public static final String RULE_ADDON_EXTRAS = "ADDON_EXTRAS";
        public static final String RULE_EXTRA_VARIANTS = "EXTRA_VARIANTS";
        public static final String RULE_PALETTE_NOTES = "PALETTE_NOTES";
        public static final String RULE_GIF_EXPORT_PROFILE = "GIF_EXPORT_PROFILE";

        private static final String CDN_SEED_PREFIX = "https://cdn.lumintra.com/commissions/seed/";

        private final CommissionPriceConfigRepository configRepository;
        private final CommissionPricingTierRepository tierRepository;
        private final CommissionRequestRepository commissionRequestRepository;
        private final CommissionAttachmentRepository attachmentRepository;
        private final CommissionSubmissionRepository submissionRepository;
        private final CommissionMessageRepository messageRepository;
        private final PricingInvariantValidator invariantValidator;
        private final CustomerLookupService customerLookupService;
        private final EmployeeLookupService employeeLookupService;
        private final PaymentSessionPort paymentSessionPort;
        private final ObjectMapper objectMapper;

        @Value("${app.seed.test-customer-email:}")
        private String customerEmail;

        @Value("${app.seed.test-staff-email:}")
        private String staffEmail;

        @Value("${app.seed.test-designer-email:}")
        private String designerEmail;

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                log.info("Checking database for Flow 3 Commission seed data...");

                Map<String, CommissionPriceConfigEntity> configsByKey = loadActiveConfigsByKey();
                CommissionPricingTierEntity customTier = tierRepository.findByIsCustomTrueAndIsActiveTrue()
                                .or(() -> tierRepository.findByTierCode("CUSTOM"))
                                .orElse(null);

                if (customTier == null || !configsByKey.containsKey("FORMAT_TYPE")
                                || !configsByKey.containsKey("MATRIX_SIZE")) {
                        log.warn("Base Commission pricing configuration is missing. Skipping Flow 3 seeding.");
                        return;
                }

                CommissionPriceOptionEntity animatedGifOption = findOption(configsByKey.get("FORMAT_TYPE"),
                                "ANIMATED_GIF");
                seedCustomPricingRules(configsByKey, animatedGifOption);

                if (commissionRequestRepository.count() > 0) {
                        log.info("Commission requests already exist in database. Skipping Flow 3 sample ticket seeding.");
                        return;
                }

                CustomerProfileSnapshot customer = resolveCustomer();
                if (customer == null || customer.customerId() == null) {
                        log.warn("No customer profile found in database (email={}). Skipping Flow 3 sample ticket seeding.",
                                        customerEmail);
                        return;
                }

                Long staffEmployeeId = resolveStaffEmployeeId();
                Long designerEmployeeId = resolveDesignerEmployeeId();
                if (designerEmployeeId == null) {
                        log.warn("No active DESIGNER employee found; designer-stage commissions will remain unassigned and omit draft submissions.");
                }

                PricingContext pricingContext = new PricingContext(customTier, configsByKey);
                seedCommissionScenarios(customer.customerId(), staffEmployeeId, designerEmployeeId, pricingContext);

                log.info("Flow 3 Commission seed data verification complete [customerId={}] [staffId={}] [designerId={}].",
                                customer.customerId(), staffEmployeeId, designerEmployeeId);
        }

        private Map<String, CommissionPriceConfigEntity> loadActiveConfigsByKey() {
                Map<String, CommissionPriceConfigEntity> byKey = new LinkedHashMap<>();
                List<CommissionPriceConfigEntity> configs = configRepository.findActiveWithActiveOptions();
                if (configs == null) {
                        return byKey;
                }
                for (CommissionPriceConfigEntity config : configs) {
                        if (config != null && config.getRuleKey() != null) {
                                byKey.put(config.getRuleKey().toUpperCase(Locale.ROOT), config);
                        }
                }
                return byKey;
        }

        private void seedCustomPricingRules(
                        Map<String, CommissionPriceConfigEntity> configsByKey,
                        CommissionPriceOptionEntity animatedGifOption) {

                CommissionPriceConfigEntity addonExtras = CommissionPriceConfigEntity.createCustom(
                                RULE_ADDON_EXTRAS,
                                "Tiện ích bổ sung (Add-ons)",
                                "Các tùy chọn nâng cao: hiệu ứng chiều sâu, tệp nguồn thiết kế hoặc bản quyền thương mại.",
                                SelectionType.MULTI_SELECT,
                                UiType.CHECKBOX,
                                false,
                                null,
                                null,
                                false,
                                4,
                                null,
                                List.of(
                                                CommissionPriceOptionEntity.create("PARALLAX_EFFECT",
                                                                "Hiệu ứng chiều sâu Parallax (+25% Base)",
                                                                ModifierType.BASE_PERCENT, new BigDecimal("25.00"),
                                                                false, 1),
                                                CommissionPriceOptionEntity.create("SOURCE_FILE_ASEPRITE",
                                                                "Tệp gốc thiết kế (.aseprite)",
                                                                ModifierType.FLAT_ADD, new BigDecimal("30000.00"),
                                                                false, 2),
                                                CommissionPriceOptionEntity.create("COMMERCIAL_LICENSE",
                                                                "Bản quyền thương mại (+20% Subtotal)",
                                                                ModifierType.SUBTOTAL_PERCENT, new BigDecimal("20.00"),
                                                                false, 3)));
                saveCustomRuleIfMissing(addonExtras, null, configsByKey);

                CommissionPriceConfigEntity extraVariants = CommissionPriceConfigEntity.createCustom(
                                RULE_EXTRA_VARIANTS,
                                "Biến thể màu sắc bổ sung",
                                "Số lượng biến thể màu / thời tiết thêm cho cùng một tác phẩm pixel art.",
                                SelectionType.QUANTITY,
                                UiType.NUMBER,
                                false,
                                0,
                                5,
                                false,
                                5,
                                null,
                                List.of(
                                                CommissionPriceOptionEntity.create("VARIANT_UNIT",
                                                                "Mỗi biến thể bổ sung (+35,000đ)",
                                                                ModifierType.FLAT_ADD, new BigDecimal("35000.00"), true,
                                                                1)));
                saveCustomRuleIfMissing(extraVariants, null, configsByKey);

                CommissionPriceConfigEntity paletteNotes = CommissionPriceConfigEntity.createCustom(
                                RULE_PALETTE_NOTES,
                                "Ghi chú bảng màu & phong cách",
                                "Nhập mã màu hex, tâm trạng hoặc chi tiết sáng tạo cho họa sĩ (không ảnh hưởng giá).",
                                SelectionType.TEXT_INPUT,
                                UiType.TEXT,
                                false,
                                null,
                                null,
                                false,
                                6,
                                null,
                                List.of());
                saveCustomRuleIfMissing(paletteNotes, null, configsByKey);

                if (animatedGifOption != null && animatedGifOption.getOptionId() != null) {
                        CommissionPriceConfigEntity gifProfile = CommissionPriceConfigEntity.createCustom(
                                        RULE_GIF_EXPORT_PROFILE,
                                        "Cấu hình xuất GIF",
                                        "Chọn tốc độ khung hình mục tiêu khi xuất tệp ảnh động GIF cho ma trận LED.",
                                        SelectionType.SINGLE_SELECT,
                                        UiType.DROPDOWN,
                                        false,
                                        null,
                                        null,
                                        false,
                                        7,
                                        animatedGifOption.getOptionId(),
                                        List.of(
                                                        CommissionPriceOptionEntity.create("STANDARD_30FPS",
                                                                        "Standard 30 FPS GIF",
                                                                        ModifierType.FLAT_ADD, new BigDecimal("0.00"),
                                                                        true, 1),
                                                        CommissionPriceOptionEntity.create("HIGH_LOOP_SMOOTH",
                                                                        "Smooth Seamless Loop (+15,000đ)",
                                                                        ModifierType.FLAT_ADD,
                                                                        new BigDecimal("15000.00"), false, 2)));
                        saveCustomRuleIfMissing(gifProfile, animatedGifOption, configsByKey);
                }
        }

        private void saveCustomRuleIfMissing(
                        CommissionPriceConfigEntity candidate,
                        CommissionPriceOptionEntity parentOption,
                        Map<String, CommissionPriceConfigEntity> configsByKey) {
                String key = candidate.getRuleKey().toUpperCase(Locale.ROOT);
                if (configsByKey.containsKey(key)
                                || configRepository.existsByRuleKeyIgnoreCase(candidate.getRuleKey())) {
                        return;
                }
                invariantValidator.validateCustomRuleCreation(candidate);
                if (parentOption != null) {
                        invariantValidator.validateParentOptionDependency(null, parentOption, id -> null);
                }
                CommissionPriceConfigEntity saved = configRepository.save(candidate);
                configsByKey.put(key, saved);
                log.info("Seeded custom commission pricing rule [ruleKey={}] [configId={}]", saved.getRuleKey(),
                                saved.getConfigId());
        }

        private void seedCommissionScenarios(
                        Long customerId,
                        Long staffEmployeeId,
                        Long designerEmployeeId,
                        PricingContext ctx) {
                Instant now = Instant.now();

                // 1. PENDING_REVIEW (Approve candidate) — full multi-primitive + 3-modifier
                // breakdown
                CommissionRequestEntity pendingForApprove = saveCommission(
                                customerId,
                                "[SEED] Cyberpunk Shiba Inu Loop",
                                "Neon cyberpunk Shiba blinking under holographic rain for rear windshield display.",
                                "32x64",
                                CommissionStatus.PENDING_REVIEW,
                                staffEmployeeId,
                                null,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "ANIMATED_GIF"),
                                                ctx.single("MATRIX_SIZE", "32x64"),
                                                ctx.single("FRAME_RANGE", "6-15"),
                                                ctx.multi(RULE_ADDON_EXTRAS,
                                                                List.of("PARALLAX_EFFECT", "COMMERCIAL_LICENSE")),
                                                ctx.quantity(RULE_EXTRA_VARIANTS, 1),
                                                ctx.text(RULE_PALETTE_NOTES,
                                                                "#00FFCC neon cyan, #FF00AA hot magenta")));
                attachReferences(pendingForApprove.getCommissionId(), List.of(
                                new AttachmentSeed(CDN_SEED_PREFIX + "shiba-pose-ref.png",
                                                "Shiba head angle & sunglasses pose"),
                                new AttachmentSeed(CDN_SEED_PREFIX + "cyber-palette.png",
                                                "Neon cyan/magenta mood palette")));

                // 2. PENDING_REVIEW (Reject candidate) — separate ticket so staff can test both
                // /approve and /reject
                CommissionRequestEntity pendingForReject = saveCommission(
                                customerId,
                                "[SEED] Drift RX-7 Rear Emblem",
                                "Pixel art rotary sports car rear silhouette with glowing brake lights.",
                                "64x64",
                                CommissionStatus.PENDING_REVIEW,
                                staffEmployeeId,
                                null,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "STATIC_IMAGE"),
                                                ctx.single("MATRIX_SIZE", "64x64"),
                                                ctx.multi(RULE_ADDON_EXTRAS, List.of("SOURCE_FILE_ASEPRITE"))));
                attachReferences(pendingForReject.getCommissionId(), List.of(
                                new AttachmentSeed(CDN_SEED_PREFIX + "rx7-tail-lights.png",
                                                "Reference tail light bar glow")));

                // 3. PENDING_PAYMENT — creates a real PaymentSessionPort transaction for
                // payment polling & webhook tests
                CommissionRequestEntity pendingPayment = saveCommission(
                                customerId,
                                "[SEED] Midnight Sakura Emblem",
                                "Falling cherry blossom petals over dark midnight mountain silhouette.",
                                "32x32",
                                CommissionStatus.PENDING_PAYMENT,
                                staffEmployeeId,
                                null,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "ANIMATED_GIF"),
                                                ctx.single("MATRIX_SIZE", "32x32"),
                                                ctx.single("FRAME_RANGE", "1-5"),
                                                ctx.single(RULE_GIF_EXPORT_PROFILE, "STANDARD_30FPS")));
                attachReferences(pendingPayment.getCommissionId(), List.of(
                                new AttachmentSeed(CDN_SEED_PREFIX + "sakura-mood.png",
                                                "Sakura pink-on-indigo color mood")));
                PaymentSessionResponse paymentSession = paymentSessionPort
                                .createSession(new CreatePaymentSessionCommand(
                                                PaymentTargetType.COMMISSION_INITIAL,
                                                pendingPayment.getCommissionId(),
                                                customerId,
                                                pendingPayment.getInitialPrice(),
                                                ApproveCommissionRequestUseCase.DEFAULT_QUOTE_EXPIRATION));
                pendingPayment.setActivePaymentCode(paymentSession.paymentCode());
                pendingPayment.setQuoteExpiresAt(paymentSession.expiresAt() != null
                                ? paymentSession.expiresAt()
                                : now.plus(ApproveCommissionRequestUseCase.DEFAULT_QUOTE_EXPIRATION));
                commissionRequestRepository.save(pendingPayment);

                // 4. PAID — ready for POST /api/management/commissions/{id}/assign + active
                // chat room
                CommissionRequestEntity paidUnassigned = saveCommission(
                                customerId,
                                "[SEED] Synthwave Skyline Pulse",
                                "Retro 80s grid horizon with pulsing sunset stripes.",
                                "32x64",
                                CommissionStatus.PAID,
                                staffEmployeeId,
                                null,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "ANIMATED_GIF"),
                                                ctx.single("MATRIX_SIZE", "32x64"),
                                                ctx.single("FRAME_RANGE", "6-15"),
                                                ctx.multi(RULE_ADDON_EXTRAS, List.of("COMMERCIAL_LICENSE"))));
                addSystemUnlockMessage(paidUnassigned);
                addChatMessage(paidUnassigned.getCommissionId(), customerId, CommissionMessageSenderRole.CUSTOMER,
                                CommissionMessageType.TEXT,
                                "Deposit paid! Let me know when a pixel artist picks this up.", null, null);

                // 5. IN_PROGRESS — active designer assignment + rich Customer/Staff/Image chat
                // history
                CommissionRequestEntity inProgress = saveCommission(
                                customerId,
                                "[SEED] Neon Oni Mask Flame",
                                "Glowing oni mask with animated cyan/crimson eye flares and subtle flame loop.",
                                "64x64",
                                CommissionStatus.IN_PROGRESS,
                                staffEmployeeId,
                                designerEmployeeId,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "ANIMATED_GIF"),
                                                ctx.single("MATRIX_SIZE", "64x64"),
                                                ctx.single("FRAME_RANGE", "16-30"),
                                                ctx.quantity(RULE_EXTRA_VARIANTS, 2),
                                                ctx.text(RULE_PALETTE_NOTES,
                                                                "Crimson #E11D48 + Electric violet #7C3AED")));
                inProgress.setSlaDueAt(now.plus(48, ChronoUnit.HOURS));
                commissionRequestRepository.save(inProgress);
                attachReferences(inProgress.getCommissionId(), List.of(
                                new AttachmentSeed(CDN_SEED_PREFIX + "oni-front.png", "Mask horns & jaw shape"),
                                new AttachmentSeed(CDN_SEED_PREFIX + "oni-glow.png", "Eye flare glow intensity")));
                addSystemUnlockMessage(inProgress);
                addChatMessage(inProgress.getCommissionId(), customerId, CommissionMessageSenderRole.CUSTOMER,
                                CommissionMessageType.TEXT,
                                "Could the horns glow slightly dimmer than the eyes for better contrast?", null, null);
                if (staffEmployeeId != null) {
                        addChatMessage(inProgress.getCommissionId(), staffEmployeeId, CommissionMessageSenderRole.STAFF,
                                        CommissionMessageType.TEXT,
                                        "Noted! Our pixel artist is blocking out the 64x64 animation frames now.", null,
                                        null);
                }
                addChatMessage(inProgress.getCommissionId(), customerId, CommissionMessageSenderRole.CUSTOMER,
                                CommissionMessageType.IMAGE, "Exact hex gradient reference attached.",
                                CDN_SEED_PREFIX + "oni-swatch.png", null);

                // 6. INTERNAL_REVIEW — contains PENDING_STAFF_REVIEW draft (hidden from
                // customer GET /{id}, visible in management)
                CommissionRequestEntity internalReview = saveCommission(
                                customerId,
                                "[SEED] Arcade Ghost Chaser",
                                "Retro 8-bit arcade pixel ghost with glitch trail effect.",
                                "32x64",
                                CommissionStatus.INTERNAL_REVIEW,
                                staffEmployeeId,
                                designerEmployeeId,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "STATIC_IMAGE"),
                                                ctx.single("MATRIX_SIZE", "32x64"),
                                                ctx.multi(RULE_ADDON_EXTRAS, List.of("SOURCE_FILE_ASEPRITE"))));
                internalReview.setSlaDueAt(now.plus(24, ChronoUnit.HOURS));
                commissionRequestRepository.save(internalReview);
                addSystemUnlockMessage(internalReview);
                if (staffEmployeeId != null) {
                        addChatMessage(internalReview.getCommissionId(), staffEmployeeId,
                                        CommissionMessageSenderRole.STAFF,
                                        CommissionMessageType.TEXT,
                                        "Your artwork draft is currently undergoing internal quality review.", null,
                                        null);
                }
                if (designerEmployeeId != null) {
                        saveSubmission(internalReview.getCommissionId(), designerEmployeeId, 1,
                                        CDN_SEED_PREFIX + "ghost-chaser-v1.png",
                                        "Initial clean pixel silhouette aligned to 32x64 safe border.",
                                        CommissionSubmissionStatus.PENDING_STAFF_REVIEW, null, null, null, null);
                }

                // 7. INTERNAL_REVISION_REQUIRED — designer queue visibility + internal
                // STAFF_REJECTED draft note
                CommissionRequestEntity internalRevision = saveCommission(
                                customerId,
                                "[SEED] Pixel Mecha Core Icon",
                                "Compact futuristic mecha core emblem with high contrast LED pixels.",
                                "32x32",
                                CommissionStatus.INTERNAL_REVISION_REQUIRED,
                                staffEmployeeId,
                                designerEmployeeId,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "STATIC_IMAGE"),
                                                ctx.single("MATRIX_SIZE", "32x32")));
                internalRevision.setSlaDueAt(now.plus(18, ChronoUnit.HOURS));
                commissionRequestRepository.save(internalRevision);
                addSystemUnlockMessage(internalRevision);
                addChatMessage(internalRevision.getCommissionId(), customerId, CommissionMessageSenderRole.CUSTOMER,
                                CommissionMessageType.TEXT,
                                "Please keep the center core bright white so it pops at night.", null, null);
                if (designerEmployeeId != null) {
                        saveSubmission(internalRevision.getCommissionId(), designerEmployeeId, 1,
                                        CDN_SEED_PREFIX + "mecha-core-v1.png",
                                        "First internal draft for QA.",
                                        CommissionSubmissionStatus.STAFF_REJECTED,
                                        staffEmployeeId,
                                        "Outer rim is too faint on LED simulator; boost brightness by 20% and thicken border.",
                                        now.minus(2, ChronoUnit.HOURS),
                                        null);
                }

                // 8. PREVIEW_DELIVERED — customer sees v2 STAFF_APPROVED + DELIVERABLE_CARD;
                // management sees both v1 & v2
                CommissionRequestEntity previewDelivered = saveCommission(
                                customerId,
                                "[SEED] Turbo Boost Gauges",
                                "Animated dual turbo boost pressure sweep matching RPM surge.",
                                "64x64",
                                CommissionStatus.PREVIEW_DELIVERED,
                                staffEmployeeId,
                                designerEmployeeId,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "ANIMATED_GIF"),
                                                ctx.single("MATRIX_SIZE", "64x64"),
                                                ctx.single("FRAME_RANGE", "6-15"),
                                                ctx.single(RULE_GIF_EXPORT_PROFILE, "HIGH_LOOP_SMOOTH")));
                previewDelivered.setAutoCompleteAt(now.plus(5, ChronoUnit.DAYS));
                commissionRequestRepository.save(previewDelivered);
                addSystemUnlockMessage(previewDelivered);
                addChatMessage(previewDelivered.getCommissionId(), customerId, CommissionMessageSenderRole.CUSTOMER,
                                CommissionMessageType.TEXT, "Looking forward to seeing the sweep speed!", null, null);
                if (designerEmployeeId != null) {
                        saveSubmission(previewDelivered.getCommissionId(), designerEmployeeId, 1,
                                        CDN_SEED_PREFIX + "turbo-gauges-v1.gif",
                                        "Draft v1 with 10 frames.",
                                        CommissionSubmissionStatus.STAFF_REJECTED,
                                        staffEmployeeId,
                                        "Needle skip between frame 6 and 7; smooth interpolation needed.",
                                        now.minus(1, ChronoUnit.DAYS),
                                        null);
                        CommissionSubmissionEntity approvedV2 = saveSubmission(previewDelivered.getCommissionId(),
                                        designerEmployeeId, 2,
                                        CDN_SEED_PREFIX + "turbo-gauges-v2.gif",
                                        "Smoothed 14-frame sweep at 30 FPS with crisp amber/red redline zone.",
                                        CommissionSubmissionStatus.STAFF_APPROVED,
                                        staffEmployeeId,
                                        "QA passed — clean seamless loop.",
                                        now.minus(6, ChronoUnit.HOURS),
                                        now.minus(6, ChronoUnit.HOURS));
                        addChatMessage(
                                        previewDelivered.getCommissionId(),
                                        staffEmployeeId != null ? staffEmployeeId : designerEmployeeId,
                                        CommissionMessageSenderRole.STAFF,
                                        CommissionMessageType.DELIVERABLE_CARD,
                                        "Preview v2 has passed QA review and is ready for your approval.",
                                        approvedV2.getImageUrl(),
                                        deliverableMetadataJson(approvedV2));
                }

                // 9. COMPLETED — final approved submission linked via approvedSubmissionId,
                // closed chat
                CommissionRequestEntity completed = saveCommission(
                                customerId,
                                "[SEED] Hologram Fox Companion",
                                "Minimalist holographic fox companion idle animation for side window.",
                                "32x64",
                                CommissionStatus.COMPLETED,
                                staffEmployeeId,
                                designerEmployeeId,
                                1,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "STATIC_IMAGE"),
                                                ctx.single("MATRIX_SIZE", "32x64"),
                                                ctx.multi(RULE_ADDON_EXTRAS,
                                                                List.of("PARALLAX_EFFECT", "COMMERCIAL_LICENSE"))));
                addSystemUnlockMessage(completed);
                if (designerEmployeeId != null) {
                        CommissionSubmissionEntity accepted = saveSubmission(completed.getCommissionId(),
                                        designerEmployeeId, 1,
                                        CDN_SEED_PREFIX + "hologram-fox-final.png",
                                        "Final approved 32x64 holographic fox pixel sprite.",
                                        CommissionSubmissionStatus.CUSTOMER_ACCEPTED,
                                        staffEmployeeId,
                                        "Approved for customer delivery.",
                                        now.minus(4, ChronoUnit.DAYS),
                                        now.minus(4, ChronoUnit.DAYS));
                        completed.setApprovedSubmissionId(accepted.getSubmissionId());
                        commissionRequestRepository.save(completed);
                        addChatMessage(
                                        completed.getCommissionId(),
                                        staffEmployeeId != null ? staffEmployeeId : designerEmployeeId,
                                        CommissionMessageSenderRole.STAFF,
                                        CommissionMessageType.DELIVERABLE_CARD,
                                        "Final artwork delivered and accepted.",
                                        accepted.getImageUrl(),
                                        deliverableMetadataJson(accepted));
                }

                // 10. CANCELLED — rejected during initial intake with rejectionReason populated
                CommissionRequestEntity cancelled = saveCommission(
                                customerId,
                                "[SEED] Trademarked Emblem Copy",
                                "Exact replica of commercial automaker trademark logo.",
                                "32x32",
                                CommissionStatus.CANCELLED,
                                staffEmployeeId,
                                null,
                                0,
                                ctx.buildBreakdown(
                                                ctx.single("FORMAT_TYPE", "STATIC_IMAGE"),
                                                ctx.single("MATRIX_SIZE", "32x32")));
                cancelled.setRejectionReason(
                                "Submitted reference contains trademarked brand assets without commercial license proof.");
                commissionRequestRepository.save(cancelled);
                attachReferences(cancelled.getCommissionId(), List.of(
                                new AttachmentSeed(CDN_SEED_PREFIX + "rejected-logo-ref.png",
                                                "Trademarked logo reference")));
        }

        private CommissionRequestEntity saveCommission(
                        Long customerId,
                        String title,
                        String description,
                        String targetMatrixDimensions,
                        CommissionStatus status,
                        Long assignedStaffId,
                        Long assignedDesignerId,
                        int revisionsUsed,
                        ComputedBreakdown breakdown) {
                CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                                customerId,
                                title,
                                description,
                                breakdown.tierCode(),
                                targetMatrixDimensions,
                                breakdown.json(),
                                breakdown.totalPrice(),
                                breakdown.maxRevisions(),
                                assignedStaffId);
                entity.setStatus(status);
                entity.setAssignedDesignerId(assignedDesignerId);
                entity.setRevisionsUsed(revisionsUsed);
                return commissionRequestRepository.saveAndFlush(entity);
        }

        private void attachReferences(Long commissionId, List<AttachmentSeed> attachments) {
                for (int i = 0; i < attachments.size(); i++) {
                        AttachmentSeed seed = attachments.get(i);
                        attachmentRepository.save(CommissionAttachmentEntity.create(commissionId, seed.imageUrl(),
                                        seed.caption(), i));
                }
        }

        private CommissionSubmissionEntity saveSubmission(
                        Long commissionId,
                        Long designerId,
                        int versionNumber,
                        String imageUrl,
                        String designerNote,
                        CommissionSubmissionStatus status,
                        Long reviewerStaffId,
                        String staffReviewNote,
                        Instant reviewedAt,
                        Instant deliveredAt) {
                CommissionSubmissionEntity submission = CommissionSubmissionEntity.createDraft(
                                commissionId, designerId, versionNumber, imageUrl, designerNote);
                submission.setStatus(status);
                submission.setReviewerStaffId(reviewerStaffId);
                submission.setStaffReviewNote(staffReviewNote);
                submission.setReviewedAt(reviewedAt);
                submission.setDeliveredAt(deliveredAt);
                return submissionRepository.saveAndFlush(submission);
        }

        private void addSystemUnlockMessage(CommissionRequestEntity commission) {
                String amountDisplay = commission.getInitialPrice().stripTrailingZeros().toPlainString() + " VND";
                CommissionMessageEntity msg = CommissionMessageEntity.createSystemMessage(
                                commission.getCommissionId(),
                                "Thanh toán thành công. Đã nhận" + amountDisplay
                                                + "đầu. Quá trình trao đổi về yêu cầu thiết kế, sử dụng chatbox đã mở.");
                messageRepository.saveAndFlush(msg);
        }

        private void addChatMessage(
                        Long commissionId,
                        Long senderId,
                        CommissionMessageSenderRole senderRole,
                        CommissionMessageType messageType,
                        String content,
                        String mediaUrl,
                        String metadataJson) {
                CommissionMessageEntity msg = CommissionMessageEntity.create(
                                commissionId, senderId, senderRole, messageType, content, mediaUrl, metadataJson);
                messageRepository.saveAndFlush(msg);
        }

        private String deliverableMetadataJson(CommissionSubmissionEntity submission) {
                try {
                        return objectMapper.writeValueAsString(Map.of(
                                        "submissionId", submission.getSubmissionId(),
                                        "versionNumber", submission.getVersionNumber(),
                                        "imageUrl", submission.getImageUrl()));
                } catch (JsonProcessingException ex) {
                        log.warn("Failed to serialize deliverable card metadata [submissionId={}]",
                                        submission.getSubmissionId(), ex);
                        return "{}";
                }
        }

        private CustomerProfileSnapshot resolveCustomer() {
                if (customerEmail != null && !customerEmail.isBlank()) {
                        CustomerProfileSnapshot byEmail = customerLookupService.findProfileByEmail(customerEmail.trim())
                                        .orElse(null);
                        if (byEmail != null) {
                                return byEmail;
                        }
                }
                return customerLookupService.findFirstProfile().orElse(null);
        }

        private Long resolveStaffEmployeeId() {
                List<Long> staffIds = employeeLookupService.findActiveStaffEmployeeIds();
                if (staffIds == null || staffIds.isEmpty()) {
                        return null;
                }
                if (staffEmail != null && !staffEmail.isBlank()) {
                        Map<Long, EmployeeProfileSnapshot> profiles = employeeLookupService
                                        .findProfilesByEmployeeIds(staffIds);
                        if (profiles != null) {
                                for (Long id : staffIds) {
                                        EmployeeProfileSnapshot profile = profiles.get(id);
                                        if (profile != null && profile.email() != null
                                                        && profile.email().equalsIgnoreCase(staffEmail.trim())) {
                                                return id;
                                        }
                                }
                        }
                }
                return staffIds.get(0);
        }

        private Long resolveDesignerEmployeeId() {
                List<EmployeeProfileSnapshot> designers = employeeLookupService.findActiveDesigners();
                if (designers == null || designers.isEmpty()) {
                        return null;
                }
                if (designerEmail != null && !designerEmail.isBlank()) {
                        for (EmployeeProfileSnapshot profile : designers) {
                                if (profile.email() != null && profile.email().equalsIgnoreCase(designerEmail.trim())) {
                                        return profile.employeeId();
                                }
                        }
                }
                return designers.get(0).employeeId();
        }

        private static CommissionPriceOptionEntity findOption(CommissionPriceConfigEntity config, String optionCode) {
                if (config == null || config.getOptions() == null || optionCode == null) {
                        return null;
                }
                return config.getOptions().stream()
                                .filter(Objects::nonNull)
                                .filter(CommissionPriceOptionEntity::isActive)
                                .filter(opt -> optionCode.equalsIgnoreCase(opt.getOptionCode()))
                                .findFirst()
                                .orElse(null);
        }

        private final class PricingContext {
                private final CommissionPricingTierEntity tier;
                private final Map<String, CommissionPriceConfigEntity> configsByKey;

                private PricingContext(CommissionPricingTierEntity tier,
                                Map<String, CommissionPriceConfigEntity> configsByKey) {
                        this.tier = tier;
                        this.configsByKey = configsByKey;
                }

                private SelectionSeed single(String ruleKey, String optionCode) {
                        CommissionPriceConfigEntity config = configsByKey.get(ruleKey.toUpperCase(Locale.ROOT));
                        CommissionPriceOptionEntity option = findOption(config, optionCode);
                        return new SelectionSeed(config, option != null ? List.of(option) : List.of(), 1, null);
                }

                private SelectionSeed multi(String ruleKey, List<String> optionCodes) {
                        CommissionPriceConfigEntity config = configsByKey.get(ruleKey.toUpperCase(Locale.ROOT));
                        List<CommissionPriceOptionEntity> selected = new ArrayList<>();
                        for (String code : optionCodes) {
                                CommissionPriceOptionEntity opt = findOption(config, code);
                                if (opt != null) {
                                        selected.add(opt);
                                }
                        }
                        return new SelectionSeed(config, selected, 1, null);
                }

                private SelectionSeed quantity(String ruleKey, int quantity) {
                        CommissionPriceConfigEntity config = configsByKey.get(ruleKey.toUpperCase(Locale.ROOT));
                        List<CommissionPriceOptionEntity> unitOptions = (config != null && config.getOptions() != null
                                        && !config.getOptions().isEmpty())
                                                        ? List.of(config.getOptions().get(0))
                                                        : List.of();
                        return new SelectionSeed(config, unitOptions, quantity, null);
                }

                private SelectionSeed text(String ruleKey, String textValue) {
                        CommissionPriceConfigEntity config = configsByKey.get(ruleKey.toUpperCase(Locale.ROOT));
                        return new SelectionSeed(config, List.of(), 1, textValue);
                }

                private ComputedBreakdown buildBreakdown(SelectionSeed... seeds) {
                        List<SelectionSeed> validSeeds = new ArrayList<>();
                        List<ModifierLineInput> chargeLines = new ArrayList<>();
                        Map<LineKey, Integer> indexByKey = new HashMap<>();

                        for (SelectionSeed seed : seeds) {
                                if (seed == null || seed.config() == null) {
                                        continue;
                                }
                                validSeeds.add(seed);
                                for (CommissionPriceOptionEntity opt : seed.options()) {
                                        indexByKey.put(new LineKey(seed.config().getConfigId(), opt.getOptionId()),
                                                        chargeLines.size());
                                        chargeLines.add(new ModifierLineInput(seed.config(), opt, seed.quantity()));
                                }
                        }

                        BigDecimal tierBase = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
                        ModifierCalculationResult calc = invariantValidator.calculateModifierPipeline(tierBase,
                                        chargeLines);

                        List<RuleSelectionSnapshot> ruleSnapshots = new ArrayList<>();
                        for (SelectionSeed seed : validSeeds) {
                                CommissionPriceConfigEntity config = seed.config();
                                List<OptionSnapshot> optionSnapshots = new ArrayList<>();
                                BigDecimal ruleTotal = BigDecimal.ZERO;

                                for (CommissionPriceOptionEntity opt : seed.options()) {
                                        Integer idx = indexByKey
                                                        .get(new LineKey(config.getConfigId(), opt.getOptionId()));
                                        BigDecimal amount = (idx != null && idx < calc.lineAmounts().size())
                                                        ? calc.lineAmounts().get(idx)
                                                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                                        ruleTotal = ruleTotal.add(amount);
                                        optionSnapshots.add(new OptionSnapshot(
                                                        opt.getOptionId(),
                                                        opt.getOptionCode(),
                                                        opt.getOptionLabel(),
                                                        opt.resolvedModifierType(),
                                                        opt.getBasePrice() != null ? opt.getBasePrice()
                                                                        : BigDecimal.ZERO,
                                                        false,
                                                        amount));
                                }

                                ruleSnapshots.add(new RuleSelectionSnapshot(
                                                config.getConfigId(),
                                                config.getRuleKey(),
                                                config.getName(),
                                                config.getSelectionType(),
                                                config.isBasePrice(),
                                                config.getSelectionType() == SelectionType.QUANTITY ? seed.quantity()
                                                                : null,
                                                seed.textValue(),
                                                optionSnapshots,
                                                ruleTotal.setScale(2, RoundingMode.HALF_UP)));
                        }

                        CommissionPriceBreakdownSnapshot snapshot = new CommissionPriceBreakdownSnapshot(
                                        tier.getTierCode(),
                                        tier.getName(),
                                        tier.isCustom(),
                                        tierBase.setScale(2, RoundingMode.HALF_UP),
                                        tier.getDiscountPercent(),
                                        tier.getMaxRevisions(),
                                        ruleSnapshots,
                                        calc.baseBucketTotal(),
                                        calc.finalTotal(),
                                        calc.finalTotal());

                        String json;
                        try {
                                json = objectMapper.writeValueAsString(snapshot);
                        } catch (JsonProcessingException ex) {
                                log.warn("Failed to serialize seeded price breakdown snapshot", ex);
                                json = "{}";
                        }
                        return new ComputedBreakdown(tier.getTierCode(), tier.getMaxRevisions(), calc.finalTotal(),
                                        json);
                }
        }

        private record SelectionSeed(
                        CommissionPriceConfigEntity config,
                        List<CommissionPriceOptionEntity> options,
                        int quantity,
                        String textValue) {
        }

        private record LineKey(Long configId, Long optionId) {
        }

        private record ComputedBreakdown(
                        String tierCode,
                        int maxRevisions,
                        BigDecimal totalPrice,
                        String json) {
        }

        private record AttachmentSeed(String imageUrl, String caption) {
        }
}
