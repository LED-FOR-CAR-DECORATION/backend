package com.lumintra.backend.iam.application.listener;

import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.domain.event.StaffPasswordChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class StaffAuditEventListenerTest {

    private StaffAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new StaffAuditEventListener();
    }

    @Test
    @DisplayName("UTCID01 [N]: onStaffLoginSuccess handles staff login success event without exception")
    void onStaffLoginSuccess_handlesEventSuccessfully() {
        StaffLoginSuccessEvent event = new StaffLoginSuccessEvent(100L, "manager@lumintra.com", "MANAGER");

        assertThatCode(() -> listener.onStaffLoginSuccess(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UTCID02 [N]: onStaffAccountLocked handles staff account locked event without exception")
    void onStaffAccountLocked_handlesEventSuccessfully() {
        StaffAccountLockedEvent event = new StaffAccountLockedEvent(100L, "technician@lumintra.com", "TECHNICIAN");

        assertThatCode(() -> listener.onStaffAccountLocked(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UTCID03 [N]: onStaffPasswordChanged handles staff password changed audit event without exception")
    void onStaffPasswordChanged_handlesEventSuccessfully() {
        StaffPasswordChangedEvent event = new StaffPasswordChangedEvent(100L, "admin@lumintra.com", "ADMIN");

        assertThatCode(() -> listener.onStaffPasswordChanged(event))
                .doesNotThrowAnyException();
    }
}
