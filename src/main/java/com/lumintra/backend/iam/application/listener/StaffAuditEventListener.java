package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StaffAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(StaffAuditEventListener.class);

    @EventListener
    public void onStaffLoginSuccess(StaffLoginSuccessEvent event) {
        //TODO Write audit events to database asynchronously
        log.info("Audit: Staff login successful [userId={}] [email={}] [role={}]",
                event.userId(), event.email(), event.role());
    }

    @EventListener
    public void onStaffAccountLocked(StaffAccountLockedEvent event) {
        //TODO Write audit events to database asynchronously
        log.warn("Audit: Staff account locked [userId={}] [email={}] [role={}] [duration=15m]",
                event.userId(), event.email(), event.role());
    }
}
