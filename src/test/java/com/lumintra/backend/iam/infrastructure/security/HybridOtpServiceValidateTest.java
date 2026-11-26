package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserOtpTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import com.lumintra.backend.shared.exception.iam.MaxOtpAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridOtpServiceValidateTest {

    @Mock
    private SafeCacheService cacheService;

    @Mock
    private UserOtpTokenRepository otpTokenRepository;

    @InjectMocks
    private HybridOtpService hybridOtpService;

    @Test
    @DisplayName("UTCID01 [N]: Validate OTP successfully from Redis cache and delete OTP and attempts keys")
    void validateOtp_successFromRedis() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;
        String validOtp = "123456";

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn(validOtp);

        boolean result = hybridOtpService.validateOtp(email, validOtp, purpose);

        assertThat(result).isTrue();
        verify(cacheService).delete("auth:otp:EMAIL_VERIFICATION:customer@example.com");
        verify(cacheService).delete("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com");
        verifyNoInteractions(otpTokenRepository);
    }

    @Test
    @DisplayName("UTCID02 [B]: Validate zero-padded 6-digit boundary OTP ('000123') matches successfully")
    void validateOtp_zeroPaddedBoundaryCode() {
        String email = "boundary@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;
        String validOtp = "000123";

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:boundary@example.com")).thenReturn(validOtp);

        boolean result = hybridOtpService.validateOtp(email, validOtp, purpose);

        assertThat(result).isTrue();
        verify(cacheService).delete("auth:otp:EMAIL_VERIFICATION:boundary@example.com");
    }

    @Test
    @DisplayName("UTCID03 [A]: Validate OTP returns false immediately when input OTP is null or blank")
    void validateOtp_nullOrBlankInput_returnsFalse() {
        assertThat(hybridOtpService.validateOtp("test@example.com", null, OtpPurpose.EMAIL_VERIFICATION)).isFalse();
        assertThat(hybridOtpService.validateOtp("test@example.com", "   ", OtpPurpose.EMAIL_VERIFICATION)).isFalse();
        verifyNoInteractions(cacheService, otpTokenRepository);
    }

    @Test
    @DisplayName("UTCID04 [A]: Validate OTP returns false when OTP is expired or absent from Redis cache")
    void validateOtp_keyNotFoundInRedis_returnsFalse() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn(null);

        boolean result = hybridOtpService.validateOtp(email, "123456", purpose);

        assertThat(result).isFalse();
        verify(cacheService, never()).delete(any());
        verifyNoInteractions(otpTokenRepository);
    }

    @Test
    @DisplayName("UTCID05 [A]: Validate OTP records mismatch in Redis attempts counter and returns false")
    void validateOtp_mismatchInRedisUnderThreshold_returnsFalse() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn("654321");
        when(cacheService.increment(eq("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com"), any(Duration.class)))
                .thenReturn(1);

        boolean result = hybridOtpService.validateOtp(email, "111111", purpose);

        assertThat(result).isFalse();
        verify(cacheService, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Validate OTP invalidates Redis token and throws MaxOtpAttemptsException on 5th mismatch")
    void validateOtp_maxMismatchesInRedis_invalidatesTokenAndThrowsException() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn("654321");
        when(cacheService.increment(eq("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com"), any(Duration.class)))
                .thenReturn(5);

        assertThatThrownBy(() -> hybridOtpService.validateOtp(email, "111111", purpose))
                .isInstanceOf(MaxOtpAttemptsException.class)
                .hasMessage("Maximum OTP attempts reached. Please request a new code.");

        verify(cacheService).delete("auth:otp:EMAIL_VERIFICATION:customer@example.com");
        verify(cacheService).delete("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com");
    }

    @Test
    @DisplayName("UTCID07 [N]: Fallback to database when Redis unavailable, matches unexpired token, deletes and returns true")
    void validateOtp_fallbackToDatabase_success() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;
        String validOtp = "888888";

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com"))
                .thenThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                validOtp,
                purpose,
                Instant.now().plus(Duration.ofMinutes(10))
        );
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        boolean result = hybridOtpService.validateOtp(email, validOtp, purpose);

        assertThat(result).isTrue();
        verify(otpTokenRepository).delete(entity);
    }

    @Test
    @DisplayName("UTCID08 [A]: Fallback to database when Redis unavailable, deletes expired token and returns false")
    void validateOtp_fallbackToDatabase_tokenExpired_deletesAndReturnsFalse() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com"))
                .thenThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "123456",
                purpose,
                Instant.now().minus(Duration.ofMinutes(1)) // Expired
        );
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        boolean result = hybridOtpService.validateOtp(email, "123456", purpose);

        assertThat(result).isFalse();
        verify(otpTokenRepository).delete(entity);
    }

    @Test
    @DisplayName("UTCID09 [A]: Fallback to database when Redis unavailable, returns false when token not found in DB")
    void validateOtp_fallbackToDatabase_tokenNotFound_returnsFalse() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com"))
                .thenThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")));

        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.empty());

        boolean result = hybridOtpService.validateOtp(email, "123456", purpose);

        assertThat(result).isFalse();
        verify(otpTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID10 [A]: Fallback to database when Redis unavailable, increments failedAttempts and returns false")
    void validateOtp_fallbackToDatabase_mismatchUnderThreshold_incrementsFailedAttempts() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com"))
                .thenThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "654321",
                purpose,
                Instant.now().plus(Duration.ofMinutes(10))
        );
        entity.setFailedAttempts(1);
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        boolean result = hybridOtpService.validateOtp(email, "111111", purpose);

        assertThat(result).isFalse();
        assertThat(entity.getFailedAttempts()).isEqualTo(2);
        verify(otpTokenRepository).save(entity);
        verify(otpTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID11 [A]: Fallback to database when Redis unavailable, deletes token and throws on 5th failed attempt")
    void validateOtp_fallbackToDatabase_maxAttemptsReached_deletesTokenAndThrowsException() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com"))
                .thenThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "654321",
                purpose,
                Instant.now().plus(Duration.ofMinutes(10))
        );
        entity.setFailedAttempts(4); // 4 + 1 = 5
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> hybridOtpService.validateOtp(email, "111111", purpose))
                .isInstanceOf(MaxOtpAttemptsException.class)
                .hasMessage("Maximum OTP attempts reached. Please request a new code.");

        verify(otpTokenRepository).delete(entity);
        verify(otpTokenRepository, never()).save(entity);
    }
}
