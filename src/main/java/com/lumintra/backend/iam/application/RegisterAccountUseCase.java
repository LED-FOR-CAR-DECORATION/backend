package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.domain.event.CustomerRegisteredEvent;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterAccountUseCase.class);
    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RegisterResponse execute(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: Email already exists [email={}]", normalizedEmail);
            throw new EmailAlreadyRegisteredException("Email is already registered");
        }

        UserAccountEntity account = UserAccountEntity.createCustomerAccount(
                normalizedEmail,
                passwordEncoder.encode(request.password())
        );
        UserAccountEntity savedAccount = userAccountRepository.save(account);

        CustomerEntity customer = CustomerEntity.createCustomerFor(savedAccount);
        customerRepository.save(customer);

        String otp = otpService.generateAndStoreOtp(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION);

        log.info("Customer registered successfully (Pending Verification) [accountId={}] [email={}]", savedAccount.getAccountId(), normalizedEmail);

        eventPublisher.publishEvent(new CustomerRegisteredEvent(savedAccount.getAccountId(), normalizedEmail, null, otp));

        return RegisterResponse.from(savedAccount);
    }
}
