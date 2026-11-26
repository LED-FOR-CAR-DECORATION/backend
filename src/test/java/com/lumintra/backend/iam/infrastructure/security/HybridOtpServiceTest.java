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
        verify(cacheService).set(eq("auth:otp:EMAIL_VERIFICATION:customer@example.com"), eq(otp), eq(Duration.ofMinutes(5)));
        verify(cacheService).delete(eq("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com"));
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
}
