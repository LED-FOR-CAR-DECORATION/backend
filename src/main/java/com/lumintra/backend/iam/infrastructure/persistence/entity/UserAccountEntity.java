package com.lumintra.backend.iam.infrastructure.persistence.entity;

import com.lumintra.backend.iam.domain.model.AccountStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    public static UserAccountEntity createCustomerAccount(String email, String passwordHash) {
        UserAccountEntity account = new UserAccountEntity();
        account.setEmail(email);
        account.setPasswordHash(passwordHash);
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setCreatedAt(Instant.now());
        account.setFailedAttempts(0);
        return account;
    }
}