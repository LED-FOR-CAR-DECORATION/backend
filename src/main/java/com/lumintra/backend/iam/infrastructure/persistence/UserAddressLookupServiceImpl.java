package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.UserAddressSnapshot;
import com.lumintra.backend.iam.domain.service.UserAddressLookupService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserAddressLookupServiceImpl implements UserAddressLookupService {

    private final UserAddressRepository userAddressRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAddressSnapshot> findByIdAndCustomerId(Long addressId, Long customerId) {
        if (addressId == null || customerId == null) {
            return Optional.empty();
        }
        return userAddressRepository.findByAddressIdAndCustomerId(addressId, customerId)
                .map(this::toSnapshot);
    }

    private UserAddressSnapshot toSnapshot(UserAddressEntity entity) {
        return new UserAddressSnapshot(
                entity.getAddressId(),
                entity.getCustomerId(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getFullAddress(),
                entity.getLatitude(),
                entity.getLongitude()
        );
    }
}
