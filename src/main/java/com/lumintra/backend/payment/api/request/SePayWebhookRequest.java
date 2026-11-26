package com.lumintra.backend.payment.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Schema(description = "Inbound SePay bank transfer notification webhook payload")
@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayWebhookRequest(
        @Schema(description = "Unique SePay transaction ID (sepay_tx_id) used for webhook idempotency (0 may be sent by SePay dashboard test pings)", example = "92704", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "id must not be null")
        @PositiveOrZero(message = "id must be zero or a positive identifier")
        Long id,

        @Schema(description = "Receiving bank short name reported by SePay", example = "TPBank", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String gateway,

        @Schema(description = "Bank transaction timestamp string (yyyy-MM-dd HH:mm:ss or ISO-8601)", example = "2026-11-12 23:05:12", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String transactionDate,

        @Schema(description = "Beneficiary bank account number", example = "10006140844", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String accountNumber,

        @Schema(description = "Auto-detected payment code extracted by SePay (if configured)", example = "LMTORD102A1B2C3D4", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String code,

        @Schema(description = "Bank transfer memo content", example = "LMTORD102A1B2C3D4", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String content,

        @Schema(description = "Transfer direction ('in' for incoming deposit, 'out' for withdrawal)", example = "in", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "transferType must not be blank")
        String transferType,

        @Schema(description = "Transferred amount in VND", example = "250000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "transferAmount must not be null")
        @Positive(message = "transferAmount must be positive")
        BigDecimal transferAmount,

        @Schema(description = "Cumulative bank account balance after transaction", example = "1250000", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BigDecimal accumulated,

        @Schema(description = "Virtual sub-account number if used", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String subAccount,

        @Schema(description = "Bank SMS/transaction reference code", example = "MBVCB.3278907687", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String referenceCode,

        @Schema(description = "Full raw SMS/bank transfer narrative", example = "TK 10006140844 +250,000 VND luc 2026-11-12 23:05:12. ND: LMTORD102A1B2C3D4", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description
) {
    private static final DateTimeFormatter SEPAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public boolean isIncomingTransfer() {
        return transferType != null && "in".equalsIgnoreCase(transferType.trim());
    }

    public String normalizedCode() {
        return trimToNull(code);
    }

    public String normalizedContent() {
        return trimToNull(content);
    }

    public String normalizedDescription() {
        return trimToNull(description);
    }

    public String normalizedReferenceCode() {
        return trimToNull(referenceCode);
    }

    public Instant resolvedPaidAt(Instant fallback) {
        String rawDate = trimToNull(transactionDate);
        if (rawDate == null) {
            return fallback;
        }
        try {
            return Instant.parse(rawDate);
        } catch (DateTimeParseException ignored) {
            // Fall through to SePay local timestamp format
        }
        try {
            return LocalDateTime.parse(rawDate, SEPAY_DATE_FORMATTER)
                    .atZone(VIETNAM_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
