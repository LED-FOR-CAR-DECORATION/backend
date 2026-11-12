package com.lumintra.backend.iam.infrastructure.bootstrap;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.RoleRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Order(1)
@RequiredArgsConstructor
public class IamDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IamDataSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email}") private String adminEmail;
    @Value("${app.seed.admin-password}") private String adminPassword;

    @Value("${app.seed.test-manager-email}") private String managerEmail;
    @Value("${app.seed.test-manager-password}") private String managerPassword;

    @Value("${app.seed.test-customer-email}") private String customerEmail;
    @Value("${app.seed.test-customer-password}") private String customerPassword;

    @Value("${app.seed.test-staff-email}") private String staffEmail;
    @Value("${app.seed.test-staff-password}") private String staffPassword;

    @Value("${app.seed.test-designer-email}") private String designerEmail;
    @Value("${app.seed.test-designer-password}") private String designerPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base IAM seed data...");

        RoleEntity adminRole = seedSystemRoles();
        seedSuperAdmin(adminRole);
        seedTestAccounts();

        log.info("IAM seed data verification complete.");
    }

    private RoleEntity seedSystemRoles() {
        RoleEntity adminRole = seedRole("ADMIN", "Super Administrator", true);
        seedRole("MANAGER", "System Manager", true);
        seedRole("STAFF", "Standard Staff", true);
        seedRole("DESIGNER", "Art Designer", true);
        seedRole("CUSTOMER", "End User", true);

        return adminRole;
    }

    private RoleEntity seedRole(String roleName, String description, boolean isSystem) {
        RoleEntity role = roleRepository.findByRoleName(roleName).orElseGet(() -> {
            RoleEntity newRole = new RoleEntity();
            newRole.setRoleName(roleName);
            newRole.setCreatedAt(Instant.now());
            log.info("Seeding default system role [roleName={}]", roleName);
            return newRole;
        });

        role.setDescription(description);
        role.setSystem(isSystem);

        return roleRepository.save(role);
    }

    private void seedSuperAdmin(RoleEntity adminRole) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        userAccountRepository.findByEmail(adminEmail).orElseGet(() -> {
            // Create Base Account
            UserAccountEntity account = new UserAccountEntity();
            account.setEmail(adminEmail);
            account.setPasswordHash(passwordEncoder.encode(adminPassword));
            account.setAccountType(AccountType.EMPLOYEE);
            account.setStatus(AccountStatus.ACTIVE);
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            // Create Employee linked to the Admin Role
            EmployeeEntity employee = new EmployeeEntity();
            employee.setAccount(savedAccount);
            employee.setRole(adminRole);
            employee.setFullName("System Administrator");
            employee.setCreatedAt(Instant.now());
            employeeRepository.save(employee);

            log.info("Seeding default Super Admin account [email={}]", adminEmail);
            return savedAccount;
        });
    }

    private void seedTestAccounts() {
        seedCustomerAccount(customerEmail, customerPassword, "Test Customer");
        seedEmployeeAccount(managerEmail, managerPassword, "MANAGER", "Test Manager");
        seedEmployeeAccount(staffEmail, staffPassword, "STAFF", "Test Staff");
        seedEmployeeAccount(designerEmail, designerPassword, "DESIGNER", "Test Designer");
    }

    private void seedEmployeeAccount(String email, String password, String roleName, String fullName) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;

        userAccountRepository.findByEmail(email).orElseGet(() -> {
            RoleEntity role = roleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

            UserAccountEntity account = new UserAccountEntity();
            account.setEmail(email);
            account.setPasswordHash(passwordEncoder.encode(password));
            account.setAccountType(AccountType.EMPLOYEE);
            account.setStatus(AccountStatus.ACTIVE);
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            EmployeeEntity employee = new EmployeeEntity();
            employee.setAccount(savedAccount);
            employee.setRole(role);
            employee.setFullName(fullName);
            employee.setCreatedAt(Instant.now());
            employeeRepository.save(employee);

            log.info("Seeding test employee account [email={}] [role={}]", email, roleName);
            return savedAccount;
        });
    }

    private void seedCustomerAccount(String email, String password, String fullName) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;

        userAccountRepository.findByEmail(email).orElseGet(() -> {
            UserAccountEntity account = new UserAccountEntity();
            account.setEmail(email);
            account.setPasswordHash(passwordEncoder.encode(password));
            account.setAccountType(AccountType.CUSTOMER);
            account.setStatus(AccountStatus.ACTIVE);
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            CustomerEntity customer = new CustomerEntity();
            customer.setAccount(savedAccount);
            customer.setFullName(fullName);
            customer.setCreatedAt(Instant.now());
            customerRepository.save(customer);

            log.info("Seeding test customer account [email={}]", email);
            return savedAccount;
        });
    }
}
