package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.domain.event.CustomerRegisteredEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerProfileEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerProfileRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterAccountUseCaseTest {

    private static Validator validator;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpService otpService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegisterAccountUseCase registerAccountUseCase;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("UTCID01 [N]: Register customer account with standard valid email and password")
    void utcid01_registerCustomerAccount_normalSuccess() {
        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "P@ssword123",
                "P@ssword123"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setAccountId(101L);
            return entity;
        });
        when(otpService.generateAndStoreOtp("customer@example.com", OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn("654321");

        RegisterResponse response = registerAccountUseCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getEmail()).isEqualTo("customer@example.com");
        assertThat(response.getAccountType()).isEqualTo(AccountType.CUSTOMER.name());
        assertThat(response.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION.name());

        ArgumentCaptor<CustomerProfileEntity> profileCaptor = ArgumentCaptor.forClass(CustomerProfileEntity.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getAccount().getAccountId()).isEqualTo(101L);

        ArgumentCaptor<CustomerRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(CustomerRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(101L);
        assertThat(eventCaptor.getValue().email()).isEqualTo("customer@example.com");
        assertThat(eventCaptor.getValue().otp()).isEqualTo("654321");
    }

    @Test
    @DisplayName("UTCID02 [N]: Register customer account normalizes whitespace and uppercase email")
    void utcid02_registerCustomerAccount_normalizesEmail() {
        RegisterRequest request = new RegisterRequest(
                "  Customer.New@Example.COM  ",
                "P@ssword123",
                "P@ssword123"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("customer.new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setAccountId(102L);
            return entity;
        });
        when(otpService.generateAndStoreOtp("customer.new@example.com", OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn("112233");

        RegisterResponse response = registerAccountUseCase.execute(request);

        assertThat(response.getId()).isEqualTo(102L);
        assertThat(response.getEmail()).isEqualTo("customer.new@example.com");
        assertThat(response.getAccountType()).isEqualTo("CUSTOMER");
        assertThat(response.getStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    @DisplayName("UTCID03 [B]: Register customer account with minimum 8-character password boundary")
    void utcid03_registerCustomerAccount_minBoundaryPassword() {
        String minPassword = "Aa1@abcd"; // Exactly 8 characters
        RegisterRequest request = new RegisterRequest(
                "a@b.co",
                minPassword,
                minPassword
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();

        when(userAccountRepository.existsByEmailIgnoreCase("a@b.co")).thenReturn(false);
        when(passwordEncoder.encode(minPassword)).thenReturn("$2a$10$minHash");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setAccountId(103L);
            return entity;
        });
        when(otpService.generateAndStoreOtp(eq("a@b.co"), eq(OtpPurpose.EMAIL_VERIFICATION)))
                .thenReturn("999888");

        RegisterResponse response = registerAccountUseCase.execute(request);

        assertThat(response.getId()).isEqualTo(103L);
        assertThat(response.getEmail()).isEqualTo("a@b.co");
    }

    @Test
    @DisplayName("UTCID04 [B]: Register customer account with maximum 72-character password boundary")
    void utcid04_registerCustomerAccount_maxBoundaryPassword() {
        String maxPassword = "Aa1@" + "x".repeat(68); // Exactly 72 characters
        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                maxPassword,
                maxPassword
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();

        when(userAccountRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode(maxPassword)).thenReturn("$2a$10$maxHash");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setAccountId(104L);
            return entity;
        });
        when(otpService.generateAndStoreOtp("customer@example.com", OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn("777666");

        RegisterResponse response = registerAccountUseCase.execute(request);

        assertThat(response.getId()).isEqualTo(104L);
        assertThat(response.getStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    @DisplayName("UTCID05 [A]: Reject registration when email is already registered (409 Conflict)")
    void utcid05_registerCustomerAccount_duplicateEmailThrowsConflict() {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com",
                "P@ssword123",
                "P@ssword123"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registerAccountUseCase.execute(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered");

        verify(userAccountRepository, never()).save(any());
        verify(customerProfileRepository, never()).save(any());
        verify(otpService, never()).generateAndStoreOtp(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Validation fails when password and confirmPassword do not match")
    void utcid06_validateRegisterRequest_passwordMismatch() {
        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "P@ssword123",
                "Different@123"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Passwords do not match");
    }

    @Test
    @DisplayName("UTCID07 [A]: Validation fails when email format is invalid")
    void utcid07_validateRegisterRequest_invalidEmailFormat() {
        RegisterRequest request = new RegisterRequest(
                "not-an-email",
                "P@ssword123",
                "P@ssword123"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Invalid email format");
    }

    @Test
    @DisplayName("UTCID08 [A]: Validation fails when password is shorter than 8 characters (7 chars)")
    void utcid08_validateRegisterRequest_passwordTooShort() {
        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "Aa1@abc", // 7 chars
                "Aa1@abc"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .anyMatch(msg -> msg.contains("Password must be 8-72 characters long"));
    }
}
