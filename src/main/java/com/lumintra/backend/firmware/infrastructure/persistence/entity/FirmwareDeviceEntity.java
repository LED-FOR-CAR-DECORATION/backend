package com.lumintra.backend.firmware.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="firmware_device") @Getter @Setter
public class FirmwareDeviceEntity {
    @Id @Column(length=12) private String id;
    @Column(nullable=false) private Long ownerAccountId;
    @Column(nullable=false, length=32) private String hardwareRevision;
    @Version private long revision;
}
