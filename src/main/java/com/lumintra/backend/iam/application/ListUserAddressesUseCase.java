package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.UserAddressMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListUserAddressesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListUserAddressesUseCase.class);

    private final UserAccountRepository userAccountRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserAddressMapper userAddressMapper;

    @Transactional(readOnly = true)
    public List<UserAddressResponse> execute(String subject) {
        long customerId = parseCustomerId(subject);

        var account = userAccountRepository.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException("Account not found."));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active.");
        }
        if (account.getAccountType() != AccountType.CUSTOMER) {
            throw new AccessDeniedException("Customer account is required.");
        }

        List<UserAddressEntity> entities = userAddressRepository.findAllByCustomerIdOrdered(customerId);
        List<UserAddressResponse> responses = userAddressMapper.toResponseList(entities);

        log.info("Customer addresses retrieved [customerId={}] [count={}]", customerId, responses.size());
        return responses;
    }

    private long parseCustomerId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        final long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (id <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        return id;
    }
}
