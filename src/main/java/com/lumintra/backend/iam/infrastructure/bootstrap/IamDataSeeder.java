package com.lumintra.backend.iam.infrastructure.bootstrap;

import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IamDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IamDataSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
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

    // Helper record to bind permissions to their descriptions and default roles
    private record PermissionDef(String name, String description, Set<String> roles) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base IAM seed data...");

        List<PermissionDef> definitions = getPermissionDefinitions();
        Map<String, PermissionEntity> savedPermissions = seedPermissions(definitions);

        RoleEntity adminRole = seedSystemRoles(definitions, savedPermissions);
        seedSuperAdmin(adminRole);
        seedTestAccounts();

        log.info("IAM seed data verification complete.");
    }

    private Map<String, PermissionEntity> seedPermissions(List<PermissionDef> definitions) {
        return definitions.stream().map(def -> {
            PermissionEntity entity = permissionRepository.findByPermissionName(def.name())
                    .orElseGet(() -> {
                        PermissionEntity newPerm = new PermissionEntity();
                        newPerm.setPermissionName(def.name());
                        newPerm.setCreatedAt(Instant.now());
                        return newPerm;
                    });

            // Always update description in case it changed in the code
            entity.setDescription(def.description());
            return permissionRepository.save(entity);
        }).collect(Collectors.toMap(PermissionEntity::getPermissionName, p -> p));
    }

    private RoleEntity seedSystemRoles(List<PermissionDef> definitions, Map<String, PermissionEntity> savedPermissions) {
        // Resolve role permission sets based on the definitions map
        Set<PermissionEntity> adminPerms = extractPermsForRole("ADMIN", definitions, savedPermissions);
        Set<PermissionEntity> managerPerms = extractPermsForRole("MANAGER", definitions, savedPermissions);
        Set<PermissionEntity> staffPerms = extractPermsForRole("STAFF", definitions, savedPermissions);
        Set<PermissionEntity> designerPerms = extractPermsForRole("DESIGNER", definitions, savedPermissions);
        Set<PermissionEntity> customerPerms = extractPermsForRole("CUSTOMER", definitions, savedPermissions); // Will be empty based on current spec

        // Create immutable default system roles
        RoleEntity adminRole = seedRole("ADMIN", "Super Administrator", true, adminPerms);
        seedRole("MANAGER", "System Manager", true, managerPerms);
        seedRole("STAFF", "Standard Staff", true, staffPerms);
        seedRole("DESIGNER", "Art Designer", true, designerPerms);
        seedRole("CUSTOMER", "End User", true, customerPerms);

        return adminRole;
    }

    private Set<PermissionEntity> extractPermsForRole(String roleName, List<PermissionDef> definitions, Map<String, PermissionEntity> savedPermissions) {
        return definitions.stream()
                .filter(def -> def.roles().contains(roleName))
                .map(def -> savedPermissions.get(def.name()))
                .collect(Collectors.toSet());
    }

    private RoleEntity seedRole(String roleName, String description, boolean isSystem, Set<PermissionEntity> permissions) {
        RoleEntity role = roleRepository.findByRoleName(roleName).orElseGet(() -> {
            RoleEntity newRole = new RoleEntity();
            newRole.setRoleName(roleName);
            newRole.setCreatedAt(Instant.now());
            log.info("Seeding default system role [roleName={}]", roleName);
            return newRole;
        });

        // Update changeable properties
        role.setDescription(description);
        role.setSystem(isSystem);
        role.setPermissions(permissions);

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
            account.setStatus("ACTIVE");
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            // Create Employee Profile linked to the Admin Role
            EmployeeProfileEntity profile = new EmployeeProfileEntity();
            profile.setAccount(savedAccount);
            profile.setRole(adminRole);
            profile.setFullName("System Administrator");
            profile.setCreatedAt(Instant.now());
            employeeProfileRepository.save(profile);

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
            account.setStatus("ACTIVE");
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            EmployeeProfileEntity profile = new EmployeeProfileEntity();
            profile.setAccount(savedAccount);
            profile.setRole(role);
            profile.setFullName(fullName);
            profile.setCreatedAt(Instant.now());
            employeeProfileRepository.save(profile);

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
            account.setStatus("ACTIVE");
            account.setCreatedAt(Instant.now());
            UserAccountEntity savedAccount = userAccountRepository.save(account);

            CustomerProfileEntity profile = new CustomerProfileEntity();
            profile.setAccount(savedAccount);
            profile.setFullName(fullName);
            profile.setCreatedAt(Instant.now());
            customerProfileRepository.save(profile);

            log.info("Seeding test customer account [email={}]", email);
            return savedAccount;
        });
    }

    private List<PermissionDef> getPermissionDefinitions() {
        return List.of(
                // 1. Identity & Access Management
                new PermissionDef("READ_USER", "View internal & customer user list, profiles, and account status.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("CREATE_USER", "Create internal accounts with randomized credentials.", Set.of("ADMIN")),
                new PermissionDef("UPDATE_USER", "Update user profile details, assign roles, or lock/unlock/suspend accounts.", Set.of("ADMIN")),
                new PermissionDef("DELETE_USER", "Deactivate or soft-delete user accounts.", Set.of("ADMIN")),
                new PermissionDef("READ_ROLE", "View system roles, custom roles, and their mapped permissions.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("CREATE_ROLE", "Create new custom roles and assign permission sets.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("UPDATE_ROLE", "Rename custom roles and modify their permission mappings.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("DELETE_ROLE", "Delete custom roles and reassign affected users to default Staff role.", Set.of("ADMIN", "MANAGER")),

                // 2. Store Catalog, Characters, Bundles & Tags
                new PermissionDef("READ_CHARACTER", "View internal character art repository and version history.", Set.of("ADMIN", "MANAGER", "DESIGNER", "STAFF")),
                new PermissionDef("CREATE_CHARACTER", "Create new official character entries for the platform catalog.", Set.of("MANAGER", "DESIGNER")),
                new PermissionDef("UPDATE_CHARACTER", "Upload new animation versions and update specs.", Set.of("MANAGER", "DESIGNER")),
                new PermissionDef("APPROVE_CHARACTER", "Approve or reject designer-submitted character templates.", Set.of("MANAGER")),
                new PermissionDef("CREATE_STORE_LISTING", "Create store listings from approved characters and set pricing.", Set.of("MANAGER")),
                new PermissionDef("UPDATE_STORE_LISTING", "Update store listing price, metadata, or status.", Set.of("MANAGER")),
                new PermissionDef("DELETE_STORE_LISTING", "Soft-delete store listings from the catalog.", Set.of("MANAGER")),
                new PermissionDef("MANAGE_TAG", "Create, update, delete category tags and map them to listings.", Set.of("MANAGER")),
                new PermissionDef("READ_BUNDLE", "View promotional hardware + character bundles.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("MANAGE_BUNDLE", "Create, update, activate, or archive promotional bundles.", Set.of("MANAGER")),
                new PermissionDef("MODERATE_REVIEW", "View and delete inappropriate customer star ratings and text reviews.", Set.of("MANAGER", "STAFF")),

                // 3. Hardware Catalog, Devices, Warranty, Returns & OTA
                new PermissionDef("READ_LED_MODEL", "View hardware LED models, technical specs, and stock levels.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("MANAGE_LED_MODEL", "Create, update specifications, adjust stock quantities.", Set.of("MANAGER")),
                new PermissionDef("READ_LED_INSTANCE", "View individual physical LED units, binding status, firmware.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("MANAGE_LED_INSTANCE", "Register factory serial/MAC batches or manually link/unlink.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("READ_DEVICE_TELEMETRY", "Inspect historical hardware sensor logs, brightness history.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("READ_WARRANTY", "View customer hardware warranty records and defect claim evidence.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("ADJUDICATE_WARRANTY", "Approve, reject, or resolve hardware warranty claims.", Set.of("MANAGER")),
                new PermissionDef("READ_DEVICE_RETURN", "View hardware return/replacement requests.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("MANAGE_DEVICE_RETURN", "Process and update status of hardware device returns.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("READ_FIRMWARE", "View firmware binary releases and device OTA update logs.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("MANAGE_FIRMWARE", "Upload new firmware versions, set active release, or rollback.", Set.of("ADMIN")),

                // 4. Commerce, Order Fulfillment, Delivery & Payments
                new PermissionDef("READ_ORDER", "View customer order queue, line items, and fulfillment status.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("CREATE_MANUAL_ORDER", "Manually create a customer purchase order.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("FULFILL_ORDER", "Process orders, scan/bind LED_INSTANCE serial numbers.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("CANCEL_ORDER", "Cancel orders prior to shipping dispatch and release inventory.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("READ_DELIVERY", "View shipping waybills, tracking milestones, and webhook logs.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("DISPATCH_DELIVERY", "Generate shipping waybills and trigger manual status sync.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("READ_PAYMENT", "View payment transaction ledger across orders and commissions.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("READ_ENTITLEMENT", "View customer digital ownership records and gift code status.", Set.of("ADMIN", "MANAGER", "STAFF")),

                // 5. Custom Art Commissions & Customer Chat
                new PermissionDef("READ_ALL_COMMISSION", "View all customer commission requests and status.", Set.of("ADMIN", "MANAGER", "STAFF")),
                new PermissionDef("READ_ASSIGNED_COMMISSION", "View only commission tickets assigned to the authenticated designer.", Set.of("DESIGNER")),
                new PermissionDef("QUOTE_COMMISSION", "Review customer requirements and issue price quotes.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("ASSIGN_COMMISSION", "Assign or reassign paid commission tickets to internal designers.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("SUBMIT_DRAFT_COMMISSION", "Upload artwork draft versions and attachments for assigned commissions.", Set.of("DESIGNER")),
                new PermissionDef("REVIEW_DRAFT_COMMISSION", "Inspect designer drafts, record decisions, post deliverables.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("MANAGE_COMMISSION_PRICING", "Configure base commission pricing rules and selectable options.", Set.of("MANAGER")),
                new PermissionDef("READ_CONVERSATION", "View customer support/commission chat rooms and message history.", Set.of("MANAGER", "STAFF")),
                new PermissionDef("MESSAGE_CONVERSATION", "Send, edit, or delete messages and attachments in chat rooms.", Set.of("MANAGER", "STAFF")),

                // 6. Dashboard, Analytics & System Monitoring
                new PermissionDef("READ_DASHBOARD", "View operational KPIs, revenue charts, and daily summaries.", Set.of("ADMIN", "MANAGER")),
                new PermissionDef("READ_SYSTEM_HEALTH", "View Spring Actuator health indicators and memory metrics.", Set.of("ADMIN")),
                new PermissionDef("READ_SYSTEM_AUDIT", "Search structured MDC audit logs and stream live application logs.", Set.of("ADMIN")),
                new PermissionDef("DOWNLOAD_SYSTEM_LOG", "Download encrypted archived log files.", Set.of("ADMIN"))
        );
    }
}