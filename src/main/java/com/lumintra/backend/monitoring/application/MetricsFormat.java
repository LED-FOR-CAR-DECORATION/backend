package com.lumintra.backend.monitoring.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Shared output formatting for monitoring responses (MON-02/MON-03 AC08). */
final class MetricsFormat {

    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private MetricsFormat() {
    }

    static OffsetDateTime toDisplayTime(Instant instant) {
        return instant.atZone(DISPLAY_ZONE).toOffsetDateTime();
    }

    /** Rounds half-up; {@code null}, NaN and infinite values become {@code null}. */
    static Double round(Double value, int decimals) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    static double roundOrZero(double value, int decimals) {
        Double rounded = round(value, decimals);
        return rounded == null ? 0.0 : rounded;
    }
}
