package com.lumintra.backend.device.infrastructure.security;

import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.infrastructure.config.DeviceQrProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Generates and verifies tamper-proof QR code payloads for hardware devices during factory provisioning
 * Signs and verifies compact factory QR payloads {@code {"v":1,"sn":...,"mac":...,"sig":...}}
 * using lowercase hexadecimal HMAC-SHA256 over canonical input {@code "v1|" + sn + "|" + mac}.
 */
@Service
@EnableConfigurationProperties(DeviceQrProperties.class)
public class HmacFactoryQrPayloadService implements FactoryQrPayloadService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int PAYLOAD_VERSION = 1;
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] secretKeyBytes;

    @Autowired
    public HmacFactoryQrPayloadService(DeviceQrProperties properties) {
        this(properties != null ? properties.factoryQrSecret() : null);
    }

    public HmacFactoryQrPayloadService(String factoryQrSecret) {
        if (factoryQrSecret == null || factoryQrSecret.isBlank()) {
            throw new IllegalStateException("app.device.factory-qr-secret must be configured and non-blank");
        }
        this.secretKeyBytes = factoryQrSecret.getBytes(StandardCharsets.UTF_8);
    }

    /*
    Normalizes inputs, format accepted data, hash data using HmacSHA256 using .env secret
     */
    @Override
    public String sign(String serialNumber, String macAddress) {
        String canonicalSn = normalizeSerialNumber(serialNumber);
        String canonicalMac = normalizeMacAddress(macAddress);
        String canonicalMessage = "v" + PAYLOAD_VERSION + "|" + canonicalSn + "|" + canonicalMac;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(canonicalMessage.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 QR signature", ex);
        }
    }

    @Override
    public String generateQrPayload(String serialNumber, String macAddress) {
        String canonicalSn = normalizeSerialNumber(serialNumber);
        String canonicalMac = normalizeMacAddress(macAddress);
        String signature = sign(canonicalSn, canonicalMac);

        return "{\"v\":" + PAYLOAD_VERSION
                + ",\"sn\":\"" + escapeJsonString(canonicalSn) + "\""
                + ",\"mac\":\"" + escapeJsonString(canonicalMac) + "\""
                + ",\"sig\":\"" + signature + "\"}";
    }

    @Override
    public boolean verifySignature(String serialNumber, String macAddress, String signature) {
        if (serialNumber == null || serialNumber.isBlank()
                || macAddress == null || macAddress.isBlank()
                || signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(serialNumber, macAddress);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = signature.strip().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    private static String normalizeSerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }
        return serialNumber.strip();
    }

    private static String normalizeMacAddress(String macAddress) {
        if (macAddress == null || macAddress.isBlank()) {
            throw new IllegalArgumentException("macAddress must not be blank");
        }
        return macAddress.strip().toUpperCase(Locale.ROOT);
    }

    private static String escapeJsonString(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
