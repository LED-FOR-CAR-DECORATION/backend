package com.lumintra.backend.iam;

import com.lumintra.backend.iam.api.controller.*;
import com.lumintra.backend.iam.api.request.*;
import com.lumintra.backend.iam.application.*;
import com.lumintra.backend.iam.domain.event.*;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.*;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.caching.*;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.*;
import com.lumintra.backend.shared.security.*;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({RegistrationTest.Config.class, AuthFlowTest.WebConfig.class})
@RecordApplicationEvents
class AuthFlowTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({AuthController.class, SessionAuthController.class, ManagementAuthController.class,
            SecurityConfig.class, JwtAuthenticationFilter.class})
    static class WebConfig {
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(mock(Tracer.class)); }
    }

    @Autowired WebApplicationContext context;
    @Autowired RegisterUseCase register;
    @Autowired LoginUseCase login;
    @Autowired TokenSessionService sessions;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserAccountRepository accounts;
    @Autowired AuthSessionRepository sessionRecords;
    @Autowired CustomerProfileRepository customers;
    @Autowired EmployeeProfileRepository employees;
    @Autowired GoogleIdentityRepository identities;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired LoginAttemptService attempts;
    @Autowired SafeCacheService cache;
    @Autowired GoogleIdentityVerifier verifier;
    @Autowired ApplicationEvents events;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(cache, verifier);
        identities.deleteAll(); sessionRecords.deleteAll(); employees.deleteAll(); customers.deleteAll(); accounts.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    UserAccountEntity customer(String email) {
        register.execute(new RegisterRequest("Test Customer", email, "Secure123!", "Secure123!"));
        return accounts.findByEmail(email).orElseThrow();
    }
    UserAccountEntity employee() {
        var account = new UserAccountEntity(); account.setEmail("staff@example.com"); account.setStatus("ACTIVE");
        account.setAccountType(AccountType.EMPLOYEE); account.setPasswordHash(encoder.encode("Secure123!")); accounts.saveAndFlush(account);
        var role = roles.findByRoleName("ADMIN").orElseGet(() -> {
            var value = new RoleEntity(); value.setRoleName("ADMIN"); value.setPermissions(Set.of()); return roles.saveAndFlush(value);
        });
        var profile = new EmployeeProfileEntity(); profile.setAccount(account); profile.setRole(role); profile.setFullName("Staff"); employees.saveAndFlush(profile);
        return account;
    }
    String loginBody(String email, String password) { return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"; }
    LoginRequest credentials(String email) { return new LoginRequest(email, "Secure123!"); }

    @Test void publicLoginAndManagementScopeAreEnforced() throws Exception {
        customer("customer@example.com"); employee();
        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody("customer@example.com", "Secure123!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").isString());
        mvc.perform(post("/api/management/auth/login").contentType("application/json").content(loginBody("customer@example.com", "Secure123!")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MANAGEMENT_ACCESS_DENIED"));
        mvc.perform(post("/api/management/auth/login").contentType("application/json").content(loginBody("staff@example.com", "Secure123!")))
                .andExpect(status().isOk());
        assertThat(events.stream(StaffLoginSuccessEvent.class).count()).isEqualTo(1);
    }

    @Test void tokenDurationsTypesRolesAndRefreshStorageAreCorrect() {
        employee();
        var pair = login.execute(credentials("staff@example.com"), true);
        var access = tokens.parseAccessToken(pair.getAccessToken()); var refresh = tokens.parseRefreshToken(pair.getRefreshToken());
        assertThat(Duration.between(access.getIssuedAt().toInstant(), access.getExpiration().toInstant())).isEqualTo(Duration.ofMinutes(15));
        assertThat(Duration.between(refresh.getIssuedAt().toInstant(), refresh.getExpiration().toInstant()).toSeconds()).isBetween(2591999L, 2592000L);
        assertThat(tokens.getAuthentication(pair.getAccessToken()).getAuthorities()).extracting("authority").contains("ROLE_ADMIN");
        assertThat(sessionRecords.findAll().getFirst().getRefreshHash()).hasSize(64).isNotEqualTo(pair.getRefreshToken());
        assertThatThrownBy(() -> tokens.parseAccessToken(pair.getRefreshToken())).isInstanceOf(AuthFlowException.class);
        assertThatThrownBy(() -> sessions.refresh(pair.getAccessToken())).isInstanceOf(AuthFlowException.class);
    }

    @Test void refreshRotatesAndRejectsReplayEvenForConcurrentRequests() throws Exception {
        customer("customer@example.com"); var pair = login.execute(credentials("customer@example.com"));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> rotate = () -> { start.await(); try { sessions.refresh(pair.getRefreshToken()); return true; } catch (AuthFlowException ex) { return false; } };
            var first = executor.submit(rotate); var second = executor.submit(rotate); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + pair.getRefreshToken() + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test void refreshEndpointIgnoresExpiredAccessHeaderAndValidatesPayload() throws Exception {
        customer("customer@example.com"); var pair = login.execute(credentials("customer@example.com"));
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer expired")
                        .contentType("application/json").content("{\"refreshToken\":\"" + pair.getRefreshToken() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void lockoutAfterFiveFailuresResetsAfterExpiryAndAuditsStaff() throws Exception {
        var employee = employee();
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/management/auth/login").contentType("application/json").content(loginBody(employee.getEmail(), "wrong"))).andExpect(status().isUnauthorized());
        var locked = accounts.findById(employee.getAccountId()).orElseThrow();
        assertThat(locked.getFailedAttempts()).isEqualTo(5);
        assertThat(locked.getLockoutUntil()).isBetween(Instant.now().plusSeconds(890), Instant.now().plusSeconds(901));
        mvc.perform(post("/api/management/auth/login").contentType("application/json").content(loginBody(employee.getEmail(), "Secure123!"))).andExpect(status().isLocked());
        assertThat(events.stream(StaffAccountLockedEvent.class).count()).isEqualTo(1);
        locked.setLockoutUntil(Instant.now().minusSeconds(1)); accounts.saveAndFlush(locked);
        login.execute(credentials(employee.getEmail()), true);
        var resetAccount = accounts.findById(employee.getAccountId()).orElseThrow();
        assertThat(resetAccount.getFailedAttempts()).isZero(); assertThat(resetAccount.getLockoutUntil()).isNull();
    }

    @Test void googleCreatesCustomerAndLinksExistingVerifiedEmail() throws Exception {
        when(verifier.verify("google-token")).thenReturn(new GoogleIdentityVerifier.GoogleIdentity("google-1", "new@gmail.com", "Google Customer", true));
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString());
        assertThat(accounts.findByEmail("new@gmail.com").orElseThrow().getAccountType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(identities.count()).isEqualTo(1);
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test void googleManagementRejectsUnknownEmailAndCustomers() throws Exception {
        when(verifier.verify("unknown")).thenReturn(new GoogleIdentityVerifier.GoogleIdentity("unknown", "unknown@gmail.com", "Unknown", true));
        mvc.perform(post("/api/management/auth/google").contentType("application/json").content("{\"idToken\":\"unknown\"}"))
                .andExpect(status().isForbidden());
        assertThat(accounts.count()).isZero();
        customer("customer@gmail.com");
        when(verifier.verify("customer")).thenReturn(new GoogleIdentityVerifier.GoogleIdentity("customer", "customer@gmail.com", "Customer", true));
        mvc.perform(post("/api/management/auth/google").contentType("application/json").content("{\"idToken\":\"customer\"}"))
                .andExpect(status().isForbidden());
        var staff = employee();
        when(verifier.verify("staff")).thenReturn(new GoogleIdentityVerifier.GoogleIdentity("staff", staff.getEmail(), "Staff", true));
        mvc.perform(post("/api/management/auth/google").contentType("application/json").content("{\"idToken\":\"staff\"}"))
                .andExpect(status().isOk());
        assertThat(events.stream(StaffLoginSuccessEvent.class).count()).isEqualTo(1);
    }

    @Test void untrustedGoogleEmailNeedsPasswordToLinkExistingAccount() throws Exception {
        customer("customer@example.com");
        when(verifier.verify("third-party")).thenReturn(new GoogleIdentityVerifier.GoogleIdentity("third-party", "customer@example.com", "Customer", false));
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"third-party\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GOOGLE_LINK_REQUIRED"));
        assertThat(identities.count()).isZero();
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"third-party\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(accounts.findByEmail("customer@example.com").orElseThrow().getFailedAttempts()).isEqualTo(1);
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"third-party\",\"password\":\"Secure123!\"}"))
                .andExpect(status().isOk());
        assertThat(identities.count()).isEqualTo(1);
    }

    @Test void disabledAccountCannotLoginOrRefresh() throws Exception {
        var account = customer("customer@example.com"); var pair = login.execute(credentials(account.getEmail()));
        account.setStatus("INACTIVE"); accounts.saveAndFlush(account);
        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(account.getEmail(), "Secure123!")))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> sessions.refresh(pair.getRefreshToken())).isInstanceOf(AuthFlowException.class);
    }
}
