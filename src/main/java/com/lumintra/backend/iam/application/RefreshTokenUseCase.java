package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RefreshTokenRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public LoginResponse execute(RefreshTokenRequest request) {
        String rawToken = request.refreshToken();

        if (!tokenProvider.validateToken(rawToken)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        // Validate active token and immediately revoke it
        if (!refreshTokenService.validateAndRevoke(rawToken)) {
            throw new InvalidCredentialsException("Refresh token is unrecognized or already consumed.");
        }

        Long accountId = tokenProvider.getAccountIdFromToken(rawToken);
        UserAccountEntity account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidCredentialsException("Account not found."));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is suspended or deleted.");
        }

        List<String> roles;
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            EmployeeEntity employee = employeeRepository.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Employee profile missing"));
            roles = List.of("ROLE_" + employee.getRole().getRoleName().toUpperCase());
        } else {
            roles = List.of("ROLE_CUSTOMER");
        }

        // AC02: Issue new token pair
        String newAccessToken = tokenProvider.createAccessToken(account.getAccountId(), account.getEmail(), roles);
        String newRefreshToken = tokenProvider.createRefreshToken(account.getAccountId());

        refreshTokenService.storeRefreshToken(account.getAccountId(), newRefreshToken);

        return LoginResponse.of(newAccessToken, newRefreshToken);
    }
}