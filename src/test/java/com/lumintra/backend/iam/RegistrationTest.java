package com.lumintra.backend.iam;

import com.lumintra.backend.iam.api.controller.AuthController;
import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterUseCase;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import io.micrometer.tracing.Tracer;
import io.swagger.v3.core.converter.ModelConverters;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(RegistrationTest.Config.class)
class RegistrationTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    @Import({RegisterUseCase.class, LoginUseCase.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.lumintra.backend.iam.infrastructure.persistence.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean LoginAttemptService loginAttemptService() { return mock(LoginAttemptService.class); }
        @Bean JwtTokenProvider tokenProvider() { return new JwtTokenProvider("test-only-secret-at-least-thirty-two-bytes-long"); }
    }

    @Autowired RegisterUseCase register;
    @Autowired LoginUseCase login;
    @MockitoSpyBean UserAccountRepository accounts;
    @MockitoSpyBean CustomerProfileRepository profiles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;
    MockMvc mvc;

    @BeforeEach void setup() {
        profiles.deleteAll();
        accounts.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, register))
                .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class))).build();
    }

    String request(String email, String password, String confirmation) {
        return """
                {"fullName":" Nguyễn Văn A ","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(email, password, confirmation);
    }

    @Test void createsCustomerProfileAndCanLoginWithNormalizedEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(request(" New@Example.com ", "Secure123!", "Secure123!")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        var account = accounts.findByEmail("new@example.com").orElseThrow();
        assertThat(account.getAccountType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(encoder.matches("Secure123!", account.getPasswordHash())).isTrue();
        assertThat(profiles.findById(account.getAccountId()).orElseThrow().getFullName()).isEqualTo("Nguyễn Văn A");
        var response = login.execute(new LoginRequest(" NEW@EXAMPLE.COM ", "Secure123!"));
        assertThat(tokens.validateToken(response.getAccessToken())).isTrue();
    }

    @Test void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register.execute(new RegisterRequest("User Name", "new@example.com", "Secure123!", "Secure123!"));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(request("NEW@EXAMPLE.COM", "Secure123!", "Secure123!")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test void rejectsInvalidFieldsWithoutWritingRecords() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"fullName\":\" \",\"email\":\"bad\",\"password\":\"weak\",\"confirmPassword\":\"different\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field == 'fullName')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field == 'email')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field == 'password')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field == 'confirmPassword')]").isNotEmpty());
        assertThat(accounts.count()).isZero();
    }

    @Test void rejectsUtf8PasswordOverBcryptLimit() throws Exception {
        String password = "Secure123!" + "ệ".repeat(25);
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(request("new@example.com", password, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'password')]").isNotEmpty());
        assertThat(accounts.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Short1!", "lowercase1!", "UPPERCASE1!", "NoDigits!!", "NoSpecial123", "Secure 123!"})
    void rejectsWeakPasswordsAtTheBackend(String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(request("new@example.com", password, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'password')]").isNotEmpty());
        assertThat(accounts.count()).isZero();
    }

    @Test void databaseRejectsDuplicateEvenWhenPrecheckMissesIt() throws Exception {
        register.execute(new RegisterRequest("User Name", "new@example.com", "Secure123!", "Secure123!"));
        // Simulate a competing transaction inserting after the application pre-check.
        doReturn(false).when(accounts).existsByEmailIgnoreCase("new@example.com");
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(request("new@example.com", "Secure123!", "Secure123!")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(profiles.count()).isEqualTo(1);
    }

    @Test void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void swaggerRegistrationSchemaExposesOnlyClientFields() {
        var schema = ModelConverters.getInstance().readAll(RegisterRequest.class).get("RegisterRequest");
        assertThat(schema.getProperties().keySet())
                .containsExactlyInAnyOrder("fullName", "email", "password", "confirmPassword");
        assertThat(schema.getRequired()).contains("fullName", "email", "password", "confirmPassword");
        var passwordSchema = (io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get("password");
        var confirmationSchema = (io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get("confirmPassword");
        assertThat(passwordSchema.getWriteOnly()).isTrue();
        assertThat(confirmationSchema.getExample()).isEqualTo("Secure123!");
    }

    @Test void rollsBackAccountWhenProfileWriteFails() {
        doThrow(new IllegalStateException("test profile failure")).when(profiles).saveAndFlush(any());
        assertThatThrownBy(() -> register.execute(new RegisterRequest("User Name", "rollback@example.com", "Secure123!", "Secure123!")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(accounts.findByEmail("rollback@example.com")).isEmpty();
        assertThat(profiles.count()).isZero();
    }
}
