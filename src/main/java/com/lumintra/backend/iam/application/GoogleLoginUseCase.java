package com.lumintra.backend.iam.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.lumintra.backend.iam.api.request.GoogleLoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginUseCase.class);
    private final GoogleIdTokenVerifier verifier;
    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse execute(GoogleLoginRequest request) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.idToken());
            if (idToken == null) {
                throw new InvalidCredentialsException("Invalid or expired Google ID token.");
            }
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new InvalidCredentialsException("Failed to verify Google token.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail().trim().toLowerCase();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        if (loginAttemptService.isAccountLocked(email)) {
            log.warn("Google login rejected: Account locked [email={}]", email);
            throw new AccountLockedException("Account is locked for 15 minutes.");
        }

        UserAccountEntity account = userAccountRepository.findByEmail(email).orElse(null);

        // Reject staff trying to use Google Login
        if (account != null && account.getAccountType() == AccountType.EMPLOYEE) {
            handleFailedLogin(email);
            throw new InvalidCredentialsException("Google login is not supported for staff accounts.");
        }

        // Automatic Registration for new customers
        if (account == null) {
            account = UserAccountEntity.createOAuthCustomerAccount(email, pictureUrl);
            account = userAccountRepository.save(account);

            CustomerEntity customer = CustomerEntity.createOAuthCustomer(account, name, pictureUrl);
            customerRepository.save(customer);

            log.info("New OAuth customer registered automatically [accountId={}] [email={}]", account.getAccountId(), email);
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is suspended or deleted.");
        }

        loginAttemptService.resetAttempts(email);

        String accessToken = tokenProvider.createAccessToken(account.getAccountId(), account.getEmail(), List.of("ROLE_CUSTOMER"));
        String refreshToken = tokenProvider.createRefreshToken(account.getAccountId());

        log.info("Google login successful [accountId={}] [accountType={}]", account.getAccountId(), account.getAccountType());

        return LoginResponse.of(accessToken, refreshToken);
    }

    private void handleFailedLogin(String email) {
        int attempts = loginAttemptService.registerFailedAttempt(email);
        log.warn("Google login failed [email={}] [attemptCount={}]", email, attempts);
        if (attempts >= 5) {
            log.warn("Account lockout triggered via Google Auth [email={}]", email);
        }
    }
}