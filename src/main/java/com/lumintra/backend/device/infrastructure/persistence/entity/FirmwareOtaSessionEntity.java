package com.lumintra.backend.device.infrastructure.persistence.entity;

import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One-time OTA download session (DEV-21): the device presents the plain token to fetch the manifest
 * and binary (DEV-22/23). Only the SHA-256 of the token is stored.
 */
@Entity
@Table(
        name = "firmware_ota_session",
        indexes = {
                @Index(name = "uk_firmware_ota_session_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_firmware_ota_session_instance_status", columnList = "led_instance_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FirmwareOtaSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    /** Lowercase hex SHA-256 of the 64-hex-character token; the token itself is never stored. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "led_instance_id", nullable = false)
    private Long ledInstanceId;

    @Column(name = "firmware_id", nullable = false)
    private Long firmwareId;

    /** Nonce the device returned from {@code get_ota_info}; echoed in the manifest for replay protection. */
    @Column(name = "nonce", nullable = false, length = 32)
    private String nonce;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OtaSessionStatus status = OtaSessionStatus.ISSUED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** First manifest fetch by the device (DEV-22). */
    @Column(name = "manifest_fetched_at")
    private Instant manifestFetchedAt;

    /** Binary download attempts by the device (DEV-23). */
    @Column(name = "download_attempts", nullable = false)
    private int downloadAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static FirmwareOtaSessionEntity issue(
            String tokenHash, Long ledInstanceId, Long firmwareId, String nonce, Instant expiresAt) {
        FirmwareOtaSessionEntity session = new FirmwareOtaSessionEntity();
        session.tokenHash = tokenHash;
        session.ledInstanceId = ledInstanceId;
        session.firmwareId = firmwareId;
        session.nonce = nonce;
        session.expiresAt = expiresAt;
        session.status = OtaSessionStatus.ISSUED;
        return session;
    }

    /** Records the first manifest fetch (DEV-22); returns true when this call was the first. */
    public boolean markManifestFetched(Instant at) {
        if (manifestFetchedAt != null) {
            return false;
        }
        manifestFetchedAt = at;
        return true;
    }

    public void revoke() {
        this.status = OtaSessionStatus.REVOKED;
    }

    /** Test/support hook; ids are normally assigned by the database. */
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
