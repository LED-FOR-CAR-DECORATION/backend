package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
@Getter @Setter
public class AuthSessionEntity {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false) private UserAccountEntity account;
    @Column(name = "refresh_hash", nullable = false, length = 64, unique = true) private String refreshHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private boolean revoked;
    @Column(nullable = false) private boolean management;
}
