package com.lumintra.backend.device.domain.service;

/**
 * Domain service exposed by Device feature to verify physical hardware ownership without leaking persistence entities.
 */
public interface DeviceOwnershipService {

    /**
     * Checks whether the specified LED hardware device is bound to the given customer ID.
     *
     * @param deviceId unique instance ID of the hardware device
     * @param customerId unique ID of the customer account
     * @return true if device exists and belongs to customerId; false otherwise
     */
    boolean isDeviceOwnedByCustomer(Long deviceId, Long customerId);
}
