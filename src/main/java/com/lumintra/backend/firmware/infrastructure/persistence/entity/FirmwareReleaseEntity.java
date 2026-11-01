package com.lumintra.backend.firmware.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="firmware_release", uniqueConstraints=@UniqueConstraint(columnNames={"hardware_revision", "version"}))
@Getter @Setter
public class FirmwareReleaseEntity {
    @Id private String id;
    @Column(nullable=false, length=32) private String version;
    @Column(name="hardware_revision", nullable=false, length=32) private String hardwareRevision;
    @Column(nullable=false, length=64) private String sha256;
    @Column(nullable=false, columnDefinition="bytea") private byte[] binary;
    @Column(nullable=false) private int size;
    @Column(nullable=false) private boolean deprecated;
    @Column(nullable=false) private Instant createdAt;
}
