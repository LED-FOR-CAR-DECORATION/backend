package com.lumintra.backend.device.infrastructure.persistence.entity;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "led_instance")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Column(name = "mac_address", nullable = false, unique = true, length = 17)
    private String macAddress;

    @Column(name = "hardware_revision", length = 50)
    private String hardwareRevision;

    @Column(name = "current_firmware_id")
    private Long currentFirmwareId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "owner_customer_id")
    private Long ownerCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LedInstanceStatus status = LedInstanceStatus.IN_STOCK;

    @Column(name = "manufactured_at")
    private Instant manufacturedAt;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "warranty_expires_at")
    private Instant warrantyExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static LedInstanceEntity createInStock(
            Long modelId,
            String serialNumber,
            String macAddress,
            String hardwareRevision,
            Instant manufacturedAt) {
        return createWithState(
                modelId,
                serialNumber,
                macAddress,
                hardwareRevision,
                null,
                null,
                LedInstanceStatus.IN_STOCK,
                manufacturedAt,
                null,
                null
        );
    }

    public static LedInstanceEntity createWithState(
            Long modelId,
            String serialNumber,
            String macAddress,
            String hardwareRevision,
            Long ownerCustomerId,
            String deviceName,
            LedInstanceStatus status,
            Instant manufacturedAt,
            Instant boundAt,
            Instant warrantyExpiresAt) {
        LedInstanceEntity entity = new LedInstanceEntity();
        entity.setModelId(modelId);
        entity.setSerialNumber(serialNumber != null ? serialNumber.strip() : null);
        entity.setMacAddress(macAddress != null ? macAddress.strip().toUpperCase(Locale.ROOT) : null);
        entity.setHardwareRevision(hardwareRevision != null && !hardwareRevision.isBlank() ? hardwareRevision.strip() : null);
        entity.setOwnerCustomerId(ownerCustomerId);
        entity.setDeviceName(deviceName != null && !deviceName.isBlank() ? deviceName.strip() : null);
        entity.setStatus(status != null ? status : LedInstanceStatus.IN_STOCK);
        entity.setManufacturedAt(manufacturedAt);
        entity.setBoundAt(boundAt);
        entity.setWarrantyExpiresAt(warrantyExpiresAt);
        return entity;
    }

    /**
     * Suppresses printable factory QR payloads once a physical unit is permanently bound
     * or decommissioned.
     */
    public boolean isQrReprintable() {
        return status != null
                && status != LedInstanceStatus.BOUND
                && status != LedInstanceStatus.DECOMMISSIONED;
    }

    /**
     * Derives hardware warranty status from claim activation and expiration timestamp.
     */
    public WarrantyStatus resolveWarrantyStatus(Instant asOf) {
        if (warrantyExpiresAt == null) {
            return WarrantyStatus.NOT_ACTIVATED;
        }
        Instant effectiveNow = asOf != null ? asOf : Instant.now();
        return effectiveNow.isAfter(warrantyExpiresAt)
                ? WarrantyStatus.EXPIRED
                : WarrantyStatus.ACTIVE;
    }

    /**
     * Permanently binds this LED hardware instance to the claiming customer and activates its warranty.
     */
    public void bindToCustomer(
            Long customerId,
            String resolvedDeviceName,
            Instant boundTimestamp,
            Instant warrantyExpiry) {
        this.ownerCustomerId = customerId;
        this.deviceName = resolvedDeviceName != null && !resolvedDeviceName.isBlank()
                ? resolvedDeviceName.strip()
                : null;
        this.status = LedInstanceStatus.BOUND;
        this.boundAt = boundTimestamp;
        this.warrantyExpiresAt = warrantyExpiry;
        this.updatedAt = boundTimestamp;
    }
}
