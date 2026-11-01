package com.lumintra.backend.firmware.application;

import com.lumintra.backend.firmware.api.request.FirmwareRequests;
import com.lumintra.backend.firmware.api.response.FirmwareResponses.*;
import com.lumintra.backend.firmware.domain.model.FirmwareException;
import com.lumintra.backend.firmware.domain.service.FirmwarePolicy;
import com.lumintra.backend.firmware.infrastructure.persistence.entity.*;
import com.lumintra.backend.firmware.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.application.AccountLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class FirmwareUseCases {
    private final FirmwareReleaseRepository releases;
    private final FirmwareDeviceRepository devices;
    private final FirmwareChannelRepository channels;
    private final FirmwareSessionRepository sessions;
    private final AccountLookup accounts;

    @Transactional
    public Release upload(byte[] image, String filename, String version, String hardware) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".bin"))
            throw new FirmwareException("FIRMWARE_INVALID", "Only .bin application images are supported.");
        String hash = FirmwarePolicy.validate(image, version, hardware);
        var release = new FirmwareReleaseEntity();
        release.setId(UUID.randomUUID().toString()); release.setVersion(version); release.setHardwareRevision(hardware);
        release.setBinary(image); release.setSha256(hash); release.setSize(image.length); release.setCreatedAt(Instant.now());
        releases.saveAndFlush(release);
        return response(release);
    }
    @Transactional(readOnly=true)
    public List<Release> list() { return releases.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList(); }
    @Transactional
    public Release activate(String id) {
        var release = release(id);
        if (release.isDeprecated()) throw new FirmwareException("FIRMWARE_REVOKED", "A deprecated release cannot be activated.");
        var channel = channels.findById(release.getHardwareRevision()).orElseGet(FirmwareChannelEntity::new);
        channel.setHardwareRevision(release.getHardwareRevision()); channel.setReleaseId(id); channels.saveAndFlush(channel);
        return response(release);
    }
    @Transactional
    public Release deprecate(String id) {
        var release = release(id); release.setDeprecated(true); releases.saveAndFlush(release);
        channels.findById(release.getHardwareRevision()).filter(c -> c.getReleaseId().equals(id)).ifPresent(channels::delete);
        return response(release);
    }
    @Transactional
    public Device provision(FirmwareRequests.Device request) {
        if (!accounts.isActive(request.ownerAccountId()))
            throw new FirmwareException("FIRMWARE_INVALID", "The owner account must exist and be active.");
        if (devices.existsById(request.deviceId()))
            throw new FirmwareException("FIRMWARE_CONFLICT", "Device is already assigned. Ownership transfers require a separate verified process.");
        var device = new FirmwareDeviceEntity(); device.setId(request.deviceId());
        device.setOwnerAccountId(request.ownerAccountId()); device.setHardwareRevision(request.hardwareRevision());
        devices.saveAndFlush(device);
        return new Device(device.getId(), device.getOwnerAccountId(), device.getHardwareRevision());
    }
    @Transactional(readOnly=true)
    public Check check(String id, Long owner, String currentVersion) {
        var device = owned(id, owner);
        var release = active(device);
        return new Check(release != null && !release.getVersion().equals(currentVersion), release == null ? null : response(release));
    }
    @Transactional
    public Session begin(String id, Long owner, FirmwareRequests.Session request) {
        var device = owned(id, owner); var release = active(device);
        if (release == null || release.getVersion().equals(request.currentVersion()))
            throw new FirmwareException("FIRMWARE_UNAVAILABLE", "No compatible update is available.");
        byte[] secret = new byte[32]; new java.security.SecureRandom().nextBytes(secret);
        String token = HexFormat.of().formatHex(secret);
        var session = new FirmwareSessionEntity(); session.setTokenHash(hashToken(token));
        session.setDeviceId(id); session.setOwnerAccountId(owner); session.setReleaseId(release.getId());
        session.setNonce(request.nonce()); session.setExpiresAt(Instant.now().plusSeconds(900)); sessions.save(session);
        return new Session(token, release.getVersion(), session.getExpiresAt());
    }
    @Transactional(readOnly=true)
    public Manifest manifest(String token) {
        var session = validSession(token); var release = release(session.getReleaseId());
        return new Manifest(session.getDeviceId(), session.getNonce(), release.getVersion(), release.getHardwareRevision(), release.getSha256(), release.getSize());
    }
    @Transactional(readOnly=true)
    public Binary binary(String token) {
        var session = validSession(token);
        return new Binary(release(session.getReleaseId()).getBinary());
    }
    private FirmwareSessionEntity validSession(String token) {
        if (!token.matches("[0-9a-f]{64}")) throw expired();
        var session = sessions.findById(hashToken(token)).orElseThrow(FirmwareUseCases::expired);
        if (!session.getExpiresAt().isAfter(Instant.now())) throw expired();
        var device = devices.findById(session.getDeviceId()).orElseThrow(FirmwareUseCases::expired);
        var release = release(session.getReleaseId());
        if (!device.getOwnerAccountId().equals(session.getOwnerAccountId()) || !accounts.isActive(session.getOwnerAccountId())
                || release.isDeprecated() || !device.getHardwareRevision().equals(release.getHardwareRevision())) throw expired();
        return session;
    }
    private FirmwareDeviceEntity owned(String id, Long owner) {
        var device = devices.findById(id).orElseThrow(() -> new FirmwareException("FIRMWARE_DEVICE_NOT_FOUND", "Device is not registered."));
        if (!device.getOwnerAccountId().equals(owner)) throw new FirmwareException("FIRMWARE_FORBIDDEN", "You do not own this device.");
        return device;
    }
    private FirmwareReleaseEntity active(FirmwareDeviceEntity device) {
        return channels.findById(device.getHardwareRevision()).map(c -> release(c.getReleaseId())).filter(r -> !r.isDeprecated()).orElse(null);
    }
    private FirmwareReleaseEntity release(String id) {
        return releases.findById(id).orElseThrow(() -> new FirmwareException("FIRMWARE_NOT_FOUND", "Firmware release was not found."));
    }
    private Release response(FirmwareReleaseEntity r) {
        boolean active = channels.findById(r.getHardwareRevision()).map(c -> c.getReleaseId().equals(r.getId())).orElse(false);
        return new Release(r.getId(),r.getVersion(),r.getHardwareRevision(),r.getSha256(),r.getSize(),r.isDeprecated(),active,r.getCreatedAt());
    }
    private static String hashToken(String token) { return FirmwarePolicy.sha256(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
    private static FirmwareException expired() { return new FirmwareException("FIRMWARE_SESSION_INVALID", "Update authorization expired, was revoked, or is invalid. Start a new update."); }
}
