package com.lumintra.backend.commerce.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveWebhookRequest(
        @NotBlank(message = "_id must not be blank")
        @JsonProperty("_id")
        String orderId,

        @NotBlank(message = "status must not be blank")
        String status,

        @JsonProperty("sub_status")
        String subStatus,

        @JsonProperty("shared_link")
        String sharedLink,

        @JsonProperty("total_fee")
        BigDecimal totalFee,

        @JsonProperty("total_pay")
        BigDecimal totalPay,

        @JsonProperty("complete_time")
        BigDecimal completeTime,

        List<PathPoint> path
) {
    private static final Pattern STOP_SUFFIX_PATTERN = Pattern.compile("-\\d+$");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PathPoint(
            String status
    ) {}

    public String rawTrackingNumber() {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        return orderId.trim();
    }

    public String baseTrackingNumber() {
        String raw = rawTrackingNumber();
        if (raw == null) {
            return null;
        }
        String normalized = STOP_SUFFIX_PATTERN.matcher(raw).replaceFirst("");
        return normalized.isBlank() ? raw : normalized;
    }

    public boolean hasStopIndexSuffix() {
        String raw = rawTrackingNumber();
        return raw != null && STOP_SUFFIX_PATTERN.matcher(raw).find();
    }

    public String normalizedTrackingUrl() {
        if (sharedLink == null || sharedLink.isBlank()) {
            return null;
        }
        return sharedLink.trim();
    }

    public BigDecimal resolvedCarrierFee() {
        if (totalFee != null && totalFee.compareTo(BigDecimal.ZERO) >= 0) {
            return totalFee;
        }
        if (totalPay != null && totalPay.compareTo(BigDecimal.ZERO) >= 0) {
            return totalPay;
        }
        return null;
    }
}
