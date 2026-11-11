package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.ManagedUserUpdatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ManagedUserUpdateAuditListener {
    private static final Logger log = LoggerFactory.getLogger(ManagedUserUpdateAuditListener.class);

    @TransactionalEventListener
    public void onUserUpdated(ManagedUserUpdatedEvent event) {
        log.info(
                "Audit: Internal user updated [actorId={}] [accountId={}] [changedFields={}]"
                    + " [updatedAt={}]",
                event.actorId(),
                event.accountId(),
                event.changedFields(),
                event.updatedAt());
    }
}
