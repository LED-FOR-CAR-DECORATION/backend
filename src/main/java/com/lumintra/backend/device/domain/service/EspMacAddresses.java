package com.lumintra.backend.device.domain.service;

import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * MAC helpers for the ESP32 units. {@code led_instance.mac_address} holds the BLE MAC (what the mobile app sees
 * when scanning), while the firmware identifies itself with the base eFuse MAC. ESP-IDF derives the BLE MAC by
 * adding 2 to the last byte of the base MAC (wrapping within that byte), so the base MAC is recovered by
 * subtracting 2 from it.
 */
public final class EspMacAddresses {

    private static final Pattern MAC = Pattern.compile("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$");
    private static final int BLE_OFFSET = 2;

    private EspMacAddresses() {
    }

    /** Base MAC as 12 uppercase hex characters without separators, e.g. AA:BB:CC:00:00:03 becomes AABBCC000001. */
    public static String baseMacHexFromBleMac(String bleMac) {
        if (bleMac == null || !MAC.matcher(bleMac.strip()).matches()) {
            throw new IllegalArgumentException("Not a colon-separated MAC address: " + bleMac);
        }
        byte[] bytes = HexFormat.ofDelimiter(":").parseHex(bleMac.strip());
        bytes[5] = (byte) ((bytes[5] & 0xFF) - BLE_OFFSET);
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }
}
