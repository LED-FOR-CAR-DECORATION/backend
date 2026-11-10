package com.lumintra.backend.iam.domain.service;

import com.lumintra.backend.iam.domain.model.UserAddressSnapshot;

import java.util.Optional;

/**
 * Read-only domain boundary exposed by IAM for verifying and retrieving a customer's saved delivery address.
 */
public interface UserAddressLookupService {
    Optional<UserAddressSnapshot> findByIdAndCustomerId(Long addressId, Long customerId);
}
