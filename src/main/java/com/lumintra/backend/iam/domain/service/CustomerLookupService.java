package com.lumintra.backend.iam.domain.service;

import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only domain boundary exposed by IAM for retrieving customer contact summaries without leaking persistence internals.
 */
public interface CustomerLookupService {

    Optional<CustomerProfileSnapshot> findProfileByCustomerId(Long customerId);

    Map<Long, CustomerProfileSnapshot> findProfilesByCustomerIds(Collection<Long> customerIds);
}
