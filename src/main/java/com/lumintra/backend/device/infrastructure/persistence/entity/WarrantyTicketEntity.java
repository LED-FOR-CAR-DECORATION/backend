package com.lumintra.backend.device.infrastructure.persistence.entity;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.shared.exception.device.InvalidWarrantyTransitionException;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warranty_ticket")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarrantyTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "device_serial_number", nullable = false, length = 100)
    private String deviceSerialNumber;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_media_urls", columnDefinition = "jsonb")
    private List<String> evidenceMediaUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WarrantyTicketStatus status = WarrantyTicketStatus.PENDING;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static WarrantyTicketEntity createPending(
            Long customerId,
            Long orderItemId,
            String deviceSerialNumber,
            String issueDescription,
            List<String> evidenceMediaUrls) {
        WarrantyTicketEntity entity = new WarrantyTicketEntity();
        entity.setCustomerId(customerId);
        entity.setOrderItemId(orderItemId);
        entity.setDeviceSerialNumber(deviceSerialNumber != null ? deviceSerialNumber.strip() : null);
        entity.setIssueDescription(issueDescription != null ? issueDescription.strip() : null);
        entity.setEvidenceMediaUrls(evidenceMediaUrls != null ? new ArrayList<>(evidenceMediaUrls) : new ArrayList<>());
        entity.setStatus(WarrantyTicketStatus.PENDING);
        return entity;
    }

    public void approve(String notes) {
        this.status = WarrantyTicketStatus.APPROVED;
        this.resolutionNotes = notes != null && !notes.isBlank() ? notes.strip() : null;
    }

    public void reject(String notes) {
        this.status = WarrantyTicketStatus.REJECTED;
        this.resolutionNotes = notes != null && !notes.isBlank() ? notes.strip() : null;
    }

    public void complete(String notes) {
        this.status = WarrantyTicketStatus.COMPLETED;
        this.resolutionNotes = notes != null && !notes.isBlank() ? notes.strip() : null;
    }

    public void process(WarrantyTicketStatus targetStatus, String notes) {
        if (targetStatus == null || !this.status.canTransitionTo(targetStatus)) {
            throw new InvalidWarrantyTransitionException(
                    "Cannot transition warranty claim from " + this.status + " to " + targetStatus + "."
            );
        }
        if ((targetStatus == WarrantyTicketStatus.REJECTED || targetStatus == WarrantyTicketStatus.COMPLETED)
                && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("resolutionNotes is required when transitioning to " + targetStatus);
        }
        this.status = targetStatus;
        this.resolutionNotes = notes != null && !notes.isBlank() ? notes.strip() : null;
    }

    public boolean isActive() {
        return status == WarrantyTicketStatus.PENDING || status == WarrantyTicketStatus.APPROVED;
    }
}
