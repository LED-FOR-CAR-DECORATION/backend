package com.lumintra.backend.notification.application;

import com.lumintra.backend.iam.domain.event.InternalUserCreatedEvent;
import com.lumintra.backend.notification.domain.service.InternalUserEmailProvider;
import com.lumintra.backend.shared.exception.notification.InternalUserEmailUnavailableException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InternalUserNotificationListener {
    private static final Logger log =
            LoggerFactory.getLogger(InternalUserNotificationListener.class);
    private final InternalUserEmailProvider provider;

    @Async
    @TransactionalEventListener
    public void onInternalUserCreated(InternalUserCreatedEvent event) {
        try {
            provider.sendCredentials(event.email(), event.password(), event.deliveryId());
            log.info("Internal account email accepted [accountId={}]", event.accountId());
        } catch (InternalUserEmailUnavailableException ex) {
            // Provider exceptions may embed credentials or recipient data; never log their
            // payloads.
            log.error(
                    "Internal account email delivery failed [accountId={}] [deliveryId={}]",
                    event.accountId(),
                    event.deliveryId());
        }
    }
}
