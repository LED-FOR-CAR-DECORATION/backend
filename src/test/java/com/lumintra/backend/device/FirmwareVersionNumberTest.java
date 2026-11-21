package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.FirmwareVersionNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmwareVersionNumberTest {

    @Test
    @DisplayName("DEV-12 Version [N]: Parses MAJOR.MINOR.PATCH and orders numerically, not lexically")
    void parse_andCompare_numerically() {
        assertThat(FirmwareVersionNumber.parse("1.10.0")).isGreaterThan(FirmwareVersionNumber.parse("1.9.9"));
        assertThat(FirmwareVersionNumber.parse("2.0.0")).isGreaterThan(FirmwareVersionNumber.parse("1.99.99"));
        assertThat(FirmwareVersionNumber.parse(" 0.0.1 ")).isEqualByComparingTo(new FirmwareVersionNumber(0, 0, 1));
        assertThat(FirmwareVersionNumber.parse("999999.0.0").toString()).isEqualTo("999999.0.0");
    }

    @Test
    @DisplayName("DEV-12 Version [A]: Rejects leading zeros, missing parts, suffixes and oversized parts")
    void parse_invalid_throws() {
        for (String raw : new String[]{null, "", "1.2", "1.2.3.4", "01.2.3", "1.2.3-beta", "v1.2.3", "1000000.0.0"}) {
            assertThatThrownBy(() -> FirmwareVersionNumber.parse(raw)).isInstanceOf(IllegalArgumentException.class);
            assertThat(FirmwareVersionNumber.tryParse(raw)).isNull();
        }
    }
}
