package com.lumintra.backend.iam.infrastructure.persistence.entity;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_otp_tokens")
@Getter
@Setter
public class UserOtpTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tokenId;

    @Column(nullable = false)
    private String email;

    @Column(name = "otp_code", nullable = false)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "resend_count", nullable = false)
    private int resendCount = 0;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    public static UserOtpTokenEntity createOtpFor(String email, String otpCode, OtpPurpose purpose, Instant expiresAt) {
        UserOtpTokenEntity tokenEntity = new UserOtpTokenEntity();
        tokenEntity.setEmail(email);
        tokenEntity.setOtpCode(otpCode);
        tokenEntity.setPurpose(purpose);
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setFailedAttempts(0);
        tokenEntity.setResendCount(0);
        tokenEntity.setLastSentAt(Instant.now());
        return tokenEntity;
    }
}