package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeleteUserAddressUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteUserAddressUseCase.class);
    private static final Pageable FIRST_ACTIVE_ADDRESS = PageRequest.of(0, 1);

    private final UserAccountRepository userAccountRepository;
    private final UserAddressRepository userAddressRepository;

    @Transactional
    public void execute(String subject, Long addressId) {
        long customerId = parseCustomerId(subject);

        var account = userAccountRepository.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException("Account not found."));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active.");
        }
        if (account.getAccountType() != AccountType.CUSTOMER) {
            throw new AccessDeniedException("Customer account is required.");
        }

        if (addressId == null || addressId <= 0) {
            log.warn("Invalid address identifier requested [addressId={}] [customerId={}]",
                    addressId, customerId);
            throw new AddressNotFoundException("Delivery address not found.");
        }

        UserAddressEntity entity = userAddressRepository
                .findByAddressIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> {
                    log.warn("Customer address not found or access denied [addressId={}] [customerId={}]",
                            addressId, customerId);
                    return new AddressNotFoundException("Delivery address not found.");
                });

        boolean wasDefault = entity.isDefault();
        entity.softDelete(Instant.now());
        userAddressRepository.saveAndFlush(entity);

        Long promotedDefaultAddressId = null;
        if (wasDefault) {
            List<UserAddressEntity> candidates =
                    userAddressRepository.findActiveByCustomerIdOrdered(customerId, FIRST_ACTIVE_ADDRESS);
            if (!candidates.isEmpty()) {
                UserAddressEntity nextDefault = candidates.getFirst();
                nextDefault.markDefault();
                userAddressRepository.saveAndFlush(nextDefault);
                promotedDefaultAddressId = nextDefault.getAddressId();
            }
        }

        log.info(
                "Customer address soft deleted [customerId={}] [addressId={}] [promotedDefaultAddressId={}]",
                customerId,
                addressId,
                promotedDefaultAddressId);
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
