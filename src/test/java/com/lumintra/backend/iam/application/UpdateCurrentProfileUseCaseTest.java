package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.api.request.UpdateCurrentProfileRequest;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UpdateCurrentProfileUseCaseTest {
    @Mock UserAccountRepository accounts;
    @Mock CustomerRepository customers;
    @Mock EmployeeRepository employees;
    @InjectMocks UpdateCurrentProfileUseCase useCase;
    UserAccountEntity account;
    final UpdateCurrentProfileRequest request =
            new UpdateCurrentProfileRequest(
                    "New Name", "+84901234567", "https://example.com/new.png");

    @BeforeEach
    void setup() {
        account =
                UserAccountEntity.createCustomerAccount(
                        "user@example.com", "original-password-hash");
        account.setAccountId(42L);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvatarUrl("https://example.com/account.png");
    }

    void account() {
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
    }

    @Test
    @DisplayName("N: Customer update persists editable fields and preserves account credentials")
    void customerUpdate() {
        account();
        var profile = CustomerEntity.createCustomerFor(account);
        when(customers.findById(42L)).thenReturn(Optional.of(profile));
        var result = useCase.execute("42", request);
        verify(customers).saveAndFlush(profile);
        assertThat(profile.getFullName()).isEqualTo("New Name");
        assertThat(profile.getPhone()).isEqualTo("+84901234567");
        assertThat(profile.getAvatarUrl()).isEqualTo(request.avatar());
        assertThat(profile.getUpdatedAt()).isNotNull();
        assertThat(account.getPasswordHash()).isEqualTo("original-password-hash");
        assertThat(account.getEmail()).isEqualTo("user@example.com");
        assertThat(account.getAvatarUrl()).isEqualTo("https://example.com/account.png");
        assertThat(result.roles()).containsExactly("CUSTOMER");
        assertThat(result.name()).isEqualTo(request.name());
        verify(accounts, never()).save(any());
        verifyNoInteractions(employees);
    }

    @Test
    @DisplayName("N: Employee update preserves role and returns current database role")
    void employeeUpdate() {
        account();
        account.setAccountType(AccountType.EMPLOYEE);
        var profile = new EmployeeEntity();
        var role = new RoleEntity();
        role.setRoleName("manager");
        profile.setRole(role);
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(profile));
        var result = useCase.execute("42", request);
        verify(employees).saveAndFlush(profile);
        assertThat(profile.getRole()).isSameAs(role);
        assertThat(profile.getFullName()).isEqualTo(request.name());
        assertThat(profile.getPhone()).isEqualTo(request.phone());
        assertThat(profile.getAvatarUrl()).isEqualTo(request.avatar());
        assertThat(result.roles()).containsExactly("MANAGER");
        verifyNoInteractions(customers);
    }

    @Test
    @DisplayName("B: Null optional fields clear profile values and restore account avatar fallback")
    void clearOptionalFields() {
        account();
        var profile = CustomerEntity.createCustomerFor(account);
        profile.setPhone("0901234567");
        profile.setAvatarUrl("https://example.com/old.png");
        when(customers.findById(42L)).thenReturn(Optional.of(profile));
        var result = useCase.execute("42", new UpdateCurrentProfileRequest("Name", null, null));
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getAvatarUrl()).isNull();
        assertThat(result.phone()).isNull();
        assertThat(result.avatar()).isEqualTo(account.getAvatarUrl());
    }

    @Test
    @DisplayName("N: GET observes the same profile after PUT")
    void roundTrip() {
        account();
        var profile = CustomerEntity.createCustomerFor(account);
        when(customers.findById(42L)).thenReturn(Optional.of(profile));
        var updated = useCase.execute("42", request);
        var loaded = new GetCurrentProfileUseCase(accounts, customers, employees).execute("42");
        assertThat(loaded).isEqualTo(updated);
    }

    @Test
    @DisplayName("A: Invalid authenticated subjects cannot touch storage")
    void invalidSubjects() {
        for (String subject : new String[] {null, "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(accounts, customers, employees);
    }

    @Test
    @DisplayName("A: Missing account cannot update a profile")
    void missingAccount() {
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(customers, employees);
    }

    @Test
    @DisplayName("A: Every inactive status is denied before profile writes")
    void inactiveAccount() {
        account();
        for (var status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) continue;
            account.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42", request))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(customers, employees);
    }

    @Test
    @DisplayName("A: Missing customer profile is not created implicitly")
    void missingCustomer() {
        account();
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(UserNotFoundException.class);
        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A: Missing employee profile is not created implicitly")
    void missingEmployee() {
        account();
        account.setAccountType(AccountType.EMPLOYEE);
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(UserNotFoundException.class);
        verify(employees, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A: Missing employee role prevents profile mutation")
    void missingRole() {
        account();
        account.setAccountType(AccountType.EMPLOYEE);
        var profile = new EmployeeEntity();
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(profile.getFullName()).isNull();
        verify(employees, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A: Database read outage does not become not found")
    void readOutage() {
        when(accounts.findById(42L)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(customers, employees);
    }

    @Test
    @DisplayName("A: Flush failure propagates instead of returning a successful update")
    void writeFailure() {
        account();
        var profile = CustomerEntity.createCustomerFor(account);
        when(customers.findById(42L)).thenReturn(Optional.of(profile));
        when(customers.saveAndFlush(profile))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> useCase.execute("42", request))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
