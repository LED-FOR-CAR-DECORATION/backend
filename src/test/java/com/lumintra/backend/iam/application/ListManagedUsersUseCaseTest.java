package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ManagedUserFilterRequest;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListManagedUsersUseCaseTest {
    @Mock UserAccountRepository accounts;
    @Mock EmployeeRepository employees;
    ListManagedUsersUseCase useCase;
    UserAccountEntity account;
    EmployeeEntity requester;
    final ManagedUserFilterRequest filter = new ManagedUserFilterRequest(1,10,"STAFF",true,"name");

    @BeforeEach void setup() {
        useCase=new ListManagedUsersUseCase(accounts,employees,new ManagedUserMapper());
        account=UserAccountEntity.createCustomerAccount("admin@example.com","private-hash");
        account.setAccountId(42L); account.setAccountType(AccountType.EMPLOYEE); account.setStatus(AccountStatus.ACTIVE);
        requester=new EmployeeEntity(); requester.setAccount(account);
        var role=new RoleEntity();role.setRoleName("ADMIN");requester.setRole(role);
    }

    void account() { when(accounts.findById(42L)).thenReturn(Optional.of(account)); }
    void authorize() { account();when(employees.findByAccount_AccountId(42L)).thenReturn(Optional.of(requester)); }

    @Test @DisplayName("N: Active admin receives safe items and accurate pagination metadata")
    void adminList() {
        authorize();
        when(employees.findAll(any(Specification.class),any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(requester),PageRequest.of(1,10),11));
        var result=useCase.execute("42",filter);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().email()).isEqualTo("admin@example.com");
        assertThat(result.meta().getTotalElements()).isEqualTo(11);
        assertThat(result.meta().getPage()).isEqualTo(1);
        var pageable=ArgumentCaptor.forClass(Pageable.class);
        verify(employees).findAll(any(Specification.class),pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("account.accountId").isDescending()).isTrue();
    }

    @Test @DisplayName("N: Current database manager role is permitted")
    void managerList() {
        authorize();requester.getRole().setRoleName("manager");
        when(employees.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<EmployeeEntity>(List.of()));
        assertThat(useCase.execute("42",filter).items()).isEmpty();
    }

    @Test @DisplayName("A: Stale JWT admin privilege cannot override a demoted database role")
    void demotedRole() {
        authorize(); requester.getRole().setRoleName("STAFF");
        assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(AccessDeniedException.class);
        verify(employees,never()).findAll(any(Specification.class),any(Pageable.class));
    }

    @Test @DisplayName("A: Inactive caller is denied before querying users")
    void inactive() {
        account(); account.setStatus(AccountStatus.SUSPENDED);
        assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(employees);
    }

    @Test @DisplayName("A: Customer caller cannot list internal users")
    void customer() {
        account(); account.setAccountType(AccountType.CUSTOMER);
        assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(employees);
    }

    @Test @DisplayName("A: Missing internal profile is denied")
    void missingProfile() {
        account(); assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(AccessDeniedException.class);
        verify(employees,never()).findAll(any(Specification.class),any(Pageable.class));
    }

    @Test @DisplayName("A: Missing caller account returns unauthorized")
    void missingAccount() {
        assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(employees);
    }

    @Test @DisplayName("A: Invalid subjects cannot access the database")
    void invalidSubjects() {
        for (String subject:new String[]{null,"abc","0","-1","9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject,filter)).isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(accounts,employees);
    }

    @Test @DisplayName("A: Storage outage propagates instead of returning an empty list")
    void outage() {
        authorize();when(employees.findAll(any(Specification.class),any(Pageable.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> useCase.execute("42",filter)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
}
