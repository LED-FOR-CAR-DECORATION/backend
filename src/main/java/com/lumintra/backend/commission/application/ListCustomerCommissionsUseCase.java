package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListCustomerCommissionsUseCase {

    private final CommissionRequestRepository commissionRepository;
    private final CommissionRequestMapper commissionMapper;

    public CustomerCommissionListResponse execute(Long customerId, CustomerCommissionFilterRequest request) {
        if (customerId == null || customerId <= 0) {
            throw new InsufficientAuthenticationException("Authenticated customer identity is required.");
        }

        CustomerCommissionFilterRequest effectiveFilter = request != null
                ? request
                : new CustomerCommissionFilterRequest();

        Pageable pageable = effectiveFilter.toPageable();

        Page<CommissionRequestEntity> page = effectiveFilter.status() != null
                ? commissionRepository.findAllByCustomerIdAndStatus(customerId, effectiveFilter.status(), pageable)
                : commissionRepository.findAllByCustomerId(customerId, pageable);

        return commissionMapper.toCustomerListResponse(page);
    }
}
