package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.domain.event.UserSoftDeletedEvent;
import com.lumintra.backend.iam.domain.model.*;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

class SoftDeleteUserUseCaseTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SoftDeleteUserUseCase useCase =
            new SoftDeleteUserUseCase(accounts, employees, refresh, events, "default@example.test");
    private final UserAccountEntity actor = new UserAccountEntity();
    private final RoleEntity role = new RoleEntity();

    @BeforeEach
    void authorize() {
        actor.setStatus(AccountStatus.ACTIVE);
        actor.setAccountType(AccountType.EMPLOYEE);
        role.setRoleName("ADMIN");
        var employee = new EmployeeEntity();
        employee.setRole(role);
        when(accounts.findById(1L)).thenReturn(Optional.of(actor));
        when(employees.findByAccount_AccountId(1L)).thenReturn(Optional.of(employee));
    }

    @Test
    @DisplayName(
            "N: Deactivate the account, revoke refresh tokens and publish the supplied audit"
                    + " reason")
    void deletesAndAudits() {
        var target = target("staff@example.test", AccountStatus.ACTIVE);
        useCase.execute("1", 2, UserDeletionReason.USER_REQUEST);

        assertThat(target.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(target.getUpdatedAt()).isNotNull();
        verify(refresh).revokeAllTokensForAccount(2L);
        var event = ArgumentCaptor.forClass(UserSoftDeletedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue())
                .isEqualTo(
                        new UserSoftDeletedEvent(
                                1, 2, UserDeletionReason.USER_REQUEST, target.getUpdatedAt()));
        verify(accounts, never()).delete(any());
    }

    @Test
    @DisplayName(
            "B: Repeat deletion preserves the original timestamp and creates no duplicate audit")
    void repeatedDeletionIsIdempotent() {
        var target = target("staff@example.test", AccountStatus.DELETED);
        var timestamp = java.time.Instant.parse("2026-11-11T00:00:00Z");
        target.setUpdatedAt(timestamp);
        useCase.execute("1", 2, UserDeletionReason.OTHER);
        assertThat(target.getUpdatedAt()).isEqualTo(timestamp);
        verifyNoInteractions(refresh, events);
    }

    @Test
    @DisplayName(
            "A: Reject self-deletion and protect the configured default administrator"
                    + " case-insensitively")
    void protectsAccounts() {
        assertThatThrownBy(() -> useCase.execute("1", 1, UserDeletionReason.OTHER))
                .isInstanceOf(AccessDeniedException.class);
        verify(accounts, never()).findForDeletion(1);
        var target = target("DEFAULT@example.test", AccountStatus.ACTIVE);
        assertThatThrownBy(() -> useCase.execute("1", 2, UserDeletionReason.OTHER))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNoInteractions(refresh, events);
    }

    @Test
    @DisplayName(
            "A: Reject stale administrator authority and missing targets before revoking tokens")
    void validatesCurrentAuthorityAndTarget() {
        role.setRoleName("MANAGER");
        assertThatThrownBy(() -> useCase.execute("1", 2, UserDeletionReason.OTHER))
                .isInstanceOf(AccessDeniedException.class);
        verify(accounts, never()).findForDeletion(2);
        role.setRoleName("ADMIN");
        assertThatThrownBy(() -> useCase.execute("1", 2, UserDeletionReason.OTHER))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(refresh, events);
    }

    private UserAccountEntity target(String email, AccountStatus status) {
        var account = new UserAccountEntity();
        account.setAccountId(2L);
        account.setEmail(email);
        account.setStatus(status);
        when(accounts.findForDeletion(2L)).thenReturn(Optional.of(account));
        return account;
    }
}
