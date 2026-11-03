package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserOtpTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridOtpServiceTest {

    @Mock
    private SafeCacheService cacheService;

    @Mock
    private UserOtpTokenRepository otpTokenRepository;

    @InjectMocks
    private HybridOtpService hybridOtpService;

    @Test
    @DisplayName("UTCID01 [N]: Generate and store OTP successfully in Redis cache")
    void generateAndStoreOtp_successInRedis() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        String otp = hybridOtpService.generateAndStoreOtp(email, purpose);

        assertThat(otp).isNotNull().hasSize(6).matches("^\\d{6}$");
        verify(cacheService).set(eq("auth:otp:EMAIL_VERIFICATION:customer@example.com"), eq(otp), eq(Duration.ofMinutes(15)));
        verifyNoInteractions(otpTokenRepository);
    }

    @Test
    @DisplayName("UTCID02 [B]: Generated OTP is strictly 6 digits with zero-padded boundary format")
    void generateAndStoreOtp_boundaryZeroPaddedFormat() {
        String email = "boundary@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        String otp = hybridOtpService.generateAndStoreOtp(email, purpose);

        assertThat(otp).hasSize(6);
        assertThat(Integer.parseInt(otp)).isBetween(0, 999999);
    }

    @Test
    @DisplayName("UTCID03 [A]: Fallback to database when Redis cache is unavailable during OTP generation")
    void generateAndStoreOtp_fallbackToDatabaseWhenCacheUnavailable() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        doThrow(new CacheUnavailableException("Redis is DOWN", new RuntimeException("Connection refused")))
                .when(cacheService).set(any(), any(), any());

        String otp = hybridOtpService.generateAndStoreOtp(email, purpose);

        assertThat(otp).isNotNull().hasSize(6);
        verify(otpTokenRepository).deleteByEmailAndPurpose(email, purpose);

        ArgumentCaptor<UserOtpTokenEntity> tokenCaptor = ArgumentCaptor.forClass(UserOtpTokenEntity.class);
        verify(otpTokenRepository).save(tokenCaptor.capture());
        UserOtpTokenEntity savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getEmail()).isEqualTo(email);
        assertThat(savedToken.getOtpCode()).isEqualTo(otp);
        assertThat(savedToken.getPurpose()).isEqualTo(purpose);
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("UTCID04 [N]: Validate OTP successfully from Redis cache and delete upon match")
    void validateOtp_successFromRedis() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;
        String validOtp = "123456";

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn(validOtp);

        boolean result = hybridOtpService.validateOtp(email, validOtp, purpose);

        assertThat(result).isTrue();
        verify(cacheService).delete("auth:otp:EMAIL_VERIFICATION:customer@example.com");
    }

    @Test
    @DisplayName("UTCID05 [A]: Validate OTP returns false when OTP does not match in Redis cache")
    void validateOtp_mismatchInRedisReturnsFalse() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.get("auth:otp:EMAIL_VERIFICATION:customer@example.com")).thenReturn("654321");

        boolean result = hybridOtpService.validateOtp(email, "111111", purpose);

        assertThat(result).isFalse();
        verify(cacheService, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Validate OTP fallback to database when Redis is unavailable")
    void validateOtp_fallbackToDatabaseWhenCacheUnavailable() {
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
    @DisplayName("UTCID07 [A]: Validate OTP returns false when input OTP is null or blank")
    void validateOtp_nullOrBlankInputReturnsFalse() {
        assertThat(hybridOtpService.validateOtp("test@example.com", null, OtpPurpose.EMAIL_VERIFICATION)).isFalse();
        assertThat(hybridOtpService.validateOtp("test@example.com", "   ", OtpPurpose.EMAIL_VERIFICATION)).isFalse();
        verifyNoInteractions(cacheService, otpTokenRepository);
    }
}
