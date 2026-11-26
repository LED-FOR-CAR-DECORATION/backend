package com.lumintra.backend.device.infrastructure.persistence.entity;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A firmware binary release for one LED model (Flow 4). The binary itself lives in private cloud
 * storage under {@code storageObjectKey}; this row holds its metadata and lifecycle status.
 */
@Entity
@Table(
        name = "firmware_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_firmware_version_model_version",
                columnNames = {"model_id", "version"}),
        indexes = @Index(name = "idx_firmware_version_model_status", columnList = "model_id, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FirmwareVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "firmware_id")
    private Long firmwareId;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "changelog", columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "storage_object_key", nullable = false, length = 512)
    private String storageObjectKey;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FirmwareStatus status = FirmwareStatus.ACTIVE;

    /** Employee id of the ADMIN who uploaded the release. */
    @Column(name = "uploaded_by_employee_id")
    private Long uploadedByEmployeeId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @Column(name = "deprecated_by_employee_id")
    private Long deprecatedByEmployeeId;

    @Column(name = "deprecation_reason", length = 1000)
    private String deprecationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Moves a previously ACTIVE release aside when a newer one is uploaded. */
    public void supersede() {
        this.status = FirmwareStatus.SUPERSEDED;
    }

    /** Withdraws a faulty release for good (BR-FL4-05); the row and the stored binary are kept. */
    public void deprecate(Long employeeId, String reason, Instant at) {
        this.status = FirmwareStatus.DEPRECATED;
        this.deprecatedByEmployeeId = employeeId;
        this.deprecationReason = reason;
        this.deprecatedAt = at;
    }

    /** Rolls a model back to this previously SUPERSEDED release. */
    public void reactivate() {
        this.status = FirmwareStatus.ACTIVE;
    }

    public static FirmwareVersionEntity create(
            Long modelId,
            String version,
            String changelog,
            String storageObjectKey,
            long fileSizeBytes,
            String sha256,
            FirmwareStatus status,
            Long uploadedByEmployeeId,
            Instant uploadedAt) {
        FirmwareVersionEntity entity = new FirmwareVersionEntity();
        entity.modelId = modelId;
        entity.version = version;
        entity.changelog = changelog;
        entity.storageObjectKey = storageObjectKey;
        entity.fileSizeBytes = fileSizeBytes;
        entity.sha256 = sha256;
        entity.status = status != null ? status : FirmwareStatus.ACTIVE;
        entity.uploadedByEmployeeId = uploadedByEmployeeId;
        entity.uploadedAt = uploadedAt;
        return entity;
    }
}
