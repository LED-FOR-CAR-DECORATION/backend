package com.lumintra.backend.commission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.bootstrap.CommissionFlowDataSeeder;
import com.lumintra.backend.commission.infrastructure.bootstrap.CommissionPricingDataSeeder;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
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
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commission_seeder_int_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.test-customer-email=customer@lumintra.com",
        "app.seed.test-staff-email=staff@lumintra.com",
        "app.seed.test-designer-email=designer@lumintra.com"
})
@ActiveProfiles("test")
@Import({
        CommissionPricingDataSeeder.class,
        CommissionFlowDataSeeder.class,
        PricingInvariantValidator.class,
        CommissionFlowSeederIntegrationTest.TestConfig.class
})
class CommissionFlowSeederIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private CommissionPricingDataSeeder pricingDataSeeder;

    @Autowired
    private CommissionFlowDataSeeder flowDataSeeder;

    @Autowired
    private CommissionPriceConfigRepository configRepository;

    @Autowired
    private CommissionPricingTierRepository tierRepository;

    @Autowired
    private CommissionRequestRepository commissionRequestRepository;

    @Autowired
    private CommissionAttachmentRepository attachmentRepository;

    @Autowired
    private CommissionSubmissionRepository submissionRepository;

    @Autowired
    private CommissionMessageRepository messageRepository;

    @MockitoBean
    private CustomerLookupService customerLookupService;

    @MockitoBean
    private EmployeeLookupService employeeLookupService;

    @MockitoBean
    private PaymentSessionPort paymentSessionPort;

    @Test
    @DisplayName("Flow 3 Seeder IT: CommissionPricingDataSeeder + CommissionFlowDataSeeder persist full pricing & ticket graph idempotently")
    void seedersPersistRealJpaEntitiesIdempotently() {
        given(customerLookupService.findProfileByEmail("customer@lumintra.com"))
                .willReturn(Optional.of(new CustomerProfileSnapshot(42L, "Test Customer", "0901234567", "customer@lumintra.com")));
        given(employeeLookupService.findActiveStaffEmployeeIds()).willReturn(List.of(12L));
        given(employeeLookupService.findProfilesByEmployeeIds(List.of(12L)))
                .willReturn(Map.of(12L, new EmployeeProfileSnapshot(12L, "Test Staff", "staff@lumintra.com")));
        given(employeeLookupService.findActiveDesigners())
                .willReturn(List.of(new EmployeeProfileSnapshot(15L, "Test Designer", "designer@lumintra.com")));
        given(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class))).willAnswer(inv -> {
            CreatePaymentSessionCommand cmd = inv.getArgument(0);
            return PaymentSessionResponse.builder()
                    .paymentCode("LMTCMS" + cmd.targetId() + "SEED0001")
                    .targetType(cmd.targetType())
                    .targetId(cmd.targetId())
                    .amount(cmd.amount())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        });

        DefaultApplicationArguments args = new DefaultApplicationArguments();
        pricingDataSeeder.run(args);
        flowDataSeeder.run(args);

        assertThat(configRepository.findActiveWithActiveOptions())
                .extracting(CommissionPriceConfigEntity::getRuleKey)
                .containsExactlyInAnyOrder(
                        "FORMAT_TYPE",
                        "MATRIX_SIZE",
                        "FRAME_RANGE",
                        CommissionFlowDataSeeder.RULE_ADDON_EXTRAS,
                        CommissionFlowDataSeeder.RULE_EXTRA_VARIANTS,
                        CommissionFlowDataSeeder.RULE_PALETTE_NOTES,
                        CommissionFlowDataSeeder.RULE_GIF_EXPORT_PROFILE
                );
        assertThat(tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc())
                .hasSize(1)
                .first()
                .satisfies(tier -> {
                    assertThat(tier.getTierCode()).isEqualTo("CUSTOM");
                    assertThat(tier.getMaxRevisions()).isEqualTo(1);
                });

        List<CommissionRequestEntity> commissions = commissionRequestRepository.findAll();
        assertThat(commissions).hasSize(10);

        CommissionRequestEntity pendingPayment = commissions.stream()
                .filter(c -> c.getStatus() == CommissionStatus.PENDING_PAYMENT)
                .findFirst()
                .orElseThrow();
        assertThat(pendingPayment.getActivePaymentCode())
                .isEqualTo("LMTCMS" + pendingPayment.getCommissionId() + "SEED0001");

        CommissionRequestEntity previewDelivered = commissions.stream()
                .filter(c -> c.getStatus() == CommissionStatus.PREVIEW_DELIVERED)
                .findFirst()
                .orElseThrow();
        List<CommissionSubmissionEntity> visiblePreviews = submissionRepository
                .findAllByCommissionIdAndStatusInOrderByVersionNumberAscSubmissionIdAsc(
                        previewDelivered.getCommissionId(),
                        CommissionSubmissionStatus.customerVisibleStatuses()
                );
        assertThat(visiblePreviews).hasSize(1);
        assertThat(visiblePreviews.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(messageRepository.findAllByCommissionIdOrderByCreatedAtAsc(previewDelivered.getCommissionId()))
                .hasSize(3);

        CommissionRequestEntity completed = commissions.stream()
                .filter(c -> c.getStatus() == CommissionStatus.COMPLETED)
                .findFirst()
                .orElseThrow();
        assertThat(completed.getApprovedSubmissionId()).isNotNull();

        long attachmentCount = attachmentRepository.count();
        long submissionCount = submissionRepository.count();
        long messageCount = messageRepository.count();
        assertThat(attachmentCount).isEqualTo(7);
        assertThat(submissionCount).isEqualTo(5);
        assertThat(messageCount).isGreaterThanOrEqualTo(10);

        // Idempotent second run
        pricingDataSeeder.run(args);
        flowDataSeeder.run(args);

        assertThat(configRepository.count()).isEqualTo(7);
        assertThat(tierRepository.count()).isEqualTo(1);
        assertThat(commissionRequestRepository.count()).isEqualTo(10);
        assertThat(attachmentRepository.count()).isEqualTo(attachmentCount);
        assertThat(submissionRepository.count()).isEqualTo(submissionCount);
        assertThat(messageRepository.count()).isEqualTo(messageCount);
    }
}
