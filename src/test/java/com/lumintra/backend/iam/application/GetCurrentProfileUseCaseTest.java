package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.domain.model.*;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class GetCurrentProfileUseCaseTest {
    @Mock UserAccountRepository accounts;
    @Mock CustomerRepository customers;
    @Mock EmployeeRepository employees;
    @InjectMocks GetCurrentProfileUseCase useCase;
    UserAccountEntity account;

    @BeforeEach
    void setup() {
        account = UserAccountEntity.createCustomerAccount("a@b.co", "secret-bcrypt-hash");
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvatarUrl("account.png");
    }

    void account() {
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
    }

    @Test
    @DisplayName("N: Return customer profile and default role")
    void customer() {
        account();
        var profile = CustomerEntity.createCustomerFor(account);
        profile.setFullName("An");
        profile.setPhone("0901234567");
        profile.setAvatarUrl("profile.png");
        when(customers.findById(42L)).thenReturn(Optional.of(profile));
        var result = useCase.execute("42");
        assertThat(result.name()).isEqualTo("An");
        assertThat(result.email()).isEqualTo("a@b.co");
        assertThat(result.phone()).isEqualTo("0901234567");
        assertThat(result.avatar()).isEqualTo("profile.png");
        assertThat(result.roles()).containsExactly("CUSTOMER");
        verifyNoInteractions(employees);
    }

    @Test
    @DisplayName("B: Incomplete profile preserves null fields and uses account avatar")
    void incomplete() {
        account();
        when(customers.findById(42L))
                .thenReturn(Optional.of(CustomerEntity.createCustomerFor(account)));
        var result = useCase.execute("42");
        assertThat(result.name()).isNull();
        assertThat(result.phone()).isNull();
        assertThat(result.avatar()).isEqualTo("account.png");
    }

    @Test
    @DisplayName("N: Employee role comes from current database profile")
    void employee() {
        account();
        account.setAccountType(AccountType.EMPLOYEE);
        var employee = new EmployeeEntity();
        employee.setFullName("Staff");
        employee.setPhone("123");
        var role = new RoleEntity();
        role.setRoleName("manager");
        employee.setRole(role);
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(employee));
        var result = useCase.execute("42");
        assertThat(result.roles()).containsExactly("MANAGER");
        assertThat(result.name()).isEqualTo("Staff");
        verifyNoInteractions(customers);
    }

    @Test
    @DisplayName("A: Invalid subjects never query repositories")
    void invalidSubject() {
        for (String subject : new String[] {null, "abc", "0", "-1", "9223372036854775808"})
            assertThatThrownBy(() -> useCase.execute(subject))
                    .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(accounts, customers, employees);
    }

    @Test
    @DisplayName("A: Missing account returns not found")
    void missingAccount() {
        assertThatThrownBy(() -> useCase.execute("42")).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(customers, employees);
    }

    @Test
    @DisplayName("A: All inactive statuses are denied")
    void inactive() {
        account();
        for (var status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) continue;
            account.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42"))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        verifyNoInteractions(customers, employees);
    }

    @Test
    @DisplayName("A: Missing customer profile is not silently fabricated")
    void missingProfile() {
        account();
        assertThatThrownBy(() -> useCase.execute("42")).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("A: Missing employee role fails safely")
    void missingRole() {
        account();
        account.setAccountType(AccountType.EMPLOYEE);
        when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(new EmployeeEntity()));
        assertThatThrownBy(() -> useCase.execute("42")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A: Database outage is propagated instead of empty success")
    void outage() {
        when(accounts.findById(42L))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> useCase.execute("42"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
