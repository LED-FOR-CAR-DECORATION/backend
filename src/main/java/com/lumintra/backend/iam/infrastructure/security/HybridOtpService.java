package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserOtpTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import com.lumintra.backend.shared.exception.iam.MaxOtpAttemptsException;
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
    private static final Duration OTP_TTL = Duration.ofMinutes(5); // 300s
    private static final int MAX_MISMATCHES = 5;
    private final SecureRandom secureRandom = new SecureRandom();

    private final SafeCacheService cacheService;
    private final UserOtpTokenRepository otpTokenRepository;

    private String getRedisKey(String email, OtpPurpose purpose) {
        return "auth:otp:" + purpose.name() + ":" + email;
    }

    private String getRedisAttemptsKey(String email, OtpPurpose purpose) {
        return "auth:otp_attempts:" + purpose.name() + ":" + email;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateAndStoreOtp(String email, OtpPurpose purpose) {
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        try {
            cacheService.set(getRedisKey(email, purpose), otp, OTP_TTL);
            // Reset attempts upon new generation
            cacheService.delete(getRedisAttemptsKey(email, purpose));
            return otp;
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, storing OTP in DB [email={}] [purpose={}]", email, purpose);

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

    // Inside HybridOtpService.java:

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean validateOtp(String email, String inputOtp, OtpPurpose purpose) {
        if (inputOtp == null || inputOtp.isBlank()) return false;

        try {
            String cachedOtp = cacheService.get(getRedisKey(email, purpose));
            if (cachedOtp == null) return false;

            if (inputOtp.equals(cachedOtp)) {
                cacheService.delete(getRedisKey(email, purpose));
                cacheService.delete(getRedisAttemptsKey(email, purpose));
                return true;
            } else {
                int attempts = cacheService.increment(getRedisAttemptsKey(email, purpose), OTP_TTL);
                if (attempts >= MAX_MISMATCHES) {
                    log.warn("Max OTP mismatches reached, invalidating token [email={}]", email);
                    cacheService.delete(getRedisKey(email, purpose));
                    cacheService.delete(getRedisAttemptsKey(email, purpose));
                    throw new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code.");
                }
                return false;
            }
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, validating OTP from DB [email={}] [purpose={}]", email, purpose);
            return otpTokenRepository.findByEmailAndPurpose(email, purpose)
                    .map(token -> {
                        if (token.getExpiresAt().isBefore(Instant.now())) {
                            otpTokenRepository.delete(token);
                            return false;
                        }

                        if (inputOtp.equals(token.getOtpCode())) {
                            otpTokenRepository.delete(token);
                            return true;
                        } else {
                            token.setFailedAttempts(token.getFailedAttempts() + 1);
                            if (token.getFailedAttempts() >= MAX_MISMATCHES) {
                                log.warn("Max OTP mismatches reached in DB, invalidating token [email={}]", email);
                                otpTokenRepository.delete(token);
                                throw new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code.");
                            } else {
                                otpTokenRepository.save(token);
                            }
                            return false;
                        }
                    }).orElse(false); // Token not found
        }
    }
}