package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.api.request.UpdateManagedUserRequest;
import com.lumintra.backend.iam.domain.event.ManagedUserUpdatedEvent;
import com.lumintra.backend.iam.domain.model.*;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.InvalidManagedUserRoleException;

import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

class UpdateManagedUserUseCaseTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UpdateManagedUserUseCase useCase =
            new UpdateManagedUserUseCase(
                    accounts, employees, roles, refresh, events, "root@example.test");
    private final UserAccountEntity target = new UserAccountEntity();
    private final EmployeeEntity profile = new EmployeeEntity();
    private final RoleEntity admin = role("ADMIN");

    @BeforeEach
    void setUp() {
        var actor = new UserAccountEntity();
        actor.setAccountType(AccountType.EMPLOYEE);
        actor.setStatus(AccountStatus.ACTIVE);
        var employee = new EmployeeEntity();
        employee.setRole(admin);
        when(accounts.findById(1L)).thenReturn(Optional.of(actor));
        when(employees.findByAccount_AccountId(1L)).thenReturn(Optional.of(employee));
        target.setEmail("staff@example.test");
        target.setAccountType(AccountType.EMPLOYEE);
        target.setStatus(AccountStatus.ACTIVE);
        profile.setRole(role("STAFF"));
        when(accounts.findForManagementUpdate(2L)).thenReturn(Optional.of(target));
        when(employees.findByAccount_AccountId(2L)).thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("N: Update profile and role, suspend the target and invalidate its sessions")
    void updatesAndSuspends() {
        var manager = role("MANAGER");
        when(roles.findByRoleName("MANAGER")).thenReturn(Optional.of(manager));
        useCase.execute(
                "1",
                2,
                new UpdateManagedUserRequest(
                        " New Name ",
                        "+84901234567",
                        "https://example.test/avatar",
                        " manager ",
                        true));
        assertThat(profile.getFullName()).isEqualTo("New Name");
        assertThat(profile.getRole()).isSameAs(manager);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(target.getUpdatedAt()).isNotNull();
        verify(refresh).revokeAllTokensForAccount(2L);
        verify(events)
                .publishEvent(
                        new ManagedUserUpdatedEvent(
                                1,
                                2,
                                Set.of("name", "phone", "avatar", "role", "locked"),
                                target.getUpdatedAt()));
    }

    @Test
    @DisplayName(
            "B: Unlock a manual suspension, preserving timed lockout and ignoring a repeated"
                + " update")
    void unlocksAndRepeats() {
        target.setStatus(AccountStatus.SUSPENDED);
        var deadline = Instant.parse("2026-11-11T09:00:00Z");
        target.setLockoutUntil(deadline);
        var request = new UpdateManagedUserRequest(null, null, null, null, false);
        useCase.execute("1", 2, request);
        var revision = target.getUpdatedAt();
        useCase.execute("1", 2, request);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getLockoutUntil()).isEqualTo(deadline);
        assertThat(target.getUpdatedAt()).isEqualTo(revision);
        verify(refresh).revokeAllTokensForAccount(2L);
        verify(events).publishEvent(new ManagedUserUpdatedEvent(1, 2, Set.of("locked"), revision));
    }

    @Test
    @DisplayName(
            "A: Reject customer targets, self-updates, protected admin changes and stale admin"
                + " roles")
    void protectsUsers() {
        var request = new UpdateManagedUserRequest(null, null, null, "STAFF", true);
        assertThatThrownBy(() -> useCase.execute("1", 1, request))
                .isInstanceOf(AccessDeniedException.class);
        target.setAccountType(AccountType.CUSTOMER);
        assertThatThrownBy(() -> useCase.execute("1", 2, request))
                .isInstanceOf(AccessDeniedException.class);
        target.setAccountType(AccountType.EMPLOYEE);
        target.setEmail("ROOT@example.test");
        assertThatThrownBy(() -> useCase.execute("1", 2, request))
                .isInstanceOf(AccessDeniedException.class);
        admin.setRoleName("MANAGER");
        assertThatThrownBy(() -> useCase.execute("1", 2, request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles, refresh, events);
    }

    @Test
    @DisplayName("A: Reject unknown or customer roles and preserve deleted account state")
    void rejectsInvalidRolesAndDeletedUsers() {
        for (String role : new String[] {"CUSTOMER", "UNKNOWN"}) {
            var request = new UpdateManagedUserRequest(null, null, null, role, null);
            assertThatThrownBy(() -> useCase.execute("1", 2, request))
                    .isInstanceOf(InvalidManagedUserRoleException.class);
        }
        target.setStatus(AccountStatus.DELETED);
        var unlock = new UpdateManagedUserRequest(null, null, null, null, false);
        assertThatThrownBy(() -> useCase.execute("1", 2, unlock))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.DELETED);
        verifyNoInteractions(refresh, events);
    }

    private static RoleEntity role(String name) {
        var role = new RoleEntity();
        role.setRoleName(name);
        return role;
    }
}
