package com.lumintra.backend.firmware.domain.service;

import com.lumintra.backend.firmware.domain.model.FirmwareException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FirmwarePolicy {
    // min_spiffs.csv: each OTA slot is 0x1e0000 bytes.
    public static final int MAX_BYTES = 0x1e0000;
    private FirmwarePolicy() {}
    public static String validate(byte[] image, String version, String hardware) {
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,31}") || !hardware.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,31}"))
            throw new FirmwareException("FIRMWARE_INVALID", "Version and hardware revision must contain 1–32 letters, digits, dots, dashes or underscores.");
        // ESP32 image header followed by first segment header and esp_app_desc_t.
        if (image.length < 288 || image.length > MAX_BYTES || (image[0] & 255) != 0xe9
                || (image[1] & 255) < 1 || (image[1] & 255) > 16 || image[12] != 0 || image[13] != 0
                || (image[32] & 255) != 0x32 || (image[33] & 255) != 0x54
                || (image[34] & 255) != 0xcd || (image[35] & 255) != 0xab)
            throw new FirmwareException("FIRMWARE_INVALID", "Upload an ESP32 application firmware.bin that fits the OTA slot; merged flash images are not supported.");
        validateEspImage(image);
        // Arduino ships an SDK descriptor version, so use retained application
        // markers rather than mistaking the ESP-IDF version for product version.
        if (!contains(image,"CARLED_FW:" + version) || !contains(image,"CARLED_HW:" + hardware))
            throw new FirmwareException("FIRMWARE_INVALID", "Version or hardware revision does not match the markers embedded in the binary.");
        return sha256(image);
    }
    private static boolean contains(byte[] image, String text) {
        byte[] marker=(text+"\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer: for(int i=0;i<=image.length-marker.length;i++) {
            for(int j=0;j<marker.length;j++) if(image[i+j]!=marker[j]) continue outer;
            return true;
        }
        return false;
    }
    private static void validateEspImage(byte[] image) {
        int offset=24, checksum=0xef;
        for(int segment=0;segment<(image[1]&255);segment++) {
            if(offset+8>image.length) throw invalidImage();
            long length=java.nio.ByteBuffer.wrap(image,offset+4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()&0xffffffffL;
            offset+=8;
            if(length>image.length-offset) throw invalidImage();
            for(int j=0;j<(int)length;j++) checksum^=image[offset+j]&255;
            offset+=(int)length;
        }
        int checksumOffset=offset+(15-offset%16);
        if(checksumOffset>=image.length || (image[checksumOffset]&255)!=checksum) throw invalidImage();
        int end=checksumOffset+1;
        if(image[23]==1) {
            if(image.length!=end+32) throw invalidImage();
            byte[] digest=HexFormat.of().parseHex(sha256(java.util.Arrays.copyOf(image,end)));
            if(!MessageDigest.isEqual(digest,java.util.Arrays.copyOfRange(image,end,image.length))) throw invalidImage();
        } else if(image[23]!=0 || image.length!=end) throw invalidImage();
    }
    private static FirmwareException invalidImage() {
        return new FirmwareException("FIRMWARE_INVALID","ESP32 image segments, checksum or image digest are invalid.");
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
}
