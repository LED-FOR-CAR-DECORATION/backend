package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.application.service.RevisionCheckoutReverter;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.model.CancelPaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * CMS-22: the customer cancels a pending extra-revision checkout. The revert itself is shared with
 * CMS-26 payment expiry ({@link RevisionCheckoutReverter}).
 */
@Service
@RequiredArgsConstructor
public class CancelRevisionCheckoutUseCase {
    private final CommissionRequestRepository commissions;
    private final PaymentSessionPort payments;
    private final RevisionCheckoutReverter reverter;

    @Transactional
    public void execute(Long id, Long customerId) {
        var snapshot =
                commissions
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        validate(snapshot, customerId);
        String code = snapshot.getActivePaymentCode();
        // Payment updates precede the commission lock, matching the webhook's lock order.
        payments.cancelSession(
                new CancelPaymentSessionCommand(
                        code, PaymentTargetType.COMMISSION_REVISION, id, customerId));
        var commission =
                commissions
                        .findByIdForUpdate(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        validate(commission, customerId);
        if (!Objects.equals(code, commission.getActivePaymentCode())) {
            throw conflict("Active revision checkout changed before cancellation.");
        }
        reverter.revert(commission, code, RevisionCheckoutReverter.Reason.CANCELLED, Instant.now());
    }

    private void validate(CommissionRequestEntity commission, Long customerId) {
        if (!commission.isOwnedBy(customerId)) {
            throw new CommissionOwnershipMismatchException(
                    "Only the owning customer can cancel the revision checkout.");
        }
        if (commission.getStatus() != CommissionStatus.AWAITING_EXTRA_PAYMENT
                || commission.getActivePaymentCode() == null
                || commission.getActivePaymentCode().isBlank()) {
            throw conflict("Commission has no pending extra revision checkout.");
        }
    }

    private InvalidCommissionStatusTransitionException conflict(String message) {
        return new InvalidCommissionStatusTransitionException(message);
    }
}
