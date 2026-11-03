package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeProfileEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.PermissionEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeProfileRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    private static Validator validator;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoginUseCase loginUseCase;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("UTCID01 [N]: Customer login succeeds with valid credentials, resets attempts, and returns JWT tokens")
    void login_customerSuccess() {
        LoginRequest request = new LoginRequest("customer@example.com", "P@ssword123");

        UserAccountEntity customer = new UserAccountEntity();
        customer.setAccountId(101L);
        customer.setEmail("customer@example.com");
        customer.setPasswordHash("$2a$10$hashedPassword");
        customer.setAccountType(AccountType.CUSTOMER);

        when(loginAttemptService.isAccountLocked("customer@example.com")).thenReturn(false);
        when(userAccountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("P@ssword123", "$2a$10$hashedPassword")).thenReturn(true);
        when(tokenProvider.createAccessToken(101L, "customer@example.com", List.of("ROLE_CUSTOMER"), Set.of()))
                .thenReturn("access-token-customer");
        when(tokenProvider.createRefreshToken(101L)).thenReturn("refresh-token-customer");

        LoginResponse response = loginUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-customer");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-customer");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        verify(loginAttemptService).resetAttempts("customer@example.com");
        verifyNoInteractions(eventPublisher); // Customers do not publish staff audit events
    }

    @Test
    @DisplayName("UTCID02 [N]: Employee login succeeds with RBAC permissions and publishes StaffLoginSuccessEvent")
    void login_employeeSuccess() {
        LoginRequest request = new LoginRequest("admin@lumintra.com", "P@ssword123");

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(201L);
        employeeAccount.setEmail("admin@lumintra.com");
        employeeAccount.setPasswordHash("$2a$10$hashedPassword");
        employeeAccount.setAccountType(AccountType.EMPLOYEE);

        RoleEntity role = new RoleEntity();
        role.setRoleName("ADMIN");
        PermissionEntity perm = new PermissionEntity();
        perm.setPermissionName("USER_READ");
        role.setPermissions(Set.of(perm));

        EmployeeProfileEntity employeeProfile = new EmployeeProfileEntity();
        employeeProfile.setAccount(employeeAccount);
        employeeProfile.setRole(role);

        when(loginAttemptService.isAccountLocked("admin@lumintra.com")).thenReturn(false);
        when(userAccountRepository.findByEmail("admin@lumintra.com")).thenReturn(Optional.of(employeeAccount));
        when(passwordEncoder.matches("P@ssword123", "$2a$10$hashedPassword")).thenReturn(true);
        when(employeeProfileRepository.findByAccount_AccountId(201L)).thenReturn(Optional.of(employeeProfile));
        when(tokenProvider.createAccessToken(eq(201L), eq("admin@lumintra.com"), eq(List.of("ROLE_ADMIN")), eq(Set.of("USER_READ"))))
                .thenReturn("access-token-admin");
        when(tokenProvider.createRefreshToken(201L)).thenReturn("refresh-token-admin");

        LoginResponse response = loginUseCase.execute(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token-admin");
        verify(loginAttemptService).resetAttempts("admin@lumintra.com");

        ArgumentCaptor<StaffLoginSuccessEvent> eventCaptor = ArgumentCaptor.forClass(StaffLoginSuccessEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(201L);
        assertThat(eventCaptor.getValue().email()).isEqualTo("admin@lumintra.com");
        assertThat(eventCaptor.getValue().role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("UTCID03 [A]: Login rejected when account is currently locked (423 Locked)")
    void login_accountLockedThrowsException() {
        LoginRequest request = new LoginRequest("locked@example.com", "P@ssword123");

        when(loginAttemptService.isAccountLocked("locked@example.com")).thenReturn(true);

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage("Account is locked for 15 minutes.");

        verifyNoInteractions(userAccountRepository, passwordEncoder, tokenProvider);
    }

    @Test
    @DisplayName("UTCID04 [A]: Login fails with non-existent email, increments attempt, throws InvalidCredentialsException (401)")
    void login_nonExistentEmailThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("unknown@example.com", "P@ssword123");

        when(loginAttemptService.isAccountLocked("unknown@example.com")).thenReturn(false);
        when(userAccountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(loginAttemptService.registerFailedAttempt("unknown@example.com")).thenReturn(1);

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(loginAttemptService).registerFailedAttempt("unknown@example.com");
        verifyNoInteractions(tokenProvider);
    }

    @Test
    @DisplayName("UTCID05 [A]: Login fails with incorrect password below lockout threshold (attempt 4 < 5)")
    void login_wrongPasswordBelowLockoutThreshold() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        UserAccountEntity account = new UserAccountEntity();
        account.setAccountId(105L);
        account.setEmail("user@example.com");
        account.setPasswordHash("$2a$10$hashedPassword");
        account.setAccountType(AccountType.CUSTOMER);

        when(loginAttemptService.isAccountLocked("user@example.com")).thenReturn(false);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPassword")).thenReturn(false);
        when(loginAttemptService.registerFailedAttempt("user@example.com")).thenReturn(4);

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID06 [B]: Fifth failed attempt triggers lockout threshold and publishes StaffAccountLockedEvent for employee")
    void login_fifthFailedAttemptTriggersLockoutAndStaffEvent() {
        LoginRequest request = new LoginRequest("staff@lumintra.com", "WrongPassword");

        UserAccountEntity employeeAccount = new UserAccountEntity();
        employeeAccount.setAccountId(202L);
        employeeAccount.setEmail("staff@lumintra.com");
        employeeAccount.setPasswordHash("$2a$10$hashedPassword");
        employeeAccount.setAccountType(AccountType.EMPLOYEE);

        RoleEntity role = new RoleEntity();
        role.setRoleName("SUPPORT");
        EmployeeProfileEntity profile = new EmployeeProfileEntity();
        profile.setAccount(employeeAccount);
        profile.setRole(role);

        when(loginAttemptService.isAccountLocked("staff@lumintra.com")).thenReturn(false);
        when(userAccountRepository.findByEmail("staff@lumintra.com")).thenReturn(Optional.of(employeeAccount));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPassword")).thenReturn(false);
        when(loginAttemptService.registerFailedAttempt("staff@lumintra.com")).thenReturn(5);
        when(employeeProfileRepository.findByAccount_AccountId(202L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        ArgumentCaptor<StaffAccountLockedEvent> eventCaptor = ArgumentCaptor.forClass(StaffAccountLockedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(202L);
        assertThat(eventCaptor.getValue().email()).isEqualTo("staff@lumintra.com");
        assertThat(eventCaptor.getValue().role()).isEqualTo("SUPPORT");
    }

    @Test
    @DisplayName("UTCID07 [A]: Validation fails when LoginRequest email format is invalid")
    void validateLoginRequest_invalidEmail() {
        LoginRequest request = new LoginRequest("not-an-email", "P@ssword123");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Invalid email format");
    }

    @Test
    @DisplayName("UTCID08 [A]: Validation fails when LoginRequest fields are blank")
    void validateLoginRequest_blankFields() {
        LoginRequest request = new LoginRequest("   ", "");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Email is required", "Password is required");
    }
}
