package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCustomerCommissionDetailUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionAttachmentRepository commissionAttachmentRepository;

    @Mock
    private CommissionSubmissionRepository commissionSubmissionRepository;

    private CommissionRequestMapper commissionRequestMapper;

    private GetCustomerCommissionDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        commissionRequestMapper = new CommissionRequestMapper();
        useCase = new GetCustomerCommissionDetailUseCase(
                commissionRequestRepository,
                commissionAttachmentRepository,
                commissionSubmissionRepository,
                commissionRequestMapper
        );
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID01 [N]: Returns full commission detail, reference attachments, and Staff-approved previews for owning customer")
    void execute_owningCustomer_returnsDetailWithAttachmentsAndApprovedPreviews() {
        CommissionRequestEntity commission = CommissionRequestEntity.createPendingReview(
                10L,
                "Cyberpunk Shiba Inu",
                "Neon visor winking animation",
                "ADVANCED",
                "64x64",
                "{\"tierCode\":\"ADVANCED\"}",
                new BigDecimal("350000.00"),
                3,
                20L
        );
        commission.setCommissionId(501L);
        commission.setStatus(CommissionStatus.PREVIEW_DELIVERED);
        commission.setActivePaymentCode("STALE-CODE-SHOULD-BE-HIDDEN");

        CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                501L,
                "https://storage.googleapis.com/lumintra-buckets/commission-references/ref-1.png",
                "Front angle palette",
                0
        );
        attachment.setAttachmentId(701L);
        attachment.setCreatedAt(Instant.parse("2026-11-15T10:00:00Z"));

        CommissionSubmissionEntity approvedSubmission = CommissionSubmissionEntity.createDraft(
                501L,
                30L,
                2,
                "https://storage.googleapis.com/lumintra-buckets/commission-drafts/shiba-v2.gif",
                "24-frame rain loop"
        );
        approvedSubmission.setSubmissionId(9002L);
        approvedSubmission.setStatus(CommissionSubmissionStatus.STAFF_APPROVED);
        approvedSubmission.setDeliveredAt(Instant.parse("2026-11-16T08:00:00Z"));
        approvedSubmission.setCreatedAt(Instant.parse("2026-11-16T07:30:00Z"));

        when(commissionRequestRepository.findById(501L)).thenReturn(Optional.of(commission));
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(501L))
                .thenReturn(List.of(attachment));
        when(commissionSubmissionRepository.findAllByCommissionIdAndStatusInOrderByVersionNumberAscSubmissionIdAsc(
                501L, CommissionSubmissionStatus.customerVisibleStatuses()))
                .thenReturn(List.of(approvedSubmission));

        CustomerCommissionDetailResponse response = useCase.execute(10L, 501L);

        assertThat(response.commissionId()).isEqualTo(501L);
        assertThat(response.customerId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Cyberpunk Shiba Inu");
        assertThat(response.status()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(response.activePaymentCode()).isNull();
        assertThat(response.chatActive()).isTrue();
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().getFirst().attachmentId()).isEqualTo(701L);
        assertThat(response.deliveredPreviews()).hasSize(1);
        assertThat(response.deliveredPreviews().getFirst().submissionId()).isEqualTo(9002L);
        assertThat(response.deliveredPreviews().getFirst().status()).isEqualTo(CommissionSubmissionStatus.STAFF_APPROVED);
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID02 [N]: Exposes activePaymentCode when status is PENDING_PAYMENT or AWAITING_EXTRA_PAYMENT")
    void execute_whenPaymentPending_exposesActivePaymentCode() {
        CommissionRequestEntity commission = CommissionRequestEntity.createPendingReview(
                10L,
                "Pixel Dragon",
                "Breathing fire",
                "BASIC",
                "32x32",
                "{}",
                new BigDecimal("150000.00"),
                1,
                20L
        );
        commission.setCommissionId(502L);
        commission.setStatus(CommissionStatus.PENDING_PAYMENT);
        commission.setActivePaymentCode("LMTCMS502INIT");
        commission.setQuoteExpiresAt(Instant.parse("2026-11-19T10:00:00Z"));

        when(commissionRequestRepository.findById(502L)).thenReturn(Optional.of(commission));
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(502L))
                .thenReturn(List.of());
        when(commissionSubmissionRepository.findAllByCommissionIdAndStatusInOrderByVersionNumberAscSubmissionIdAsc(
                502L, CommissionSubmissionStatus.customerVisibleStatuses()))
                .thenReturn(List.of());

        CustomerCommissionDetailResponse response = useCase.execute(10L, 502L);

        assertThat(response.activePaymentCode()).isEqualTo("LMTCMS502INIT");
        assertThat(response.chatActive()).isFalse();
        assertThat(response.quoteExpiresAt()).isEqualTo(Instant.parse("2026-11-19T10:00:00Z"));
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID03 [B]: Null or non-positive customerId throws InvalidRuleConfigurationException")
    void execute_nullOrNonPositiveCustomerId_throwsInvalidRuleConfigurationException() {
        assertThatThrownBy(() -> useCase.execute(null, 501L))
                .isInstanceOf(InvalidRuleConfigurationException.class)
                .hasMessageContaining("Authenticated customer ID is required");

        assertThatThrownBy(() -> useCase.execute(0L, 501L))
                .isInstanceOf(InvalidRuleConfigurationException.class);

        verifyNoInteractions(commissionRequestRepository, commissionAttachmentRepository, commissionSubmissionRepository);
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID04 [B]: Null or non-positive commissionId throws CommissionNotFoundException")
    void execute_nullOrNonPositiveCommissionId_throwsCommissionNotFoundException() {
        assertThatThrownBy(() -> useCase.execute(10L, null))
                .isInstanceOf(CommissionNotFoundException.class);

        assertThatThrownBy(() -> useCase.execute(10L, -1L))
                .isInstanceOf(CommissionNotFoundException.class);

        verifyNoInteractions(commissionRequestRepository, commissionAttachmentRepository, commissionSubmissionRepository);
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID05 [A]: Non-existent commissionId throws CommissionNotFoundException")
    void execute_nonExistentCommission_throwsCommissionNotFoundException() {
        when(commissionRequestRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(10L, 9999L))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("9999");

        verify(commissionRequestRepository).findById(9999L);
        verifyNoInteractions(commissionAttachmentRepository, commissionSubmissionRepository);
    }

    @Test
    @DisplayName("CMS-05 UseCase UTCID06 [A]: Commission owned by another customer throws CommissionOwnershipMismatchException")
    void execute_otherCustomersCommission_throwsCommissionOwnershipMismatchException() {
        CommissionRequestEntity commission = CommissionRequestEntity.createPendingReview(
                99L,
                "Other Customer Commission",
                "Private request",
                "BASIC",
                "32x32",
                "{}",
                new BigDecimal("150000.00"),
                1,
                20L
        );
        commission.setCommissionId(501L);

        when(commissionRequestRepository.findById(501L)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> useCase.execute(10L, 501L))
                .isInstanceOf(CommissionOwnershipMismatchException.class)
                .hasMessageContaining("permission");

        verify(commissionRequestRepository).findById(501L);
        verifyNoInteractions(commissionAttachmentRepository, commissionSubmissionRepository);
    }
}
