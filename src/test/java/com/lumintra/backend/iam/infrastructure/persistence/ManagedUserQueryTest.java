package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.ManagedUserSpecifications;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = ManagedUserQueryTest.Config.class)
class ManagedUserQueryTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = EmployeeEntity.class)
    @EnableJpaRepositories(basePackageClasses = EmployeeRepository.class)
    static class Config {}

    @Autowired EntityManager em;
    @Autowired EmployeeRepository employees;

    RoleEntity role(String name) {
        var role = new RoleEntity();
        role.setRoleName(name);
        em.persist(role);
        return role;
    }

    EmployeeEntity employee(
            RoleEntity role, String email, String name, AccountStatus status, AccountType type) {
        var account = UserAccountEntity.createCustomerAccount(email, "not-a-real-password");
        account.setAccountType(type);
        account.setStatus(status);
        em.persist(account);
        var employee = new EmployeeEntity();
        employee.setAccount(account);
        employee.setRole(role);
        employee.setFullName(name);
        em.persist(employee);
        em.flush();
        return employee;
    }

    @Test
    @DisplayName(
            "A: Database query excludes both customer account types and CUSTOMER roles before"
                    + " counting")
    void excludesCustomers() {
        var manager = role("MANAGER");
        var customer = role("customer");
        var allowed =
                employee(
                        manager,
                        "manager@example.com",
                        "Manager",
                        AccountStatus.ACTIVE,
                        AccountType.EMPLOYEE);
        employee(
                manager,
                "customer-account@example.com",
                "Not internal",
                AccountStatus.ACTIVE,
                AccountType.CUSTOMER);
        employee(
                customer,
                "customer-role@example.com",
                "Not internal",
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        var page =
                employees.findAll(
                        ManagedUserSpecifications.matching(null, null, null), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(e -> e.getAccount().getAccountId())
                .containsExactly(allowed.getAccount().getAccountId());
        assertThat(employees.count(ManagedUserSpecifications.matching("CUSTOMER", null, null)))
                .isZero();
    }

    @Test
    @DisplayName("N: Role, active status and case-insensitive name search are combined with AND")
    void combinedFilters() {
        var manager = role("MANAGER");
        var admin = role("ADMIN");
        employee(
                manager,
                "one@example.com",
                "Nguyen One",
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        employee(
                manager,
                "two@example.com",
                "Nguyen Two",
                AccountStatus.SUSPENDED,
                AccountType.EMPLOYEE);
        employee(
                admin,
                "three@example.com",
                "Nguyen Three",
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        assertThat(employees.count(ManagedUserSpecifications.matching("manager", true, "NGUYEN")))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("N: Search matches email as well as name")
    void emailSearch() {
        employee(
                role("STAFF"),
                "Unique.Name@example.com",
                null,
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        assertThat(employees.count(ManagedUserSpecifications.matching(null, null, "unique.name")))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("B: Percent, underscore and backslash are searched literally")
    void literalWildcards() {
        var staff = role("STAFF");
        employee(
                staff,
                "one@example.com",
                "50%_\\ literal",
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        employee(staff, "two@example.com", "ordinary", AccountStatus.ACTIVE, AccountType.EMPLOYEE);
        for (String search : new String[] {"%", "_", "\\"}) {
            assertThat(employees.count(ManagedUserSpecifications.matching(null, null, search)))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("N: False active filter includes every non-ACTIVE lifecycle status")
    void inactiveStatuses() {
        var staff = role("STAFF");
        for (var status : AccountStatus.values())
            employee(
                    staff,
                    status.name() + "@example.com",
                    status.name(),
                    status,
                    AccountType.EMPLOYEE);
        assertThat(employees.count(ManagedUserSpecifications.matching(null, false, null)))
                .isEqualTo(AccountStatus.values().length - 1);
        assertThat(employees.count(ManagedUserSpecifications.matching(null, true, null)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("B: Pagination has stable account ID order and no duplicate results")
    void stablePages() {
        var staff = role("STAFF");
        var first =
                employee(
                        staff,
                        "one@example.com",
                        "One",
                        AccountStatus.ACTIVE,
                        AccountType.EMPLOYEE);
        var second =
                employee(
                        staff,
                        "two@example.com",
                        "Two",
                        AccountStatus.ACTIVE,
                        AccountType.EMPLOYEE);
        var sort = Sort.by(Sort.Direction.DESC, "account.accountId");
        var page0 =
                employees.findAll(
                        ManagedUserSpecifications.matching(null, null, null),
                        PageRequest.of(0, 1, sort));
        var page1 =
                employees.findAll(
                        ManagedUserSpecifications.matching(null, null, null),
                        PageRequest.of(1, 1, sort));
        assertThat(page0.getTotalElements()).isEqualTo(2);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getContent().getFirst().getAccount().getAccountId())
                .isEqualTo(second.getAccount().getAccountId());
        assertThat(page1.getContent().getFirst().getAccount().getAccountId())
                .isEqualTo(first.getAccount().getAccountId());
    }

    @Test
    @DisplayName("B: Out-of-range page is empty but preserves matching total")
    void emptyPage() {
        employee(
                role("STAFF"),
                "one@example.com",
                "One",
                AccountStatus.ACTIVE,
                AccountType.EMPLOYEE);
        var page =
                employees.findAll(
                        ManagedUserSpecifications.matching(null, null, null),
                        PageRequest.of(5, 10));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
