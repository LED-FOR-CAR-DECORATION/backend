package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import com.lumintra.backend.iam.api.response.ResetPasswordResponse;
import com.lumintra.backend.iam.domain.event.StaffPasswordChangedEvent;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.PasswordResetOtpStore;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidOtpException;
import com.lumintra.backend.shared.exception.iam.MaxOtpAttemptsException;
import com.lumintra.backend.shared.exception.iam.PasswordResetUnavailableException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetOtpStore otpStore;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ResetPasswordUseCase useCase;

    @Test
    @DisplayName("UTCID01 [N]: Customer password reset validates OTP, updates hash, revokes sessions, no staff event")
    void customerResetPasswordSuccess() {
        String email = "customer@example.com";
        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(42L);
        account.setEmail(email);
        account.setAccountType(AccountType.CUSTOMER);
        account.setPasswordHash("oldHashedPassword");

        when(otpStore.validateAndConsume(email, "123456")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("NewP@ssword123")).thenReturn("encodedNewPassword");

        ResetPasswordResponse response = useCase.execute(new ResetPasswordRequest(email, "123456", "NewP@ssword123", null));

        verify(otpStore).validateAndConsume(email, "123456");
        verify(userAccountRepository).save(account);
        assertThat(account.getPasswordHash()).isEqualTo("encodedNewPassword");
        verify(refreshTokenService).revokeAllTokensForAccount(42L);
        verifyNoInteractions(employeeRepository);
        verifyNoInteractions(eventPublisher);
        assertThat(response.message()).contains("Password has been reset successfully");
    }

    @Test
    @DisplayName("UTCID02 [N]: Employee password reset publishes StaffPasswordChangedEvent with role name")
    void employeeResetPasswordSuccess() {
        String email = "manager@lumintra.com";
        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(101L);
        account.setEmail(email);
        account.setAccountType(AccountType.EMPLOYEE);
        account.setPasswordHash("oldHash");

        RoleEntity role = new RoleEntity();
        role.setRoleName("MANAGER");

        EmployeeEntity employee = new EmployeeEntity();
        employee.setRole(role);

        when(otpStore.validateAndConsume(email, "654321")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("SecureP@ss1")).thenReturn("newHash");
        when(employeeRepository.findByAccount_AccountId(101L)).thenReturn(Optional.of(employee));

        ResetPasswordResponse response = useCase.execute(new ResetPasswordRequest(email, "654321", "SecureP@ss1", "SecureP@ss1"));

        verify(otpStore).validateAndConsume(email, "654321");
        verify(userAccountRepository).save(account);
        verify(refreshTokenService).revokeAllTokensForAccount(101L);

        ArgumentCaptor<StaffPasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(StaffPasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StaffPasswordChangedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(101L);
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.role()).isEqualTo("MANAGER");
        assertThat(response.message()).isNotBlank();
    }

    @Test
    @DisplayName("UTCID03 [N]: Employee password reset with missing profile defaults to STAFF role")
    void employeeResetPasswordMissingProfileDefaultsRole() {
        String email = "staff@lumintra.com";
        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(202L);
        account.setEmail(email);
        account.setAccountType(AccountType.EMPLOYEE);

        when(otpStore.validateAndConsume(email, "112233")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("NewP@ss123")).thenReturn("hashed");
        when(employeeRepository.findByAccount_AccountId(202L)).thenReturn(Optional.empty());

        useCase.execute(new ResetPasswordRequest(email, "112233", "NewP@ss123", null));

        ArgumentCaptor<StaffPasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(StaffPasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().role()).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("UTCID04 [B]: Reset password clears failed login attempts and lockout timestamp")
    void resetPasswordClearsLockout() {
        String email = "locked@example.com";
        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(55L);
        account.setEmail(email);
        account.setAccountType(AccountType.CUSTOMER);
        account.setFailedAttempts(5);
        account.setLockoutUntil(Instant.now().plusSeconds(900));

        when(otpStore.validateAndConsume(email, "987654")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(anyString())).thenReturn("newEncodedHash");

        useCase.execute(new ResetPasswordRequest(email, "987654", "NewP@ssword123", null));

        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.getLockoutUntil()).isNull();
        assertThat(account.getUpdatedAt()).isNotNull();
        verify(userAccountRepository).save(account);
    }

    @Test
    @DisplayName("UTCID05 [B]: Valid request with matching confirmPassword executes successfully")
    void matchingConfirmPasswordExecutesSuccessfully() {
        String email = "match@example.com";
        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(77L);
        account.setEmail(email);
        account.setAccountType(AccountType.CUSTOMER);

        when(otpStore.validateAndConsume(email, "123456")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("SameP@ssword1")).thenReturn("hash");

        useCase.execute(new ResetPasswordRequest(email, "123456", "SameP@ssword1", "SameP@ssword1"));

        verify(userAccountRepository).save(account);
    }

    @Test
    @DisplayName("UTCID06 [A]: Mismatched confirmPassword throws IllegalArgumentException before store/DB")
    void confirmPasswordMismatchThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "Password@1", "Mismatch@2");

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match");

        verifyNoInteractions(otpStore);
        verifyNoInteractions(userAccountRepository);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("UTCID07 [A]: Invalid or expired OTP throws InvalidOtpException without modifying DB")
    void invalidOtpThrowsException() {
        String email = "user@example.com";
        when(otpStore.validateAndConsume(email, "000000"))
                .thenThrow(new InvalidOtpException("Invalid or expired OTP."));

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordRequest(email, "000000", "NewP@ssword123", null)))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired OTP.");

        verifyNoInteractions(userAccountRepository);
        verifyNoInteractions(refreshTokenService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID08 [A]: Max OTP attempts reached throws MaxOtpAttemptsException")
    void maxOtpAttemptsReachedThrowsException() {
        String email = "user@example.com";
        when(otpStore.validateAndConsume(email, "000000"))
                .thenThrow(new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code."));

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordRequest(email, "000000", "NewP@ssword123", null)))
                .isInstanceOf(MaxOtpAttemptsException.class);

        verifyNoInteractions(userAccountRepository);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("UTCID09 [A]: User account not found in database throws UserNotFoundException")
    void userNotFoundThrowsException() {
        String email = "nonexistent@example.com";
        when(otpStore.validateAndConsume(email, "123456")).thenReturn(true);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordRequest(email, "123456", "NewP@ssword123", null)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Account not found.");

        verify(otpStore).validateAndConsume(email, "123456");
        verifyNoInteractions(refreshTokenService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID10 [A]: Redis store outage throws PasswordResetUnavailableException without modifying DB")
    void storeOutageThrowsException() {
        String email = "user@example.com";
        when(otpStore.validateAndConsume(email, "123456"))
                .thenThrow(new PasswordResetUnavailableException());

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordRequest(email, "123456", "NewP@ssword123", null)))
                .isInstanceOf(PasswordResetUnavailableException.class);

        verifyNoInteractions(userAccountRepository);
        verifyNoInteractions(refreshTokenService);
    }
}
