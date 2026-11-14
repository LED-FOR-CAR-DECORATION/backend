package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.AddUserAddressRequest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AddUserAddressUseCase {

    private static final Logger log = LoggerFactory.getLogger(AddUserAddressUseCase.class);
    private static final int COORDINATE_SCALE = 7;

    private final UserAccountRepository userAccountRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserAddressMapper userAddressMapper;

    @Transactional
    public UserAddressResponse execute(String subject, AddUserAddressRequest request) {
        long customerId = parseCustomerId(subject);

        var account = userAccountRepository.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException("Account not found."));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active.");
        }
        if (account.getAccountType() != AccountType.CUSTOMER) {
            throw new AccessDeniedException("Customer account is required.");
        }

        boolean hasExistingAddresses =
                userAddressRepository.existsByCustomerIdAndIsDeletedFalse(customerId);
        boolean effectiveIsDefault = !hasExistingAddresses || Boolean.TRUE.equals(request.isDefault());

        if (hasExistingAddresses && effectiveIsDefault) {
            userAddressRepository.clearDefaultByCustomerId(customerId);
        }

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());

        UserAddressEntity entity = UserAddressEntity.create(
                customerId,
                request.recipientName(),
                request.recipientPhone(),
                request.fullAddress(),
                latitude,
                longitude,
                effectiveIsDefault
        );

        UserAddressEntity saved = userAddressRepository.saveAndFlush(entity);
        UserAddressResponse response = userAddressMapper.toResponse(saved);

        log.info(
                "Customer address created [customerId={}] [addressId={}] [isDefault={}]",
                customerId,
                response.addressId(),
                response.isDefault());
        return response;
    }

    private BigDecimal normalizeCoordinate(BigDecimal coordinate) {
        return coordinate.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
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
