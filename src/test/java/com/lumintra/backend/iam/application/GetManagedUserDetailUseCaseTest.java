package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.domain.event.UserDetailViewedEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserDetailMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class GetManagedUserDetailUseCaseTest {
    @Mock UserAccountRepository accounts;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    ManagedUserDetailMapper mapper;
    GetManagedUserDetailUseCase useCase;
    EmployeeEntity viewer;
    EmployeeEntity target;

    @BeforeEach
    void setup() {
        mapper = spy(new ManagedUserDetailMapper());
        useCase = new GetManagedUserDetailUseCase(accounts, employees, mapper, events);
        viewer = employee(1L, "ADMIN");
        target = employee(42L, "STAFF");
    }

    EmployeeEntity employee(long id, String roleName) {
        var account =
                UserAccountEntity.createCustomerAccount(
                        "user" + id + "@example.com", "private-password-hash");
        account.setAccountId(id);
        account.setAccountType(AccountType.EMPLOYEE);
        account.setStatus(AccountStatus.ACTIVE);
        var employee = new EmployeeEntity();
        employee.setAccount(account);
        employee.setFullName("Employee " + id);
        var role = new RoleEntity();
        role.setRoleName(roleName);
        employee.setRole(role);
        return employee;
    }

    void authorize() {
        when(accounts.findById(1L)).thenReturn(Optional.of(viewer.getAccount()));
        when(employees.findByAccount_AccountId(1L)).thenReturn(Optional.of(viewer));
    }

    void target() {
        when(accounts.findById(42L)).thenReturn(Optional.of(target.getAccount()));
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(target));
    }

    @Test
    @DisplayName(
            "N: Admin and manager can view inactive locked staff and publish one safe audit event"
                    + " per view")
    void otherUserAudit() {
        authorize();
        target();
        target.getAccount().setStatus(AccountStatus.SUSPENDED);
        target.getAccount().setLockoutUntil(Instant.now().plusSeconds(900));
        target.getAccount().setFailedAttempts(5);
        for (String role : new String[] {"ADMIN", "MANAGER"}) {
            viewer.getRole().setRoleName(role);
            var response = useCase.execute("1", 42L);
            assertThat(response.accountId()).isEqualTo(42L);
            assertThat(response.status()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(response.lockout().locked()).isTrue();
            assertThat(response.lockout().failedAttempts()).isEqualTo(5);
            assertThat(response.role()).isEqualTo("STAFF");
        }
        var capture = ArgumentCaptor.forClass(UserDetailViewedEvent.class);
        verify(events, times(2)).publishEvent(capture.capture());
        assertThat(capture.getAllValues())
                .extracting(UserDetailViewedEvent::viewerRole)
                .containsExactly("ADMIN", "MANAGER");
        assertThat(capture.getAllValues())
                .allSatisfy(
                        event -> {
                            assertThat(event.viewerAccountId()).isEqualTo(1L);
                            assertThat(event.targetAccountId()).isEqualTo(42L);
                            assertThat(event.viewedAt()).isNotNull();
                            assertThat(event.toString())
                                    .doesNotContain("private-password-hash", "@example.com");
                        });
    }

    @Test
    @DisplayName("B: Self view returns profile without publishing an other-user audit event")
    void selfView() {
        authorize();
        assertThat(useCase.execute("1", 1L).accountId()).isEqualTo(1L);
        verifyNoInteractions(events);
        verify(accounts, never()).findById(42L);
    }

    @Test
    @DisplayName(
            "A: Invalid identity, inactive/customer callers, missing profiles and stale roles"
                    + " cannot access target details")
    void forbiddenCallers() {
        for (String subject : new String[] {null, "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, 42L))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(accounts, employees);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(InvalidCredentialsException.class);
        when(accounts.findById(1L)).thenReturn(Optional.of(viewer.getAccount()));
        for (AccountStatus status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) continue;
            viewer.getAccount().setStatus(status);
            assertThatThrownBy(() -> useCase.execute("1", 42L))
                    .isInstanceOf(AccessDeniedException.class);
        }
        viewer.getAccount().setStatus(AccountStatus.ACTIVE);
        viewer.getAccount().setAccountType(AccountType.CUSTOMER);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(AccessDeniedException.class);
        viewer.getAccount().setAccountType(AccountType.EMPLOYEE);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(AccessDeniedException.class);
        when(employees.findByAccount_AccountId(1L)).thenReturn(Optional.of(viewer));
        for (String role : new String[] {"STAFF", "CUSTOMER", "TECHNICIAN"}) {
            viewer.getRole().setRoleName(role);
            assertThatThrownBy(() -> useCase.execute("1", 42L))
                    .isInstanceOf(AccessDeniedException.class);
        }
        viewer.setRole(null);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(AccessDeniedException.class);
        verify(accounts, never()).findById(42L);
        verifyNoInteractions(mapper, events);
    }

    @Test
    @DisplayName(
            "A: Missing targets and customer account/role targets never expose details or emit"
                    + " successful-view events")
    void forbiddenTargets() {
        authorize();
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(UserNotFoundException.class);
        when(accounts.findById(42L)).thenReturn(Optional.of(target.getAccount()));
        target.getAccount().setAccountType(AccountType.CUSTOMER);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(AccessDeniedException.class);
        verify(employees, never()).findByAccount_AccountId(42L);
        target.getAccount().setAccountType(AccountType.EMPLOYEE);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(UserNotFoundException.class);
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(target));
        target.getRole().setRoleName("customer");
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(AccessDeniedException.class);
        target.setRole(null);
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mapper, events);
    }

    @Test
    @DisplayName(
            "A: Storage and mapping failures propagate without publishing a successful-view audit")
    void failures() {
        authorize();
        var failure = new DataAccessResourceFailureException("offline");
        when(accounts.findById(42L)).thenThrow(failure);
        assertThatThrownBy(() -> useCase.execute("1", 42L)).isSameAs(failure);
        doReturn(Optional.of(target.getAccount())).when(accounts).findById(42L);
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(target));
        doThrow(new IllegalStateException("mapping unavailable"))
                .when(mapper)
                .toResponse(eq(target), any());
        assertThatThrownBy(() -> useCase.execute("1", 42L))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
    }
}
