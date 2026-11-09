package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.VerifyEmailRequest;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.InvalidOtpException;
import com.lumintra.backend.shared.exception.iam.MaxOtpAttemptsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyEmailUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private VerifyEmailUseCase verifyEmailUseCase;

    @Test
    @DisplayName("UTCID01 [N]: Verify email successfully with valid OTP and transition account status to ACTIVE")
    void execute_validOtp_activatesAccountSuccessfully() {
        String email = "customer@example.com";
        String otp = "123456";
        VerifyEmailRequest request = new VerifyEmailRequest(email, otp);

        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.validateOtp(email, otp, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(true);

        verifyEmailUseCase.execute(request);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userAccountRepository).save(account);
    }

    @Test
    @DisplayName("UTCID02 [N]: Normalizes email with leading/trailing whitespace and uppercase characters")
    void execute_normalizesWhitespaceAndUppercaseEmail() {
        String inputEmail = "  Customer.Verify@Example.COM  ";
        String normalizedEmail = "customer.verify@example.com";
        String otp = "123456";
        VerifyEmailRequest request = new VerifyEmailRequest(inputEmail, otp);

        UserAccountEntity account = UserAccountEntity.createCustomerAccount(normalizedEmail, "hashed_pw");

        when(userAccountRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(account));
        when(otpService.validateOtp(normalizedEmail, otp, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(true);

        verifyEmailUseCase.execute(request);

        verify(userAccountRepository).findByEmail(normalizedEmail);
        verify(otpService).validateOtp(normalizedEmail, otp, OtpPurpose.EMAIL_VERIFICATION);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userAccountRepository).save(account);
    }

    @Test
    @DisplayName("UTCID03 [B]: Boundary 6-digit OTP values (all zeros '000000' and all nines '999999')")
    void execute_otpBoundaryDigitsZeroPaddedAndAllNines() {
        String email = "boundary@example.com";

        // Boundary 1: "000000"
        VerifyEmailRequest reqMin = new VerifyEmailRequest(email, "000000");
        UserAccountEntity account1 = UserAccountEntity.createCustomerAccount(email, "hash1");
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account1));
        when(otpService.validateOtp(email, "000000", OtpPurpose.EMAIL_VERIFICATION)).thenReturn(true);

        verifyEmailUseCase.execute(reqMin);
        assertThat(account1.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Boundary 2: "999999"
        VerifyEmailRequest reqMax = new VerifyEmailRequest(email, "999999");
        UserAccountEntity account2 = UserAccountEntity.createCustomerAccount(email, "hash2");
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account2));
        when(otpService.validateOtp(email, "999999", OtpPurpose.EMAIL_VERIFICATION)).thenReturn(true);

        verifyEmailUseCase.execute(reqMax);
        assertThat(account2.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("UTCID04 [A]: Reject verification when user account is not found in repository (404)")
    void execute_userNotFound_throwsUserNotFoundException() {
        String email = "notfound@example.com";
        VerifyEmailRequest request = new VerifyEmailRequest(email, "123456");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Account not found");

        verifyNoInteractions(otpService);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: Reject verification when account is already ACTIVE (400)")
    void execute_accountAlreadyActive_throwsAccountAlreadyVerifiedException() {
        String email = "active@example.com";
        VerifyEmailRequest request = new VerifyEmailRequest(email, "123456");

        UserAccountEntity activeAccount = UserAccountEntity.createCustomerAccount(email, "hash");
        activeAccount.verifyEmail(); // Status becomes ACTIVE

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("Account is already verified");

        verifyNoInteractions(otpService);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Reject verification when OTP is invalid or expired (400)")
    void execute_invalidOtp_throwsInvalidOtpException() {
        String email = "customer@example.com";
        VerifyEmailRequest request = new VerifyEmailRequest(email, "654321");

        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hash");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.validateOtp(email, "654321", OtpPurpose.EMAIL_VERIFICATION)).thenReturn(false);

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired OTP.");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID07 [A]: Propagate MaxOtpAttemptsException when brute force retry threshold exceeded (429)")
    void execute_maxOtpAttemptsReached_propagatesMaxOtpAttemptsException() {
        String email = "customer@example.com";
        VerifyEmailRequest request = new VerifyEmailRequest(email, "999999");

        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hash");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.validateOtp(email, "999999", OtpPurpose.EMAIL_VERIFICATION))
                .thenThrow(new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code."));

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(MaxOtpAttemptsException.class)
                .hasMessage("Maximum OTP attempts reached. Please request a new code.");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        verify(userAccountRepository, never()).save(any());
    }
}
