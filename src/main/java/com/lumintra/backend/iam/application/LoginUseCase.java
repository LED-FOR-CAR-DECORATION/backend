package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);
    private static final Set<String> INTERNAL_STAFF_ROLES = Set.of("STAFF", "ADMIN", "MANAGER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        // AC02: Reject if account is currently locked
        if (loginAttemptService.isAccountLocked(request.email())) {
            log.warn("Login rejected: Account locked [email={}]", request.email());
            throw new AccountLockedException("Account is locked for 15 minutes due to too many failed attempts.");
        }

        UserEntity user = userRepository.findByEmail(request.email())
                .orElse(null);

        // AC01 & AC02: Verify credentials
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(request.email(), user);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // AC03: Reset failed attempt counter upon successful login
        loginAttemptService.resetAttempts(request.email());

        // AC04: Audit logging for internal staff via Spring Events
        boolean isStaff = isInternalStaff(user);
        if (isStaff) {
            String roleName = user.getRoles().stream()
                    .map(RoleEntity::getRoleName)
                    .findFirst()
                    .orElse("STAFF");
            eventPublisher.publishEvent(new StaffLoginSuccessEvent(user.getUserId(), user.getEmail(), roleName));
        }

        // AC01: Issue 15-minute Access Token & 30-day Refresh Token
        String accessToken = tokenProvider.createAccessToken(user);
        String refreshToken = tokenProvider.createRefreshToken(user);

        log.info("User login successful [userId={}]", user.getUserId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 minutes in seconds
                .build();
    }

    private void handleFailedLogin(String email, UserEntity user) {
        int attempts = loginAttemptService.registerFailedAttempt(email);
        log.warn("Login failed [email={}] [attemptCount={}]", email, attempts);

        if (attempts >= 5) {
            log.warn("Account lockout triggered [email={}]", email);
            if (user != null && isInternalStaff(user)) {
                String roleName = user.getRoles().stream()
                        .map(RoleEntity::getRoleName)
                        .findFirst()
                        .orElse("STAFF");
                eventPublisher.publishEvent(new StaffAccountLockedEvent(user.getUserId(), user.getEmail(), roleName));
            }
        }
    }

    private boolean isInternalStaff(UserEntity user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> INTERNAL_STAFF_ROLES.contains(r.getRoleName().toUpperCase()));
    }
}
