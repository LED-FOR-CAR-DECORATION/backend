package com.lumintra.backend.firmware.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="firmware_channel") @Getter @Setter
public class FirmwareChannelEntity {
    @Id @Column(length=32) private String hardwareRevision;
    @Column(nullable=false, length=36) private String releaseId;
    @Version private long revision;
}
