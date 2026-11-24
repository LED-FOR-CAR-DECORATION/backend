package com.lumintra.backend.device.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EspMacAddressesTest {

    @Test
    @DisplayName("DEV-22 MAC UTCID01 [N]: BLE MAC minus 2 in the last byte, as 12 uppercase hex characters")
    void baseMac_subtractsBleOffset() {
        assertThat(EspMacAddresses.baseMacHexFromBleMac("AA:BB:CC:11:22:03")).isEqualTo("AABBCC112201");
        assertThat(EspMacAddresses.baseMacHexFromBleMac(" 24:6f:28:a1:b2:c6 ")).isEqualTo("246F28A1B2C4");
    }

    @Test
    @DisplayName("DEV-22 MAC UTCID02 [B]: Last byte wraps within itself like ESP-IDF (00 becomes FE, 01 becomes FF)")
    void baseMac_wrapsWithinLastByte() {
        assertThat(EspMacAddresses.baseMacHexFromBleMac("AA:BB:CC:11:22:00")).isEqualTo("AABBCC1122FE");
        assertThat(EspMacAddresses.baseMacHexFromBleMac("AA:BB:CC:11:22:01")).isEqualTo("AABBCC1122FF");
    }

    @Test
    @DisplayName("DEV-22 MAC UTCID03 [A]: A value that is not a colon-separated MAC is rejected")
    void baseMac_rejectsMalformed() {
        for (String bad : new String[]{null, "", "AABBCC112203", "AA:BB:CC:11:22", "GG:BB:CC:11:22:03"}) {
            assertThatThrownBy(() -> EspMacAddresses.baseMacHexFromBleMac(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
