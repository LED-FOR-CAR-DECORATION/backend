package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static RefreshTokenEntity create(Long accountId, String tokenHash, Instant expiresAt) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setAccountId(accountId);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(expiresAt);
        return entity;
    }
}