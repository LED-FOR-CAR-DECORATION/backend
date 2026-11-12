package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = CustomerLookupQueryTest.Config.class)
class CustomerLookupQueryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CustomerEntity.class)
    @EnableJpaRepositories(basePackageClasses = CustomerRepository.class)
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("COM-10 [N]: CustomerLookupService batch-fetches customer fullName, phone, and account email in one query")
    void findProfilesByCustomerIds_existingAndPartialProfiles_returnsBatchSnapshots() {
        UserAccountEntity fullAccount = UserAccountEntity.createOAuthCustomerAccount("khach1@example.com", null);
        fullAccount.setStatus(AccountStatus.ACTIVE);
        entityManager.persist(fullAccount);
        CustomerEntity fullCustomer = CustomerEntity.createCustomerFor(fullAccount);
        fullCustomer.setFullName("Nguyen Van A");
        fullCustomer.setPhone("0909111222");
        entityManager.persist(fullCustomer);

        UserAccountEntity emailOnlyAccount = UserAccountEntity.createCustomerAccount("khach2@example.com", "hashed");
        entityManager.persist(emailOnlyAccount);

        entityManager.flush();
        entityManager.clear();

        CustomerLookupServiceImpl service = new CustomerLookupServiceImpl(customerRepository);
        Map<Long, CustomerProfileSnapshot> result = service.findProfilesByCustomerIds(
                List.of(fullAccount.getAccountId(), emailOnlyAccount.getAccountId(), 99999L)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(fullAccount.getAccountId()))
                .isEqualTo(new CustomerProfileSnapshot(
                        fullAccount.getAccountId(),
                        "Nguyen Van A",
                        "0909111222",
                        "khach1@example.com"
                ));
        assertThat(result.get(emailOnlyAccount.getAccountId()))
                .isEqualTo(new CustomerProfileSnapshot(
                        emailOnlyAccount.getAccountId(),
                        null,
                        null,
                        "khach2@example.com"
                ));
    }

    @Test
    @DisplayName("COM-10 [B]: CustomerLookupService handles null/empty/non-positive IDs safely and resolves single lookup")
    void findProfileByCustomerId_edgeInputs_behaveSafely() {
        UserAccountEntity account = UserAccountEntity.createOAuthCustomerAccount("single@example.com", null);
        entityManager.persist(account);
        CustomerEntity customer = CustomerEntity.createOAuthCustomer(account, "Tran Thi B", null);
        entityManager.persist(customer);
        entityManager.flush();
        entityManager.clear();

        CustomerLookupServiceImpl service = new CustomerLookupServiceImpl(customerRepository);

        assertThat(service.findProfilesByCustomerIds(null)).isEmpty();
        assertThat(service.findProfilesByCustomerIds(List.of())).isEmpty();
        assertThat(service.findProfilesByCustomerIds(List.of(0L, -5L))).isEmpty();
        assertThat(service.findProfileByCustomerId(null)).isEmpty();
        assertThat(service.findProfileByCustomerId(0L)).isEmpty();
        assertThat(service.findProfileByCustomerId(account.getAccountId()))
                .contains(new CustomerProfileSnapshot(
                        account.getAccountId(),
                        "Tran Thi B",
                        null,
                        "single@example.com"
                ));
    }

    @Test
    @DisplayName("N: CustomerLookupService resolves customer profile by email and handles fallback to first customer profile")
    void findProfileByEmail_and_findFirstProfile_workCorrectly() {
        UserAccountEntity account1 = UserAccountEntity.createOAuthCustomerAccount("first@example.com", null);
        entityManager.persist(account1);
        CustomerEntity customer1 = CustomerEntity.createOAuthCustomer(account1, "First User", null);
        entityManager.persist(customer1);

        UserAccountEntity account2 = UserAccountEntity.createOAuthCustomerAccount("second@example.com", null);
        entityManager.persist(account2);
        CustomerEntity customer2 = CustomerEntity.createOAuthCustomer(account2, "Second User", null);
        customer2.setPhone("0988776655");
        entityManager.persist(customer2);

        UserAccountEntity employeeAccount = UserAccountEntity.createInternalAccount("employee@example.com", "hash");
        entityManager.persist(employeeAccount);

        entityManager.flush();
        entityManager.clear();

        CustomerLookupServiceImpl service = new CustomerLookupServiceImpl(customerRepository);

        // findProfileByEmail
        assertThat(service.findProfileByEmail(null)).isEmpty();
        assertThat(service.findProfileByEmail("   ")).isEmpty();
        assertThat(service.findProfileByEmail("nonexistent@example.com")).isEmpty();
        assertThat(service.findProfileByEmail("employee@example.com")).isEmpty();
        assertThat(service.findProfileByEmail("second@example.com"))
                .contains(new CustomerProfileSnapshot(
                        account2.getAccountId(),
                        "Second User",
                        "0988776655",
                        "second@example.com"
                ));

        // findFirstProfile
        assertThat(service.findFirstProfile()).isPresent();
        assertThat(service.findFirstProfile().get().email()).isEqualTo("first@example.com");
    }
}
