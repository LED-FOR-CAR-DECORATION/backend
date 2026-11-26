package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserOtpTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import com.lumintra.backend.shared.exception.iam.MaxResendAttemptsException;
import com.lumintra.backend.shared.exception.iam.ResendCooldownException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridOtpServiceResendTest {

    @Mock
    private SafeCacheService cacheService;

    @Mock
    private UserOtpTokenRepository otpTokenRepository;

    @InjectMocks
    private HybridOtpService hybridOtpService;

    @Test
    @DisplayName("UTCID01 [N]: Generate resend OTP in Redis, set 60s cooldown and increment 24h count")
    void generateOtpForResend_successInRedis() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey("auth:otp_cooldown:EMAIL_VERIFICATION:customer@example.com")).thenReturn(false);
        when(cacheService.get("auth:otp_resends:EMAIL_VERIFICATION:customer@example.com")).thenReturn("1");

        String otp = hybridOtpService.generateOtpForResend(email, purpose);

        assertThat(otp).isNotNull().hasSize(6).matches("^\\d{6}$");
        verify(cacheService).set(eq("auth:otp:EMAIL_VERIFICATION:customer@example.com"), eq(otp), eq(Duration.ofMinutes(5)));
        verify(cacheService).delete(eq("auth:otp_attempts:EMAIL_VERIFICATION:customer@example.com"));
        verify(cacheService).set(eq("auth:otp_cooldown:EMAIL_VERIFICATION:customer@example.com"), eq("LOCKED"), eq(Duration.ofSeconds(60)));
        verify(cacheService).increment(eq("auth:otp_resends:EMAIL_VERIFICATION:customer@example.com"), eq(Duration.ofHours(24)));
        verifyNoInteractions(otpTokenRepository);
    }

    @Test
    @DisplayName("UTCID02 [B]: Generated resend OTP is strictly 6 digits in [0, 999999] boundary range")
    void generateOtpForResend_boundaryZeroPaddedFormat() {
        String email = "boundary@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey("auth:otp_cooldown:EMAIL_VERIFICATION:boundary@example.com")).thenReturn(false);
        when(cacheService.get("auth:otp_resends:EMAIL_VERIFICATION:boundary@example.com")).thenReturn(null);

        String otp = hybridOtpService.generateOtpForResend(email, purpose);

        assertThat(otp).hasSize(6);
        assertThat(Integer.parseInt(otp)).isBetween(0, 999999);
    }

    @Test
    @DisplayName("UTCID03 [A]: Reject resend when 60s cooldown key is active in Redis (429)")
    void generateOtpForResend_redisCooldownActive_throwsResendCooldownException() {
        String email = "cooldown@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey("auth:otp_cooldown:EMAIL_VERIFICATION:cooldown@example.com")).thenReturn(true);

        assertThatThrownBy(() -> hybridOtpService.generateOtpForResend(email, purpose))
                .isInstanceOf(ResendCooldownException.class)
                .hasMessage("Please wait 60 seconds before requesting another email.");

        verify(cacheService, never()).set(eq("auth:otp:EMAIL_VERIFICATION:cooldown@example.com"), any(), any());
    }

    @Test
    @DisplayName("UTCID04 [A]: Reject resend when Redis resend attempt count reaches 3 (429)")
    void generateOtpForResend_redisMaxAttemptsReached_throwsMaxResendAttemptsException() {
        String email = "maxed@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey("auth:otp_cooldown:EMAIL_VERIFICATION:maxed@example.com")).thenReturn(false);
        when(cacheService.get("auth:otp_resends:EMAIL_VERIFICATION:maxed@example.com")).thenReturn("3");

        assertThatThrownBy(() -> hybridOtpService.generateOtpForResend(email, purpose))
                .isInstanceOf(MaxResendAttemptsException.class)
                .hasMessage("Maximum resend attempts reached for this email.");

        verify(cacheService, never()).set(eq("auth:otp:EMAIL_VERIFICATION:maxed@example.com"), any(), any());
    }

    @Test
    @DisplayName("UTCID05 [N]: Fallback to database when Redis unavailable, updates token and increments resend count")
    void generateOtpForResend_fallbackToDatabase_success() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey(any())).thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException()));
        doThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException()))
                .when(cacheService).set(eq("auth:otp:EMAIL_VERIFICATION:customer@example.com"), any(), any());

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "111111",
                purpose,
                Instant.now().plus(Duration.ofMinutes(5))
        );
        entity.setResendCount(1);
        entity.setLastSentAt(Instant.now().minusSeconds(120)); // Sent 2 min ago (> 60s cooldown)
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        String otp = hybridOtpService.generateOtpForResend(email, purpose);

        assertThat(otp).isNotNull().hasSize(6);
        ArgumentCaptor<UserOtpTokenEntity> captor = ArgumentCaptor.forClass(UserOtpTokenEntity.class);
        verify(otpTokenRepository).save(captor.capture());
        UserOtpTokenEntity saved = captor.getValue();
        assertThat(saved.getOtpCode()).isEqualTo(otp);
        assertThat(saved.getResendCount()).isEqualTo(2);
        assertThat(saved.getFailedAttempts()).isEqualTo(0);
        assertThat(saved.getLastSentAt()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    @DisplayName("UTCID06 [A]: Fallback to database when Redis unavailable, cooldown active within 60s (429)")
    void generateOtpForResend_fallbackToDatabase_cooldownActive_throwsResendCooldownException() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey(any())).thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException()));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "111111",
                purpose,
                Instant.now().plus(Duration.ofMinutes(5))
        );
        entity.setLastSentAt(Instant.now().minusSeconds(20)); // Sent 20s ago (< 60s cooldown)
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> hybridOtpService.generateOtpForResend(email, purpose))
                .isInstanceOf(ResendCooldownException.class)
                .hasMessage("Please wait 60 seconds before requesting another email.");

        verify(otpTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID07 [A]: Fallback to database when Redis unavailable, max resends reached >= 3 (429)")
    void generateOtpForResend_fallbackToDatabase_maxAttemptsReached_throwsMaxResendAttemptsException() {
        String email = "customer@example.com";
        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        when(cacheService.hasKey(any())).thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException()));

        UserOtpTokenEntity entity = UserOtpTokenEntity.createOtpFor(
                email,
                "111111",
                purpose,
                Instant.now().plus(Duration.ofMinutes(5))
        );
        entity.setResendCount(3);
        entity.setLastSentAt(Instant.now().minusSeconds(120));
        when(otpTokenRepository.findByEmailAndPurpose(email, purpose)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> hybridOtpService.generateOtpForResend(email, purpose))
                .isInstanceOf(MaxResendAttemptsException.class)
                .hasMessage("Maximum resend attempts reached for this email.");

        verify(otpTokenRepository, never()).save(any());
    }
}
