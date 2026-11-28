package com.lumintra.backend.monitoring.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;

/**
 * Period size for metrics history. Minute and hour periods are aligned to the UTC epoch, which
 * matches local boundaries in whole-hour zones such as Asia/Ho_Chi_Minh; day periods follow
 * calendar days in the given zone (BR-FL5-06).
 */
public enum MetricsInterval {
    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String code;
    private final Duration length;

    MetricsInterval(String code, Duration length) {
        this.code = code;
        this.length = length;
    }

    public String code() {
        return code;
    }

    public static Optional<MetricsInterval> fromCode(String code) {
        return Arrays.stream(values()).filter(interval -> interval.code.equals(code)).findFirst();
    }

    /** Start of the period containing {@code instant}. */
    public Instant floor(Instant instant, ZoneId zone) {
        if (this == ONE_DAY) {
            return instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        }
        long lengthSeconds = length.toSeconds();
        long epochSeconds = instant.getEpochSecond();
        return Instant.ofEpochSecond(Math.floorDiv(epochSeconds, lengthSeconds) * lengthSeconds);
    }

    /** Start of the period after the one starting at {@code periodStart}. */
    public Instant next(Instant periodStart, ZoneId zone) {
        if (this == ONE_DAY) {
            return periodStart.atZone(zone).plusDays(1).toLocalDate().atStartOfDay(zone).toInstant();
        }
        return periodStart.plus(length);
    }
}
