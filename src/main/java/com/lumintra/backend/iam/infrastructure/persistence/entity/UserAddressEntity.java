package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_address")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "full_address", nullable = false, length = 255)
    private String fullAddress;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static UserAddressEntity create(
            Long customerId,
            String recipientName,
            String recipientPhone,
            String fullAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean isDefault) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setCustomerId(customerId);
        entity.setRecipientName(recipientName);
        entity.setRecipientPhone(recipientPhone);
        entity.setFullAddress(fullAddress);
        entity.setLatitude(latitude);
        entity.setLongitude(longitude);
        entity.setDefault(isDefault);
        return entity;
    }

    public static UserAddressEntity of(
            Long addressId,
            Long customerId,
            String recipientName,
            String recipientPhone,
            String fullAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean isDefault) {
        UserAddressEntity entity = create(
                customerId,
                recipientName,
                recipientPhone,
                fullAddress,
                latitude,
                longitude,
                isDefault
        );
        entity.setAddressId(addressId);
        return entity;
    }

    public void updateDetails(
            String recipientName,
            String recipientPhone,
            String fullAddress,
            BigDecimal latitude,
            BigDecimal longitude) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void softDelete(Instant deletedAt) {
        this.isDeleted = true;
        this.deletedAt = deletedAt != null ? deletedAt : Instant.now();
        this.isDefault = false;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && Objects.equals(this.customerId, userId);
    }
}
