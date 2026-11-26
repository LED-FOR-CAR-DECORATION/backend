package com.lumintra.backend.payment.domain.model;

public record SePayQrDetails(
        String paymentCode,
        String qrUrl,
        String bankName,
        String accountNumber,
        String accountName
) {}
