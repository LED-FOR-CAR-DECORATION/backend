package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.UserDetailViewedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserDetailAuditEventListener {
    private static final Logger log = LoggerFactory.getLogger(UserDetailAuditEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDetailViewed(UserDetailViewedEvent event) {
        log.info(
                "Audit: Internal user detail viewed [viewerAccountId={}] [targetAccountId={}]"
                        + " [viewerRole={}] [viewedAt={}]",
                event.viewerAccountId(),
                event.targetAccountId(),
                event.viewerRole(),
                event.viewedAt());
    }
}
