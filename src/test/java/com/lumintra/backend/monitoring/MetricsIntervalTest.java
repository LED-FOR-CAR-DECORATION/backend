package com.lumintra.backend.monitoring;

import com.lumintra.backend.monitoring.domain.model.MetricsInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsIntervalTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @ParameterizedTest(name = "{0} floor({1}) = {2}")
    @CsvSource({
            "1m, 2026-11-27T10:17:42Z, 2026-11-27T10:17:00Z",
            "5m, 2026-11-27T10:17:42Z, 2026-11-27T10:15:00Z",
            "5m, 2026-11-27T10:15:00Z, 2026-11-27T10:15:00Z",
            "15m, 2026-11-27T10:29:59Z, 2026-11-27T10:15:00Z",
            "15m, 2026-11-27T10:30:00Z, 2026-11-27T10:30:00Z",
            "1h, 2026-11-27T10:59:59Z, 2026-11-27T10:00:00Z",
            // 2026-11-27 23:59 in Vietnam is still the 27th there
            "1d, 2026-11-27T16:59:00Z, 2026-11-26T17:00:00Z",
            // 2026-11-28 00:00 in Vietnam starts a new day
            "1d, 2026-11-27T17:00:00Z, 2026-11-27T17:00:00Z"
    })
    @DisplayName("UTCID01 [N]: AC03 periods start on interval boundaries; 1d follows calendar days in Asia/Ho_Chi_Minh")
    void floor_alignsToPeriodStart(String code, String instant, String expected) {
        MetricsInterval interval = MetricsInterval.fromCode(code).orElseThrow();

        assertThat(interval.floor(Instant.parse(instant), VN)).isEqualTo(Instant.parse(expected));
    }

    @Test
    @DisplayName("UTCID02 [N]: AC03 next returns the start of the following period")
    void next_returnsFollowingPeriod() {
        assertThat(MetricsInterval.FIVE_MINUTES.next(Instant.parse("2026-11-27T10:15:00Z"), VN))
                .isEqualTo(Instant.parse("2026-11-27T10:20:00Z"));
        assertThat(MetricsInterval.ONE_DAY.next(Instant.parse("2026-11-26T17:00:00Z"), VN))
                .isEqualTo(Instant.parse("2026-11-27T17:00:00Z"));
    }

    @Test
    @DisplayName("UTCID03 [B]: AC02 only 1m, 5m, 15m, 1h and 1d are recognised")
    void fromCode_unknown_isEmpty() {
        assertThat(MetricsInterval.fromCode("1h")).contains(MetricsInterval.ONE_HOUR);
        assertThat(MetricsInterval.fromCode("15m")).contains(MetricsInterval.FIFTEEN_MINUTES);
        assertThat(MetricsInterval.fromCode("2h")).isEmpty();
        assertThat(MetricsInterval.fromCode(null)).isEmpty();
    }
}
