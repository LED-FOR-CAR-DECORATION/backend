package com.lumintra.backend.firmware.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="firmware_session") @Getter @Setter
public class FirmwareSessionEntity {
    @Id @Column(length=64) private String tokenHash;
    @Column(nullable=false, length=12) private String deviceId;
    @Column(nullable=false) private Long ownerAccountId;
    @Column(nullable=false, length=36) private String releaseId;
    @Column(nullable=false, length=32) private String nonce;
    @Column(nullable=false) private Instant expiresAt;
}
