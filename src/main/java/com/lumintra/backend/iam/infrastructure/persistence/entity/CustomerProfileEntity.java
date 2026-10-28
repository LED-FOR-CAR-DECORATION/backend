// src/main/java/com/lumintra/backend/iam/infrastructure/persistence/entity/CustomerProfileEntity.java
package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "customer_profile")
@Getter
@Setter
public class CustomerProfileEntity {
    public static CustomerProfileEntity forAccount(UserAccountEntity account) {
        return forAccount(account, null);
    }

    public static CustomerProfileEntity forAccount(UserAccountEntity account, String fullName) {
        CustomerProfileEntity profile = new CustomerProfileEntity();
        profile.setAccount(account);
        profile.setFullName(fullName);
        return profile;
    }
    @Id
    private Long accountId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Shares the PK with UserAccountEntity
    @JoinColumn(name = "account_id")
    private UserAccountEntity account;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
