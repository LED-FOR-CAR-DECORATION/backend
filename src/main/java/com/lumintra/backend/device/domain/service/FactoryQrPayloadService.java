package com.lumintra.backend.device.domain.service;

/**
 * Service for generating and verifying HMAC-SHA256 signed factory QR payloads for physical LED units.
 */
public interface FactoryQrPayloadService {

    String sign(String serialNumber, String macAddress);

    String generateQrPayload(String serialNumber, String macAddress);

    boolean verifySignature(String serialNumber, String macAddress, String signature);
}
