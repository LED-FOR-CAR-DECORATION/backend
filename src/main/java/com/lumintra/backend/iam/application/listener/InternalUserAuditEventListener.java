package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.InternalUserCreatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InternalUserAuditEventListener {
    private static final Logger log = LoggerFactory.getLogger(InternalUserAuditEventListener.class);

    @TransactionalEventListener
    public void onInternalUserCreated(InternalUserCreatedEvent event) {
        log.info(
                "Audit: Internal user created [createdBy={}] [accountId={}] [role={}]"
                        + " [createdAt={}]",
                event.createdBy(),
                event.accountId(),
                event.role(),
                event.createdAt());
    }
}
