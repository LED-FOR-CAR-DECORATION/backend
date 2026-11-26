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

        List<PathPoint> path,

        @JsonProperty("cancel_comment")
        String cancelComment
) {
    private static final Pattern STOP_SUFFIX_PATTERN = Pattern.compile("-\\d+$");

    public AhamoveWebhookRequest(
            String orderId,
            String status,
            String subStatus,
            String sharedLink,
            BigDecimal totalFee,
            BigDecimal totalPay,
            BigDecimal completeTime,
            List<PathPoint> path) {
        this(orderId, status, subStatus, sharedLink, totalFee, totalPay, completeTime, path, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PathPoint(
            String status,
            @JsonProperty("fail_comment")
            String failComment
    ) {
        public PathPoint(String status) {
            this(status, null);
        }
    }

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

    public String resolvedFailureReason() {
        if (cancelComment != null && !cancelComment.isBlank()) {
            String trimmed = cancelComment.trim();
            return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
        }
        if (path != null) {
            for (PathPoint point : path) {
                if (point != null && point.failComment() != null && !point.failComment().isBlank()) {
                    String trimmed = point.failComment().trim();
                    return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
                }
            }
        }
        if (subStatus != null && !subStatus.isBlank()) {
            return "AhaMove: " + subStatus.trim();
        }
        if (status != null && !status.isBlank()) {
            return "AhaMove: " + status.trim();
        }
        return "AhaMove delivery failed";
    }
}
