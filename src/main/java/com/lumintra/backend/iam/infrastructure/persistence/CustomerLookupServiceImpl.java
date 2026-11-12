package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerLookupServiceImpl implements CustomerLookupService {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfileSnapshot> findProfileByCustomerId(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(findProfilesByCustomerIds(Set.of(customerId)).get(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CustomerProfileSnapshot> findProfilesByCustomerIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> validIds = customerIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return customerRepository.findProfileSnapshotsByCustomerIds(validIds).stream()
                .collect(Collectors.toMap(
                        CustomerProfileSnapshot::customerId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }
}
