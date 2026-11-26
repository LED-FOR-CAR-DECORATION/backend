package com.lumintra.backend.payment.infrastructure.persistence.mapper;

import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentSessionMapper {

    public PaymentSessionResponse toResponse(
            PaymentTransactionEntity entity,
            String paymentCode,
            PaymentTargetType targetType,
            SePayQrDetails qrDetails,
            PaymentStatus effectiveStatus,
            Instant expiresAt) {
        if (entity == null) {
            return null;
        }

        PaymentStatus resolvedStatus = effectiveStatus != null ? effectiveStatus : entity.getPaymentStatus();
        boolean activePending = resolvedStatus == PaymentStatus.PENDING;

        return PaymentSessionResponse.builder()
                .paymentId(entity.getPaymentId())
                .paymentCode(paymentCode)
                .targetType(targetType != null ? targetType : PaymentTargetType.ORDER)
                .targetId(entity.getOrderId())
                .gateway(entity.getGateway())
                .qrUrl(activePending && qrDetails != null ? qrDetails.qrUrl() : null)
                .bankName(qrDetails != null ? qrDetails.bankName() : null)
                .accountNumber(qrDetails != null ? qrDetails.accountNumber() : null)
                .accountName(qrDetails != null ? qrDetails.accountName() : null)
                .amount(entity.getAmount())
                .status(resolvedStatus)
                .expiresAt(expiresAt)
                .paidAt(entity.getPaidAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
