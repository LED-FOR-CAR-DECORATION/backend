package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ApproveCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ApproveCommissionRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveCommissionRequestUseCase.class);
    public static final Duration DEFAULT_QUOTE_EXPIRATION = Duration.ofHours(72);

    private final CommissionRequestRepository commissionRequestRepository;
    private final PaymentSessionPort paymentSessionPort;

    @Transactional
    public ApproveCommissionResponse execute(Long commissionId, Long callerStaffId, boolean isPrivilegedManagement) {
        log.info("Processing commission approval request [commissionId={}] [callerStaffId={}] [isPrivileged={}]",
                commissionId, callerStaffId, isPrivilegedManagement);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to STAFF role and enforce assigned_staff_id lock
        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivilegedManagement) {
            log.warn("Commission staff lock violation [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        // AC03: Transition PENDING_REVIEW to PENDING_PAYMENT
        if (commission.getStatus() != CommissionStatus.PENDING_REVIEW) {
            log.warn("Invalid commission status transition attempt [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot approve commission in status " + commission.getStatus() + " (must be PENDING_REVIEW)"
            );
        }

        // Auto-assign to current staff member if not already assigned
        if (commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerStaffId);
        }

        // AC02: Set quote_expires_at (+72h), invoke paymentSessionPort.createSession, store active_payment_code
        Instant now = Instant.now();

        BigDecimal payableAmount = commission.getTotalPrice() != null && commission.getTotalPrice().compareTo(BigDecimal.ZERO) > 0
                ? commission.getTotalPrice()
                : (commission.getInitialPrice() != null && commission.getInitialPrice().compareTo(BigDecimal.ZERO) > 0
                    ? commission.getInitialPrice()
                    : BigDecimal.ZERO);

        PaymentSessionResponse paymentSession = openPayment(commission, payableAmount, now);

        CommissionRequestEntity saved = commissionRequestRepository.save(commission);

        log.info("Commission approved and payment opened [commissionId={}] [assignedStaffId={}] [activePaymentCode={}] [quoteExpiresAt={}]",
                saved.getCommissionId(), saved.getAssignedStaffId(), saved.getActivePaymentCode(), saved.getQuoteExpiresAt());

        return ApproveCommissionResponse.builder()
                .commissionId(saved.getCommissionId())
                .title(saved.getTitle())
                .status(saved.getStatus())
                .assignedStaffId(saved.getAssignedStaffId())
                .quoteExpiresAt(saved.getQuoteExpiresAt())
                .activePaymentCode(saved.getActivePaymentCode())
                .payableAmount(payableAmount)
                .paymentSession(paymentSession)
                .updatedAt(saved.getUpdatedAt() != null ? saved.getUpdatedAt() : now)
                .build();
    }

    PaymentSessionResponse openPayment(CommissionRequestEntity commission, BigDecimal amount, Instant now) {
        PaymentSessionResponse session = paymentSessionPort.createSession(new CreatePaymentSessionCommand(
                PaymentTargetType.COMMISSION_INITIAL, commission.getCommissionId(), commission.getCustomerId(),
                amount, DEFAULT_QUOTE_EXPIRATION));
        commission.setQuoteExpiresAt(now.plus(DEFAULT_QUOTE_EXPIRATION));
        commission.setActivePaymentCode(session.paymentCode());
        commission.setStatus(CommissionStatus.PENDING_PAYMENT);
        return session;
    }
}
