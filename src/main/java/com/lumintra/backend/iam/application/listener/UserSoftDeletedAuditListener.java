package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.UserSoftDeletedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserSoftDeletedAuditListener {
    private static final Logger log = LoggerFactory.getLogger(UserSoftDeletedAuditListener.class);

    @TransactionalEventListener
    public void onUserSoftDeleted(UserSoftDeletedEvent event) {
        log.info(
                "Audit: User soft deleted [actorId={}] [accountId={}] [reason={}] [deletedAt={}]",
                event.actorId(),
                event.accountId(),
                event.reason(),
                event.deletedAt());
    }
}
