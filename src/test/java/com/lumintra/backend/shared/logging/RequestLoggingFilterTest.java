package com.lumintra.backend.shared.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    @Test
    @DisplayName("DEV-23 Logging [N]: OTA download tokens in the path are masked; other paths are unchanged")
    void maskSensitivePath() {
        String token = "ab".repeat(32);
        assertThat(RequestLoggingFilter.maskSensitivePath("/api/firmware/downloads/" + token + "/binary"))
                .isEqualTo("/api/firmware/downloads/***/binary");
        assertThat(RequestLoggingFilter.maskSensitivePath("/api/firmware/downloads/" + token + "/manifest"))
                .isEqualTo("/api/firmware/downloads/***/manifest")
                .doesNotContain(token);
        assertThat(RequestLoggingFilter.maskSensitivePath("/api/devices/2001/ota/sessions"))
                .isEqualTo("/api/devices/2001/ota/sessions");
        assertThat(RequestLoggingFilter.maskSensitivePath(null)).isNull();
    }
}
