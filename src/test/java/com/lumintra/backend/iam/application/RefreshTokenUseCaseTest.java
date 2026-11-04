package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RefreshTokenRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private RefreshTokenUseCase refreshTokenUseCase;

    @Test
    @DisplayName("UTCID01 [N]: Customer token refresh succeeds, revokes old token, and returns new token pair")
    void execute_customerSuccess_returnsNewTokens() {
        String rawToken = "valid.customer.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        UserAccountEntity customer = new UserAccountEntity();
        customer.setAccountId(101L);
        customer.setEmail("customer@example.com");
        customer.setAccountType(AccountType.CUSTOMER);
        customer.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(101L);
        when(userAccountRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(tokenProvider.createAccessToken(101L, "customer@example.com", List.of("ROLE_CUSTOMER")))
                .thenReturn("new-access-token-101");
        when(tokenProvider.createRefreshToken(101L)).thenReturn("new-refresh-token-101");

        LoginResponse response = refreshTokenUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token-101");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token-101");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        verify(refreshTokenService).storeRefreshToken(101L, "new-refresh-token-101");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    @DisplayName("UTCID02 [N]: Employee token refresh succeeds with employee role authorities")
    void execute_employeeSuccess_returnsNewTokensWithRoles() {
        String rawToken = "valid.employee.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(201L);
        employeeAccount.setEmail("admin@lumintra.com");
        employeeAccount.setAccountType(AccountType.EMPLOYEE);
        employeeAccount.setStatus(AccountStatus.ACTIVE);

        RoleEntity role = new RoleEntity();
        role.setRoleName("ADMIN");

        EmployeeEntity employee = new EmployeeEntity();
        employee.setAccount(employeeAccount);
        employee.setRole(role);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(201L);
        when(userAccountRepository.findById(201L)).thenReturn(Optional.of(employeeAccount));
        when(employeeRepository.findByAccount_AccountId(201L)).thenReturn(Optional.of(employee));
        when(tokenProvider.createAccessToken(201L, "admin@lumintra.com", List.of("ROLE_ADMIN")))
                .thenReturn("new-access-token-admin");
        when(tokenProvider.createRefreshToken(201L)).thenReturn("new-refresh-token-admin");

        LoginResponse response = refreshTokenUseCase.execute(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token-admin");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token-admin");
        verify(refreshTokenService).storeRefreshToken(201L, "new-refresh-token-admin");
    }

    @Test
    @DisplayName("UTCID03 [A]: Throws InvalidCredentialsException when tokenProvider validates token as false")
    void execute_invalidJwtToken_throwsInvalidCredentialsException() {
        String rawToken = "invalid.jwt.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        when(tokenProvider.validateToken(rawToken)).thenReturn(false);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid or expired refresh token.");

        verifyNoInteractions(refreshTokenService, userAccountRepository, employeeRepository);
    }

    @Test
    @DisplayName("UTCID04 [A]: Throws InvalidCredentialsException when token is unrecognized or already consumed")
    void execute_tokenAlreadyConsumed_throwsInvalidCredentialsException() {
        String rawToken = "consumed.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(false);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token is unrecognized or already consumed.");

        verifyNoInteractions(userAccountRepository, employeeRepository);
        verify(tokenProvider, never()).createAccessToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UTCID05 [A]: Throws InvalidCredentialsException when account is not found in database")
    void execute_accountNotFound_throwsInvalidCredentialsException() {
        String rawToken = "orphan.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(999L);
        when(userAccountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Account not found.");

        verifyNoInteractions(employeeRepository);
        verify(tokenProvider, never()).createAccessToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Throws InvalidCredentialsException when account is not active (e.g. SUSPENDED)")
    void execute_inactiveAccount_throwsInvalidCredentialsException() {
        String rawToken = "suspended.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(102L);
        account.setEmail("suspended@example.com");
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus(AccountStatus.SUSPENDED);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(102L);
        when(userAccountRepository.findById(102L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Account is suspended or deleted.");

        verify(tokenProvider, never()).createAccessToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UTCID07 [A]: Throws IllegalStateException when employee profile is missing")
    void execute_missingEmployeeProfile_throwsIllegalStateException() {
        String rawToken = "employee.no.profile.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(202L);
        employeeAccount.setEmail("no.profile@lumintra.com");
        employeeAccount.setAccountType(AccountType.EMPLOYEE);
        employeeAccount.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(202L);
        when(userAccountRepository.findById(202L)).thenReturn(Optional.of(employeeAccount));
        when(employeeRepository.findByAccount_AccountId(202L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Employee profile missing");

        verify(tokenProvider, never()).createAccessToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UTCID08 [B]: Token rotation enforces strict revocation of old token before storing new token")
    void execute_tokenRotation_enforcesRevokeBeforeStore() {
        String rawToken = "rotation.refresh.token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        UserAccountEntity customer = new UserAccountEntity();
        customer.setAccountId(103L);
        customer.setEmail("customer103@example.com");
        customer.setAccountType(AccountType.CUSTOMER);
        customer.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenService.validateAndRevoke(rawToken)).thenReturn(true);
        when(tokenProvider.getAccountIdFromToken(rawToken)).thenReturn(103L);
        when(userAccountRepository.findById(103L)).thenReturn(Optional.of(customer));
        when(tokenProvider.createAccessToken(eq(103L), anyString(), any())).thenReturn("access-103");
        when(tokenProvider.createRefreshToken(103L)).thenReturn("refresh-103");

        refreshTokenUseCase.execute(request);

        InOrder inOrder = inOrder(refreshTokenService);
        inOrder.verify(refreshTokenService).validateAndRevoke(rawToken);
        inOrder.verify(refreshTokenService).storeRefreshToken(103L, "refresh-103");
    }
}
