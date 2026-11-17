package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ApproveCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveCommissionRequestUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private PaymentSessionPort paymentSessionPort;

    @InjectMocks
    private ApproveCommissionRequestUseCase useCase;

    private PaymentSessionResponse samplePaymentSession;

    @BeforeEach
    void setUp() {
        samplePaymentSession = PaymentSessionResponse.builder()
                .paymentId(501L)
                .paymentCode("LMTCMS105")
                .targetType(PaymentTargetType.COMMISSION_INITIAL)
                .targetId(105L)
                .gateway("SEPAY")
                .qrUrl("https://qr.sepay.vn/img?des=LMTCMS105")
                .bankName("MBBank")
                .accountNumber("0123456789")
                .accountName("LUMINTRA")
                .amount(BigDecimal.valueOf(420000.00))
                .status(PaymentStatus.PENDING)
                .expiresAt(Instant.now().plus(Duration.ofHours(72)))
                .createdAt(Instant.now())
                .build();
    }

    private CommissionRequestEntity createPendingEntity(Long commissionId, Long customerId, Long assignedStaffId) {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                customerId,
                "Cyberpunk Neon Cat Animation",
                "Pixel art animation",
                "ADVANCED",
                "64x64",
                "{}",
                BigDecimal.valueOf(420000.00),
                3,
                assignedStaffId
        );
        entity.setCommissionId(commissionId);
        entity.setCreatedAt(Instant.parse("2026-11-17T08:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-17T08:00:00Z"));
        return entity;
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID01 [N]: Staff approves commission when unassigned -> assigns staff, sets quoteExpiresAt (+72h), creates payment session, transitions to PENDING_PAYMENT")
    void execute_asStaff_unassignedCommission_approvesSuccessfully() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, null);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class))).thenReturn(samplePaymentSession);
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveCommissionResponse response = useCase.execute(105L, 3L, false);

        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.title()).isEqualTo("Cyberpunk Neon Cat Animation");
        assertThat(response.status()).isEqualTo(CommissionStatus.PENDING_PAYMENT);
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.activePaymentCode()).isEqualTo("LMTCMS105");
        assertThat(response.payableAmount()).isEqualByComparingTo(BigDecimal.valueOf(420000.00));
        assertThat(response.quoteExpiresAt()).isAfter(Instant.now().plus(Duration.ofHours(71)));
        assertThat(response.paymentSession()).isEqualTo(samplePaymentSession);

        ArgumentCaptor<CreatePaymentSessionCommand> cmdCaptor = ArgumentCaptor.forClass(CreatePaymentSessionCommand.class);
        verify(paymentSessionPort).createSession(cmdCaptor.capture());
        CreatePaymentSessionCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(cmd.targetId()).isEqualTo(105L);
        assertThat(cmd.customerId()).isEqualTo(12L);
        assertThat(cmd.amount()).isEqualByComparingTo(BigDecimal.valueOf(420000.00));
        assertThat(cmd.ttl()).isEqualTo(Duration.ofHours(72));

        assertThat(entity.getAssignedStaffId()).isEqualTo(3L);
        assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PENDING_PAYMENT);
        assertThat(entity.getActivePaymentCode()).isEqualTo("LMTCMS105");
        assertThat(entity.getQuoteExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID02 [N]: Assigned staff member approves their own commission ticket successfully")
    void execute_asAssignedStaff_approvesSuccessfully() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, 3L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class))).thenReturn(samplePaymentSession);
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveCommissionResponse response = useCase.execute(105L, 3L, false);

        assertThat(response).isNotNull();
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(CommissionStatus.PENDING_PAYMENT);
        assertThat(response.activePaymentCode()).isEqualTo("LMTCMS105");
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID03 [N]: Privileged Manager/Admin approves commission assigned to another staff member (bypasses staff lock)")
    void execute_asPrivilegedManager_bypassesStaffLock() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, 7L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class))).thenReturn(samplePaymentSession);
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveCommissionResponse response = useCase.execute(105L, 1L, true);

        assertThat(response).isNotNull();
        assertThat(response.assignedStaffId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(CommissionStatus.PENDING_PAYMENT);
        assertThat(response.activePaymentCode()).isEqualTo("LMTCMS105");
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID04 [A]: Staff attempts to approve commission locked by another staff member -> throws CommissionStaffLockException")
    void execute_asDifferentStaff_lockedByAnotherStaff_throwsException() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, 7L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(CommissionStaffLockException.class)
                .hasMessageContaining("is locked by assigned staff 7");

        verify(paymentSessionPort, never()).createSession(any());
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID05 [A]: Commission ticket not found -> throws CommissionNotFoundException")
    void execute_commissionNotFound_throwsException() {
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, 3L, false))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("999");

        verify(paymentSessionPort, never()).createSession(any());
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID06 [A]: Commission ticket is not in PENDING_REVIEW -> throws InvalidCommissionStatusTransitionException")
    void execute_invalidCurrentStatus_throwsException() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, 3L);
        entity.setStatus(CommissionStatus.IN_PROGRESS);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("Cannot approve commission in status IN_PROGRESS (must be PENDING_REVIEW)");

        verify(paymentSessionPort, never()).createSession(any());
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-09 UseCase UTCID07 [A]: Payment session creation failure propagates and aborts status transition")
    void execute_paymentGatewayFailure_propagatesException() {
        CommissionRequestEntity entity = createPendingEntity(105L, 12L, null);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(paymentSessionPort.createSession(any(CreatePaymentSessionCommand.class)))
                .thenThrow(new IllegalStateException("Payment gateway unreachable"));

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment gateway unreachable");

        assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PENDING_REVIEW);
        verify(commissionRequestRepository, never()).save(any());
    }
}
