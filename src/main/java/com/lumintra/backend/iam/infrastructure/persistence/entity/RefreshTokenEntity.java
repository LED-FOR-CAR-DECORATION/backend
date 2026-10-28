package com.lumintra.backend.iam.infrastructure.persistence.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="refresh_token") @Getter @Setter
public class RefreshTokenEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="refresh_token_id") private Long refreshTokenId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="account_id",nullable=false) private UserAccountEntity account;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    public static RefreshTokenEntity create(UserAccountEntity account,Instant expiresAt) {
        var record=new RefreshTokenEntity(); record.setAccount(account);record.setExpiresAt(expiresAt);
        // Temporary non-credential digest while IDENTITY assigns the record ID in the same transaction.
        record.setTokenHash((UUID.randomUUID().toString()+UUID.randomUUID()).replace("-","")); return record;
    }
}
