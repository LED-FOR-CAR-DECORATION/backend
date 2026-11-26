package com.lumintra.backend.device.infrastructure.security;

import com.lumintra.backend.device.domain.model.VerifiedDeviceQrPayload;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.infrastructure.config.DeviceQrProperties;
import com.lumintra.backend.shared.exception.device.InvalidDeviceQrException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates and verifies tamper-proof QR code payloads for hardware devices during factory provisioning.
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
        byte[] providedBytes = signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    @Override
    public VerifiedDeviceQrPayload parseAndVerifyQrPayload(String qrPayload) {
        if (qrPayload == null || qrPayload.isBlank()) {
            throw new InvalidDeviceQrException("Invalid or tampered device QR payload.");
        }

        Map<String, Object> fields = new CompactJsonObjectParser(qrPayload).parseObject();
        Object versionObj = fields.get("v");
        Object snObj = fields.get("sn");
        Object macObj = fields.get("mac");
        Object sigObj = fields.get("sig");

        if (!(versionObj instanceof Integer version) || version != PAYLOAD_VERSION
                || !(snObj instanceof String sn) || sn.isBlank()
                || !(macObj instanceof String mac) || mac.isBlank()
                || !(sigObj instanceof String sig) || sig.isBlank()) {
            throw new InvalidDeviceQrException("Invalid or tampered device QR payload.");
        }

        String canonicalSn = sn.strip();
        String canonicalMac = mac.strip().toUpperCase(Locale.ROOT);
        String canonicalSig = sig.strip().toLowerCase(Locale.ROOT);

        if (!verifySignature(canonicalSn, canonicalMac, canonicalSig)) {
            throw new InvalidDeviceQrException("Invalid or tampered device QR payload.");
        }

        return new VerifiedDeviceQrPayload(version, canonicalSn, canonicalMac, canonicalSig);
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

    private static final class CompactJsonObjectParser {
        private final String src;
        private int pos;

        private CompactJsonObjectParser(String src) {
            this.src = src;
        }

        private Map<String, Object> parseObject() {
            skipWhitespace();
            expectChar('{');
            skipWhitespace();

            Map<String, Object> map = new LinkedHashMap<>();
            if (peekChar() == '}') {
                pos++;
                skipWhitespace();
                if (pos != src.length()) {
                    throw invalidQr();
                }
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expectChar(':');
                skipWhitespace();
                Object value = parseValue();
                if (map.put(key, value) != null) {
                    throw invalidQr();
                }
                skipWhitespace();
                char next = peekChar();
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    break;
                } else {
                    throw invalidQr();
                }
            }

            skipWhitespace();
            if (pos != src.length()) {
                throw invalidQr();
            }
            return map;
        }

        private Object parseValue() {
            char c = peekChar();
            if (c == '"') {
                return parseString();
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseInteger();
            }
            throw invalidQr();
        }

        private String parseString() {
            expectChar('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw invalidQr();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw invalidQr();
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw invalidQr();
                            }
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw invalidQr();
                            }
                            pos += 4;
                        }
                        default -> throw invalidQr();
                    }
                } else {
                    sb.append(c);
                }
            }
            throw invalidQr();
        }

        private Integer parseInteger() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= src.length() || src.charAt(pos) < '0' || src.charAt(pos) > '9') {
                throw invalidQr();
            }
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                pos++;
            }
            try {
                return Integer.valueOf(src.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw invalidQr();
            }
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peekChar() {
            if (pos >= src.length()) {
                throw invalidQr();
            }
            return src.charAt(pos);
        }

        private void expectChar(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw invalidQr();
            }
            pos++;
        }

        private static InvalidDeviceQrException invalidQr() {
            return new InvalidDeviceQrException("Invalid or tampered device QR payload.");
        }
    }
}
