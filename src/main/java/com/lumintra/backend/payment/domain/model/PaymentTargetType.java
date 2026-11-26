package com.lumintra.backend.payment.domain.model;

import java.util.Locale;

public enum PaymentTargetType {
    ORDER("ORD"),
    COMMISSION_INITIAL("CMS"),
    COMMISSION_REVISION("REV");

    private final String codeSegment;

    PaymentTargetType(String codeSegment) {
        this.codeSegment = codeSegment;
    }

    public String getCodeSegment() {
        return codeSegment;
    }

    public static PaymentTargetType fromPaymentCode(String paymentCode) {
        if (paymentCode == null || paymentCode.isBlank()) {
            return ORDER;
        }
        String upper = paymentCode.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("REV")) {
            return COMMISSION_REVISION;
        }
        if (upper.contains("CMS")) {
            return COMMISSION_INITIAL;
        }
        return ORDER;
    }
}
