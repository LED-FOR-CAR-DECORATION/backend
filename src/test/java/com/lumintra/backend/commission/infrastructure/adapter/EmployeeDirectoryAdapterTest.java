package com.lumintra.backend.commission.infrastructure.adapter;

import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeDirectoryAdapterTest {

    private final EmployeeLookupService lookup = mock(EmployeeLookupService.class);
    private final EmployeeDirectoryAdapter adapter = new EmployeeDirectoryAdapter(lookup);

    @Test
    @DisplayName("CMS-25 AC02 [N]: isStaff delegates to the IAM active-STAFF lookup")
    void isStaffDelegates() {
        when(lookup.isActiveStaff(4L)).thenReturn(true);

        assertThat(adapter.isStaff(4L)).isTrue();
        assertThat(adapter.isStaff(5L)).isFalse();
    }

    @Test
    @DisplayName("CMS-25 AC02 [B]: null and non-positive ids are never staff and skip the lookup")
    void isStaffRejectsInvalidIds() {
        assertThat(adapter.isStaff(null)).isFalse();
        assertThat(adapter.isStaff(0L)).isFalse();
        assertThat(adapter.isStaff(-1L)).isFalse();
        verify(lookup, never()).isActiveStaff(any());
    }
}
