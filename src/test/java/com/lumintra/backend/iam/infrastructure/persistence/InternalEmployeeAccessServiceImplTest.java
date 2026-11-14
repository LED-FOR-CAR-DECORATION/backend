package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEmployeeAccessServiceImplTest {

    private static final Set<String> STAFF_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private InternalEmployeeAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternalEmployeeAccessServiceImpl(userAccountRepository, employeeRepository);
    }

    @Test
    @DisplayName("COM-10 [N]: Allows active EMPLOYEE with STAFF, MANAGER, or ADMIN role (case-insensitive)")
    void requireActiveEmployeeAccountId_validInternalEmployee_returnsAccountId() {
        UserAccountEntity account = UserAccountEntity.createInternalAccount("staff@lumintra.com", "hash");
        account.setAccountId(7L);
        RoleEntity role = new RoleEntity();
        role.setRoleName("staff");
        EmployeeEntity employee = EmployeeEntity.createInternal(account, role, "Staff One");

        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(employeeRepository.findByAccount_AccountId(7L)).thenReturn(Optional.of(employee));

        Long result = service.requireActiveEmployeeAccountId("7", STAFF_ROLES);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("COM-10 [A]: Rejects invalid, non-positive, or missing account subjects with InvalidCredentialsException")
    void requireActiveEmployeeAccountId_invalidOrMissingSubject_throwsInvalidCredentials() {
        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("abc", STAFF_ROLES))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("0", STAFF_ROLES))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(userAccountRepository, employeeRepository);

        when(userAccountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("99", STAFF_ROLES))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("COM-10 [A]: Rejects suspended account, CUSTOMER accountType, missing profile, or DESIGNER role with AccessDeniedException")
    void requireActiveEmployeeAccountId_staleOrForbiddenEmployee_throwsAccessDenied() {
        UserAccountEntity suspended = UserAccountEntity.createInternalAccount("suspended@lumintra.com", "hash");
        suspended.setAccountId(10L);
        suspended.setStatus(AccountStatus.SUSPENDED);
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("10", STAFF_ROLES))
                .isInstanceOf(AccessDeniedException.class);

        UserAccountEntity customerType = UserAccountEntity.createOAuthCustomerAccount("cust@lumintra.com", null);
        customerType.setAccountId(11L);
        customerType.setAccountType(AccountType.CUSTOMER);
        when(userAccountRepository.findById(11L)).thenReturn(Optional.of(customerType));

        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("11", STAFF_ROLES))
                .isInstanceOf(AccessDeniedException.class);

        UserAccountEntity missingProfile = UserAccountEntity.createInternalAccount("noprofile@lumintra.com", "hash");
        missingProfile.setAccountId(12L);
        when(userAccountRepository.findById(12L)).thenReturn(Optional.of(missingProfile));
        when(employeeRepository.findByAccount_AccountId(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("12", STAFF_ROLES))
                .isInstanceOf(AccessDeniedException.class);

        UserAccountEntity designerAccount = UserAccountEntity.createInternalAccount("designer@lumintra.com", "hash");
        designerAccount.setAccountId(13L);
        RoleEntity designerRole = new RoleEntity();
        designerRole.setRoleName("DESIGNER");
        EmployeeEntity designerEmployee = EmployeeEntity.createInternal(designerAccount, designerRole, "Designer");
        when(userAccountRepository.findById(13L)).thenReturn(Optional.of(designerAccount));
        when(employeeRepository.findByAccount_AccountId(13L)).thenReturn(Optional.of(designerEmployee));

        assertThatThrownBy(() -> service.requireActiveEmployeeAccountId("13", STAFF_ROLES))
                .isInstanceOf(AccessDeniedException.class);
    }
}
