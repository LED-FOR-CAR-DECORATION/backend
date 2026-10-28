package com.lumintra.backend.iam.infrastructure.persistence.entity;

import com.lumintra.backend.iam.domain.model.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "user_account")
@Getter
@Setter
public class UserAccountEntity {
    public static UserAccountEntity registerCustomer(String email, String passwordHash) {
        UserAccountEntity account = new UserAccountEntity();
        account.setEmail(email);
        account.setPasswordHash(passwordHash);
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus("PENDING_VERIFICATION");
        return account;
    }

    public static UserAccountEntity googleCustomer(String email) {
        UserAccountEntity account = registerCustomer(email, null);
        account.setStatus("ACTIVE");
        return account;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    @PrePersist @PreUpdate
    private void normalizeEmail() { setEmail(email); }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @Column(unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;
}
