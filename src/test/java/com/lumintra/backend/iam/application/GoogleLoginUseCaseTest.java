package com.lumintra.backend.iam.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.lumintra.backend.iam.api.request.GoogleLoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleLoginUseCaseTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private RefreshTokenService refreshTokenService;


    @InjectMocks
    private GoogleLoginUseCase googleLoginUseCase;

    private GoogleIdToken createMockIdToken(String email, String name, String pictureUrl) {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        if (name != null) {
            payload.set("name", name);
        }
        if (pictureUrl != null) {
            payload.set("picture", pictureUrl);
        }
        when(idToken.getPayload()).thenReturn(payload);
        return idToken;
    }

    @Test
    @DisplayName("UTCID01 [N]: Existing active customer logs in successfully with valid Google token")
    void execute_existingActiveCustomer_returnsLoginResponse() throws Exception {
        String token = "valid-google-id-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "customer@example.com";

        GoogleIdToken idToken = createMockIdToken(email, "Jane Doe", "https://img.com/avatar.png");
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);

        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(100L);
        account.setEmail(email);
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus(AccountStatus.ACTIVE);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(tokenProvider.createAccessToken(100L, email, List.of("ROLE_CUSTOMER"))).thenReturn("access-token-100");
        when(tokenProvider.createRefreshToken(100L)).thenReturn("refresh-token-100");

        LoginResponse response = googleLoginUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-100");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-100");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        verify(loginAttemptService).resetAttempts(email);
        verify(refreshTokenService).storeRefreshToken(100L, "refresh-token-100");
        verify(userAccountRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID02 [N]: New customer logs in & auto-provisions UserAccount and Customer profile")
    void execute_newCustomer_provisionsAccountAndReturnsLoginResponse() throws Exception {
        String token = "new-customer-id-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "newuser@example.com";
        String fullName = "Alice Wonder";
        String avatarUrl = "https://img.com/alice.png";

        GoogleIdToken idToken = createMockIdToken(email, fullName, avatarUrl);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserAccountEntity savedAccount = new UserAccountEntity();
        savedAccount.setAccountId(200L);
        savedAccount.setEmail(email);
        savedAccount.setAccountType(AccountType.CUSTOMER);
        savedAccount.setStatus(AccountStatus.ACTIVE);
        savedAccount.setAvatarUrl(avatarUrl);

        when(userAccountRepository.save(any(UserAccountEntity.class))).thenReturn(savedAccount);
        when(tokenProvider.createAccessToken(200L, email, List.of("ROLE_CUSTOMER"))).thenReturn("access-token-200");
        when(tokenProvider.createRefreshToken(200L)).thenReturn("refresh-token-200");

        LoginResponse response = googleLoginUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-200");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-200");

        ArgumentCaptor<UserAccountEntity> accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccountEntity capturedAccount = accountCaptor.getValue();
        assertThat(capturedAccount.getEmail()).isEqualTo(email);
        assertThat(capturedAccount.getAccountType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(capturedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(capturedAccount.getAvatarUrl()).isEqualTo(avatarUrl);

        ArgumentCaptor<CustomerEntity> customerCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(customerCaptor.capture());
        CustomerEntity capturedCustomer = customerCaptor.getValue();
        assertThat(capturedCustomer.getFullName()).isEqualTo(fullName);
        assertThat(capturedCustomer.getAvatarUrl()).isEqualTo(avatarUrl);
        assertThat(capturedCustomer.getAccount()).isEqualTo(savedAccount);

        verify(refreshTokenService).storeRefreshToken(200L, "refresh-token-200");
        verify(loginAttemptService).resetAttempts(email);
    }

    @Test
    @DisplayName("UTCID03 [N]: Normalizes email with whitespace and uppercase characters")
    void execute_normalizesEmail() throws Exception {
        String token = "mixed-case-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String rawEmail = "  Alice.Smith@Domain.COM  ";
        String expectedNormalized = "alice.smith@domain.com";

        GoogleIdToken idToken = createMockIdToken(rawEmail, "Alice", null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(expectedNormalized)).thenReturn(false);

        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(300L);
        account.setEmail(expectedNormalized);
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus(AccountStatus.ACTIVE);

        when(userAccountRepository.findByEmail(expectedNormalized)).thenReturn(Optional.of(account));
        when(tokenProvider.createAccessToken(300L, expectedNormalized, List.of("ROLE_CUSTOMER"))).thenReturn("tok-300");
        when(tokenProvider.createRefreshToken(300L)).thenReturn("ref-300");

        LoginResponse response = googleLoginUseCase.execute(request);

        assertThat(response).isNotNull();
        verify(userAccountRepository).findByEmail(expectedNormalized);
        verify(tokenProvider).createAccessToken(300L, expectedNormalized, List.of("ROLE_CUSTOMER"));
        verify(refreshTokenService).storeRefreshToken(300L, "ref-300");
        verify(loginAttemptService).resetAttempts(expectedNormalized);
    }

    @Test
    @DisplayName("UTCID04 [A]: Throws InvalidCredentialsException when verifier returns null (invalid/expired token)")
    void execute_verifierReturnsNull_throwsInvalidCredentialsException() throws Exception {
        String token = "invalid-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);

        when(verifier.verify(token)).thenReturn(null);

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Failed to verify Google token.");

        verifyNoInteractions(loginAttemptService, userAccountRepository, customerRepository, tokenProvider);
    }

    @Test
    @DisplayName("UTCID05 [A]: Throws InvalidCredentialsException when verifier throws GeneralSecurityException or IOException")
    void execute_verifierThrowsException_throwsInvalidCredentialsException() throws Exception {
        String token = "corrupt-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);

        when(verifier.verify(token)).thenThrow(new GeneralSecurityException("Signature verification failed"));

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Failed to verify Google token.");

        verifyNoInteractions(loginAttemptService, userAccountRepository, customerRepository, tokenProvider);
    }

    @Test
    @DisplayName("UTCID06 [A]: Throws AccountLockedException when account is currently locked")
    void execute_accountLocked_throwsAccountLockedException() throws Exception {
        String token = "locked-account-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "locked@example.com";

        GoogleIdToken idToken = createMockIdToken(email, "Locked User", null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(true);

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage("Account is locked for 15 minutes.");

        verify(loginAttemptService, never()).resetAttempts(anyString());
        verifyNoInteractions(userAccountRepository, customerRepository, tokenProvider);
    }

    @Test
    @DisplayName("UTCID07 [A]: Rejects staff/employee account and increments failed login attempts")
    void execute_staffAccount_rejectsAndRegistersFailedAttempt() throws Exception {
        String token = "employee-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "staff@lumintra.com";

        GoogleIdToken idToken = createMockIdToken(email, "Staff Member", null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(50L);
        employeeAccount.setEmail(email);
        employeeAccount.setAccountType(AccountType.EMPLOYEE);
        employeeAccount.setStatus(AccountStatus.ACTIVE);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(employeeAccount));
        when(loginAttemptService.registerFailedAttempt(email)).thenReturn(1);

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Google login is not supported for staff accounts.");

        verify(loginAttemptService).registerFailedAttempt(email);
        verify(loginAttemptService, never()).resetAttempts(anyString());
        verifyNoInteractions(customerRepository, tokenProvider);
    }

    @Test
    @DisplayName("UTCID08 [A]: Rejects inactive/suspended customer account")
    void execute_inactiveAccount_throwsInvalidCredentialsException() throws Exception {
        String token = "suspended-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "suspended@example.com";

        GoogleIdToken idToken = createMockIdToken(email, "Suspended User", null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);

        UserAccountEntity suspendedAccount = new UserAccountEntity();
        suspendedAccount.setAccountId(60L);
        suspendedAccount.setEmail(email);
        suspendedAccount.setAccountType(AccountType.CUSTOMER);
        suspendedAccount.setStatus(AccountStatus.SUSPENDED);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(suspendedAccount));

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Account is suspended or deleted.");

        verify(loginAttemptService, never()).resetAttempts(anyString());
        verifyNoInteractions(tokenProvider);
    }

    @Test
    @DisplayName("UTCID09 [B]: Employee rejection reaches 5th failed attempt threshold")
    void execute_staffAccount_reachesFailedAttemptThreshold() throws Exception {
        String token = "employee-threshold-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "staff.threshold@lumintra.com";

        GoogleIdToken idToken = createMockIdToken(email, "Staff Lockout", null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(70L);
        employeeAccount.setEmail(email);
        employeeAccount.setAccountType(AccountType.EMPLOYEE);
        employeeAccount.setStatus(AccountStatus.ACTIVE);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(employeeAccount));
        when(loginAttemptService.registerFailedAttempt(email)).thenReturn(5);

        assertThatThrownBy(() -> googleLoginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Google login is not supported for staff accounts.");

        verify(loginAttemptService).registerFailedAttempt(email);
    }

    @Test
    @DisplayName("UTCID10 [B]: New customer auto-registration when name and picture are null")
    void execute_newCustomerWithNullNameAndPicture_handlesNullsSafely() throws Exception {
        String token = "minimal-claims-token";
        GoogleLoginRequest request = new GoogleLoginRequest(token);
        String email = "minimal@example.com";

        GoogleIdToken idToken = createMockIdToken(email, null, null);
        when(verifier.verify(token)).thenReturn(idToken);
        when(loginAttemptService.isAccountLocked(email)).thenReturn(false);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserAccountEntity savedAccount = new UserAccountEntity();
        savedAccount.setAccountId(400L);
        savedAccount.setEmail(email);
        savedAccount.setAccountType(AccountType.CUSTOMER);
        savedAccount.setStatus(AccountStatus.ACTIVE);
        savedAccount.setAvatarUrl(null);

        when(userAccountRepository.save(any(UserAccountEntity.class))).thenReturn(savedAccount);
        when(tokenProvider.createAccessToken(400L, email, List.of("ROLE_CUSTOMER"))).thenReturn("access-token-400");
        when(tokenProvider.createRefreshToken(400L)).thenReturn("refresh-token-400");

        LoginResponse response = googleLoginUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-400");
        verify(refreshTokenService).storeRefreshToken(400L, "refresh-token-400");

        ArgumentCaptor<UserAccountEntity> accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAvatarUrl()).isNull();

        ArgumentCaptor<CustomerEntity> customerCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getFullName()).isNull();
        assertThat(customerCaptor.getValue().getAvatarUrl()).isNull();
    }
}
