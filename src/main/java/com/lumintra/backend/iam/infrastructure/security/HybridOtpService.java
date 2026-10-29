// src/main/java/com/lumintra/backend/iam/infrastructure/security/HybridOtpService.java
package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserOtpTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class HybridOtpService implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(HybridOtpService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private final SecureRandom secureRandom = new SecureRandom();

    private final SafeCacheService cacheService;
    private final UserOtpTokenRepository otpTokenRepository;

    private String getRedisKey(String email, OtpPurpose purpose) {
        return "auth:otp:" + purpose.name() + ":" + email;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateAndStoreOtp(String email, OtpPurpose purpose) {
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        try {
            cacheService.set(getRedisKey(email, purpose), otp, OTP_TTL);
            return otp;
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, storing OTP in DB [email={}] [purpose={}]", email, purpose);

            // Delete any existing OTP for this specific purpose to prevent duplicates
            otpTokenRepository.deleteByEmailAndPurpose(email, purpose);

            UserOtpTokenEntity tokenEntity = UserOtpTokenEntity.createOtpFor(
                    email,
                    otp,
                    purpose,
                    Instant.now().plus(OTP_TTL)
            );
            otpTokenRepository.save(tokenEntity);

            return otp;
        }
    }

    @Override
    @Transactional
    public boolean validateOtp(String email, String inputOtp, OtpPurpose purpose) {
        if (inputOtp == null || inputOtp.isBlank()) return false;

        try {
            String cachedOtp = cacheService.get(getRedisKey(email, purpose));
            if (inputOtp.equals(cachedOtp)) {
                cacheService.delete(getRedisKey(email, purpose));
                return true;
            }
            return false;
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, validating OTP from DB [email={}] [purpose={}]", email, purpose);
            return otpTokenRepository.findByEmailAndPurpose(email, purpose)
                    .map(token -> {
                        boolean isValid = inputOtp.equals(token.getOtpCode()) &&
                                token.getExpiresAt().isAfter(Instant.now());
                        if (isValid) {
                            otpTokenRepository.delete(token);
                        }
                        return isValid;
                    }).orElse(false);
        }
    }
}