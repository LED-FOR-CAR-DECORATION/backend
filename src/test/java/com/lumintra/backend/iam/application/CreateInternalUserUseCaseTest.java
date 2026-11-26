package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.api.request.CreateInternalUserRequest;
import com.lumintra.backend.iam.domain.event.InternalUserCreatedEvent;
import com.lumintra.backend.iam.domain.model.*;
import com.lumintra.backend.iam.domain.service.InternalPasswordGenerator;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.InternalUserMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

class CreateInternalUserUseCaseTest {
    UserAccountRepository accounts;
    EmployeeRepository employees;
    RoleRepository roles;
    InternalPasswordGenerator passwords;
    InternalUserMapper mapper;
    ApplicationEventPublisher events;
    BCryptPasswordEncoder encoder;
    CreateInternalUserUseCase useCase;
    static final String SECRET = "Generated!Password123";
    CreateInternalUserRequest request =
            new CreateInternalUserRequest(
                    " Staff@Example.com ", InternalUserRole.STAFF, " New Staff ");

    RoleEntity role(String name) {
        var r = new RoleEntity();
        r.setRoleName(name);
        return r;
    }

    UserAccountEntity account() {
        var a = UserAccountEntity.createInternalAccount("admin@example.com", "hash");
        a.setAccountId(1L);
        return a;
    }

    void setup() {
        accounts = mock(UserAccountRepository.class);
        employees = mock(EmployeeRepository.class);
        roles = mock(RoleRepository.class);
        passwords = mock(InternalPasswordGenerator.class);
        mapper = spy(new InternalUserMapper());
        events = mock(ApplicationEventPublisher.class);
        encoder = spy(new BCryptPasswordEncoder(4));
        useCase =
                new CreateInternalUserUseCase(
                        accounts, employees, roles, encoder, passwords, mapper, events);
    }

    @BeforeEach
    void init() {
        setup();
    }

    void authorize() {
        var a = account();
        when(accounts.findById(1L)).thenReturn(Optional.of(a));
        when(employees.findByAccount_AccountId(1L))
                .thenReturn(Optional.of(EmployeeEntity.createInternal(a, role("ADMIN"), "Admin")));
    }

    void ready() {
        authorize();
        when(roles.findByRoleName(anyString()))
                .thenAnswer(i -> Optional.of(role(i.getArgument(0))));
        when(passwords.generate()).thenReturn(SECRET);
        when(accounts.saveAndFlush(any()))
                .thenAnswer(
                        i -> {
                            UserAccountEntity a = i.getArgument(0);
                            a.setAccountId(42L);
                            return a;
                        });
    }

    @Test
    @DisplayName(
            "N: Create each allowed role with normalized email, BCrypt hash, safe response and one"
                    + " credentials event")
    void createsAllowedRoles() {
        for (var role : InternalUserRole.values()) {
            setup();
            ready();
            var result =
                    useCase.execute(
                            "1",
                            new CreateInternalUserRequest(request.email(), role, request.name()));
            assertThat(result.accountId()).isEqualTo(42L);
            assertThat(result.email()).isEqualTo("staff@example.com");
            assertThat(result.name()).isEqualTo("New Staff");
            assertThat(result.role()).isEqualTo(role.name());
            assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
            var capture = org.mockito.ArgumentCaptor.forClass(UserAccountEntity.class);
            verify(accounts).saveAndFlush(capture.capture());
            var saved = capture.getValue();
            assertThat(saved.getAccountType()).isEqualTo(AccountType.EMPLOYEE);
            assertThat(saved.getFailedAttempts()).isZero();
            assertThat(saved.getLockoutUntil()).isNull();
            assertThat(encoder.matches(SECRET, saved.getPasswordHash())).isTrue();
            assertThat(saved.getPasswordHash()).isNotEqualTo(SECRET);
            var event = org.mockito.ArgumentCaptor.forClass(InternalUserCreatedEvent.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().password()).isEqualTo(SECRET);
            assertThat(event.getValue().role()).isEqualTo(role.name());
            assertThat(event.getValue().createdBy()).isEqualTo(1L);
            assertThat(event.getValue().deliveryId()).isNotBlank();
            assertThat(event.getValue().toString()).doesNotContain(SECRET, "staff@example.com");
        }
    }

    @Test
    @DisplayName(
            "A: Reject invalid identity, missing or inactive accounts, customer types and stale"
                    + " non-admin roles before writes")
    void rejectsCallers() {
        for (String s : Arrays.asList(null, "bad", "0", "-1", "9223372036854775808", "2")) {
            setup();
            assertThatThrownBy(() -> useCase.execute(s, request))
                    .isInstanceOf(InvalidCredentialsException.class);
            verifyNoInteractions(passwords, events);
            verify(accounts, never()).saveAndFlush(any());
        }
        for (var status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) continue;
            setup();
            var a = account();
            a.setStatus(status);
            when(accounts.findById(1L)).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> useCase.execute("1", request))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(passwords, roles, events);
        }
        for (String role : Arrays.asList("MANAGER", "STAFF", "DESIGNER", "CUSTOMER", null)) {
            setup();
            authorize();
            var a = account();
            when(employees.findByAccount_AccountId(1L))
                    .thenReturn(
                            Optional.of(
                                    EmployeeEntity.createInternal(
                                            a, role == null ? null : role(role), null)));
            assertThatThrownBy(() -> useCase.execute("1", request))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(passwords, roles, events);
        }
        setup();
        var customer = account();
        customer.setAccountType(AccountType.CUSTOMER);
        when(accounts.findById(1L)).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(employees, passwords, roles, events);
        setup();
        when(accounts.findById(1L)).thenReturn(Optional.of(account()));
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName(
            "A: Duplicate email precheck and unique constraint race return conflict; missing"
                    + " configured role prevents account creation")
    void duplicatesAndRole() throws Exception {
        authorize();
        when(accounts.existsByEmailIgnoreCase(request.email())).thenReturn(true);
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verifyNoInteractions(passwords, roles, events);
        setup();
        ready();
        doThrow(
                        new DataIntegrityViolationException(
                                "duplicate",
                                new ConstraintViolationException(
                                        "unique",
                                        new java.sql.SQLException("duplicate", "23505"),
                                        "user_account_email_key")))
                .when(accounts)
                .saveAndFlush(any());
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(events, never()).publishEvent(any());
        verify(employees, never()).saveAndFlush(any());
        setup();
        authorize();
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(passwords, events);
        verify(accounts, never()).saveAndFlush(any());
        failuresAndTransaction();
    }

    void failuresAndTransaction() throws Exception {
        ready();
        var failure = new DataIntegrityViolationException("other constraint");
        doThrow(failure).when(accounts).saveAndFlush(any());
        assertThatThrownBy(() -> useCase.execute("1", request)).isSameAs(failure);
        verifyNoInteractions(events);
        setup();
        ready();
        doThrow(new IllegalStateException("storage")).when(employees).saveAndFlush(any());
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
        setup();
        ready();
        doThrow(new IllegalStateException("mapper")).when(mapper).toResponse(any());
        assertThatThrownBy(() -> useCase.execute("1", request))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
        assertThat(
                        CreateInternalUserUseCase.class
                                .getMethod("execute", String.class, CreateInternalUserRequest.class)
                                .getAnnotation(Transactional.class)
                                .readOnly())
                .isFalse();
    }
}
