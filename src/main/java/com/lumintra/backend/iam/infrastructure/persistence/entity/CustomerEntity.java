package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class CustomerEntity {
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

    public static CustomerEntity createCustomerFor(UserAccountEntity account) {
        CustomerEntity customer = new CustomerEntity();
        customer.setAccount(account);
        customer.setCreatedAt(Instant.now());
        return customer;
    }

    public static CustomerEntity createOAuthCustomer(UserAccountEntity account, String fullName, String avatarUrl) {
        CustomerEntity customer = new CustomerEntity();
        customer.setAccount(account);
        customer.setFullName(fullName);
        customer.setAvatarUrl(avatarUrl);
        customer.setCreatedAt(Instant.now());
        return customer;
    }
}
