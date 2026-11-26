package com.lumintra.backend.notification.domain.service;

public interface InternalUserEmailProvider {
    void sendCredentials(String email, String password, String deliveryId);
}
