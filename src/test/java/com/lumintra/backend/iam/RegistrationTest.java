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
    @Import({RegisterUseCase.class, LoginUseCase.class, com.lumintra.backend.iam.application.RegisterAccountService.class, com.lumintra.backend.iam.application.TokenSessionService.class, com.lumintra.backend.iam.infrastructure.security.HybridLoginAttemptService.class, com.lumintra.backend.iam.application.GoogleAccountService.class, com.lumintra.backend.iam.application.GoogleLoginUseCase.class})
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
        @Bean com.lumintra.backend.iam.domain.service.EmailVerificationStore verificationStore() { return mock(com.lumintra.backend.iam.domain.service.EmailVerificationStore.class); }
        @Bean com.lumintra.backend.notification.domain.service.EmailProvider emailProvider() { return mock(com.lumintra.backend.notification.domain.service.EmailProvider.class); }
        @Bean com.lumintra.backend.iam.application.RegistrationVerificationService verification(com.lumintra.backend.iam.domain.service.EmailVerificationStore store, com.lumintra.backend.notification.domain.service.EmailProvider email, com.lumintra.backend.iam.application.RegisterAccountService accounts) { return new com.lumintra.backend.iam.application.RegistrationVerificationService(store, email, accounts, "test-only-otp-pepper-at-least-thirty-two-bytes"); }
        @Bean com.lumintra.backend.shared.caching.SafeCacheService cacheService() { return mock(com.lumintra.backend.shared.caching.SafeCacheService.class); }
        @Bean com.lumintra.backend.iam.domain.service.GoogleIdentityVerifier googleVerifier() { return mock(com.lumintra.backend.iam.domain.service.GoogleIdentityVerifier.class); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean JwtTokenProvider tokenProvider() { return new JwtTokenProvider("test-only-secret-at-least-thirty-two-bytes-long"); }
    }

    @Autowired RegisterUseCase register;
    @Autowired com.lumintra.backend.iam.application.RegistrationVerificationService verification;
    @Autowired LoginUseCase login;
    @MockitoSpyBean UserAccountRepository accounts;
    @MockitoSpyBean CustomerProfileRepository profiles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired com.lumintra.backend.iam.domain.service.EmailVerificationStore store;
    @Autowired com.lumintra.backend.notification.domain.service.EmailProvider email;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired EmployeeProfileRepository employees;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(store,email);
        refreshTokens.deleteAll(); employees.deleteAll();
        profiles.deleteAll();
        accounts.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, register),new com.lumintra.backend.iam.api.controller.RegistrationVerificationController(verification))
                .setControllerAdvice(new GlobalExceptionHandler(mock(Tracer.class))).build();
    }

    String request(String email, String password, String confirmation) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(email,password,confirmation);
    }
    @Test void createsPendingAccountThroughFactoriesAndRequiresEmailConfirmation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request(" New@Example.com ", "Secure123!", "Secure123!")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.data.fullName").doesNotExist());
        var account=accounts.findByEmail("new@example.com").orElseThrow();
        assertThat(account.getAccountType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(encoder.matches("Secure123!",account.getPasswordHash())).isTrue();
        assertThat(profiles.findById(account.getAccountId()).orElseThrow().getFullName()).isNull();
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"NEW@EXAMPLE.COM\",\"password\":\"Secure123!\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        var otp=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendRegistrationOtp(eq("new@example.com"),otp.capture(),anyString());
        assertThat(otp.getValue()).matches("[0-9]{6}");
        var digest=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).issue(eq("new@example.com"),digest.capture());
        assertThat(digest.getValue()).hasSize(64).isNotEqualTo(otp.getValue());
        when(store.consume(eq("new@example.com"),eq(digest.getValue()))).thenReturn(true);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\"NEW@example.com\",\"otp\":\""+otp.getValue()+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        var pair=login.execute(new LoginRequest(" NEW@EXAMPLE.COM ","Secure123!"));
        assertThat(tokens.validateToken(pair.getAccessToken())).isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\"new@example.com\",\"otp\":\""+otp.getValue()+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register.execute(new RegisterRequest("new@example.com","Secure123!","Secure123!"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request("NEW@EXAMPLE.COM","Secure123!","Secure123!")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThat(accounts.count()).isEqualTo(1);
    }
    @Test void rejectsInvalidFieldsWithoutWritingRecords() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request("bad","weak","different")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[?(@.field == 'email')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field == 'password')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field == 'confirmPassword')]").isNotEmpty());
        assertThat(accounts.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"Short1!","lowercase1!","UPPERCASE1!","NoDigits!!","NoSpecial123","Secure 123!"})
    void rejectsWeakPasswords(String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request("new@example.com",password,password)))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsUtf8PasswordOverBcryptLimit() throws Exception {
        String password="Secure123!"+"ệ".repeat(25);
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request("new@example.com",password,password)))
                .andExpect(status().isBadRequest());
    }
    @Test void rollbackOnProfileFailureDoesNotSendEmail() {
        doThrow(new IllegalStateException("test failure")).when(profiles).saveAndFlush(any());
        assertThatThrownBy(()->register.execute(new RegisterRequest("rollback@example.com","Secure123!","Secure123!"))).isInstanceOf(IllegalStateException.class);
        assertThat(accounts.count()).isZero(); verifyNoInteractions(email,store);
    }
    @Test void providerFailureLeavesPendingAccountAndAllowsResend() throws Exception {
        doThrow(new com.lumintra.backend.shared.exception.iam.EmailVerificationException("VERIFICATION_UNAVAILABLE","Test outage")).when(email).sendRegistrationOtp(anyString(),anyString(),anyString());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(request("new@example.com","Secure123!","Secure123!")))
                .andExpect(status().isServiceUnavailable());
        assertThat(accounts.findByEmail("new@example.com").orElseThrow().getStatus()).isEqualTo("PENDING_VERIFICATION");
        verify(store).invalidate(eq("new@example.com"),anyString());
        reset(email);
        mvc.perform(post("/api/auth/resend-verification").contentType("application/json").content("{\"email\":\"NEW@example.com\"}"))
                .andExpect(status().isAccepted());
    }
    @Test void invalidExpiredOrMissingOtpCannotActivateAccount() throws Exception {
        register.execute(new RegisterRequest("new@example.com","Secure123!","Secure123!"));
        when(store.consume(anyString(),anyString())).thenReturn(false);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\"new@example.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        assertThat(accounts.findByEmail("new@example.com").orElseThrow().getStatus()).isEqualTo("PENDING_VERIFICATION");
    }
    @Test void factoriesNormalizeEmailIndependentOfRequestDto() {
        var account=com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity.registerCustomer(" Customer@Example.COM ","hash");
        assertThat(account.getEmail()).isEqualTo("customer@example.com");
        account.setEmail(" MIXED@EXAMPLE.COM "); assertThat(account.getEmail()).isEqualTo("mixed@example.com");
        assertThat(com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerProfileEntity.forAccount(account).getAccount()).isSameAs(account);
    }
    @Test void registrationSwaggerHasNoFullNameField() {
        var schema=ModelConverters.getInstance().readAll(RegisterRequest.class).get("RegisterRequest");
        assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrder("email","password","confirmPassword");
        assertThat(schema.getRequired()).contains("email","password","confirmPassword");
    }
}
