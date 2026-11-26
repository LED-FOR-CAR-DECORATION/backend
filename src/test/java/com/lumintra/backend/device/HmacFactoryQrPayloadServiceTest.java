package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.VerifiedDeviceQrPayload;
import com.lumintra.backend.device.infrastructure.config.DeviceQrProperties;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.shared.exception.device.InvalidDeviceQrException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacFactoryQrPayloadServiceTest {

    private static final String TEST_SECRET = "unit-test-factory-qr-secret-2026";
    private final HmacFactoryQrPayloadService service =
            new HmacFactoryQrPayloadService(new DeviceQrProperties(TEST_SECRET));

    @Test
    @DisplayName("DEV-173 QR-01 [N]: Signs canonical 'v1|sn|mac' with lowercase hex HMAC-SHA256 and normalizes inputs")
    void generateQrPayload_validInputs_producesExpectedSignedCompactJson() throws Exception {
        String serial = "  LMT-64X64-0001 ";
        String mac = " aa:bb:cc:11:22:ff ";

        Mac jcaMac = Mac.getInstance("HmacSHA256");
        jcaMac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expectedSig = HexFormat.of().formatHex(
                jcaMac.doFinal("v1|LMT-64X64-0001|AA:BB:CC:11:22:FF".getBytes(StandardCharsets.UTF_8)));

        String sig = service.sign(serial, mac);
        String payload = service.generateQrPayload(serial, mac);

        assertThat(sig).isEqualTo(expectedSig).matches("^[0-9a-f]{64}$");
        assertThat(payload).isEqualTo(
                "{\"v\":1,\"sn\":\"LMT-64X64-0001\",\"mac\":\"AA:BB:CC:11:22:FF\",\"sig\":\"" + expectedSig + "\"}"
        );
        assertThat(service.verifySignature("LMT-64X64-0001", "AA:BB:CC:11:22:FF", expectedSig)).isTrue();
    }

    @Test
    @DisplayName("DEV-173 QR-02 [B/A]: verifySignature rejects tampered identifiers, wrong digest, or blank values")
    void verifySignature_invalidOrTamperedInputs_returnsFalse() {
        String sig = service.sign("LMT-64X64-0001", "AA:BB:CC:11:22:01");

        assertThat(service.verifySignature("LMT-64X64-0002", "AA:BB:CC:11:22:01", sig)).isFalse();
        assertThat(service.verifySignature("LMT-64X64-0001", "AA:BB:CC:11:22:02", sig)).isFalse();
        assertThat(service.verifySignature("LMT-64X64-0001", "AA:BB:CC:11:22:01", sig + "0")).isFalse();
        assertThat(service.verifySignature("LMT-64X64-0001", "AA:BB:CC:11:22:01", "")).isFalse();
        assertThat(service.verifySignature(null, "AA:BB:CC:11:22:01", sig)).isFalse();
        assertThat(service.verifySignature("LMT-64X64-0001", null, sig)).isFalse();
        assertThat(service.verifySignature("LMT-64X64-0001", "AA:BB:CC:11:22:01", null)).isFalse();
    }

    @Test
    @DisplayName("DEV-173 QR-03 [B]: Escapes special JSON characters and rejects blank secret or identifiers")
    void constructorAndGeneration_invalidArgs_handledProperly() {
        String escapedPayload = service.generateQrPayload("SN-\"QUOTE\"\\1", "AA:BB:CC:11:22:01");
        assertThat(escapedPayload).contains("\"sn\":\"SN-\\\"QUOTE\\\"\\\\1\"");

        assertThatThrownBy(() -> new HmacFactoryQrPayloadService((String) null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HmacFactoryQrPayloadService("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.sign(" ", "AA:BB:CC:11:22:01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sign("SN-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DEV-93 QR-04 [N/B]: parseAndVerifyQrPayload accepts valid compact or whitespace-formatted QR JSON and normalizes MAC")
    void parseAndVerifyQrPayload_validPayload_returnsVerifiedFields() {
        String sig = service.sign("LMT-64X64-0005", "AA:BB:CC:11:22:05");
        String compact = service.generateQrPayload("LMT-64X64-0005", "aa:bb:cc:11:22:05");

        VerifiedDeviceQrPayload verified = service.parseAndVerifyQrPayload(compact);
        assertThat(verified.version()).isEqualTo(1);
        assertThat(verified.serialNumber()).isEqualTo("LMT-64X64-0005");
        assertThat(verified.macAddress()).isEqualTo("AA:BB:CC:11:22:05");
        assertThat(verified.signature()).isEqualTo(sig);

        String formatted = " { \"mac\" : \"aa:bb:cc:11:22:05\" , \"v\" : 1 , \"sn\" : \"LMT-64X64-0005\" , \"sig\" : \""
                + sig.toUpperCase() + "\" } ";
        VerifiedDeviceQrPayload verifiedFormatted = service.parseAndVerifyQrPayload(formatted);
        assertThat(verifiedFormatted.serialNumber()).isEqualTo("LMT-64X64-0005");
        assertThat(verifiedFormatted.macAddress()).isEqualTo("AA:BB:CC:11:22:05");
        assertThat(verifiedFormatted.signature()).isEqualTo(sig);
    }

    @Test
    @DisplayName("DEV-93 QR-05 [A]: parseAndVerifyQrPayload rejects tampered signature, unsupported version, duplicate keys, or malformed JSON")
    void parseAndVerifyQrPayload_invalidOrTampered_throwsInvalidDeviceQrException() {
        String validSig = service.sign("LMT-64X64-0005", "AA:BB:CC:11:22:05");

        assertThatThrownBy(() -> service.parseAndVerifyQrPayload(null))
                .isInstanceOf(InvalidDeviceQrException.class);
        assertThatThrownBy(() -> service.parseAndVerifyQrPayload("   "))
                .isInstanceOf(InvalidDeviceQrException.class);
        assertThatThrownBy(() -> service.parseAndVerifyQrPayload("not-a-json"))
                .isInstanceOf(InvalidDeviceQrException.class);
        assertThatThrownBy(() -> service.parseAndVerifyQrPayload(
                "{\"v\":2,\"sn\":\"LMT-64X64-0005\",\"mac\":\"AA:BB:CC:11:22:05\",\"sig\":\"" + validSig + "\"}"))
                .isInstanceOf(InvalidDeviceQrException.class);
        assertThatThrownBy(() -> service.parseAndVerifyQrPayload(
                "{\"v\":1,\"sn\":\"LMT-64X64-9999\",\"mac\":\"AA:BB:CC:11:22:05\",\"sig\":\"" + validSig + "\"}"))
                .isInstanceOf(InvalidDeviceQrException.class);
        assertThatThrownBy(() -> service.parseAndVerifyQrPayload(
                "{\"v\":1,\"sn\":\"LMT-64X64-0005\",\"sn\":\"DUP\",\"mac\":\"AA:BB:CC:11:22:05\",\"sig\":\"" + validSig + "\"}"))
                .isInstanceOf(InvalidDeviceQrException.class);
    }
}
