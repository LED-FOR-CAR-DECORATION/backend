package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.CustomerOwnedCharacterController;
import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.api.response.CustomerOwnedCharacterResponse;
import com.lumintra.backend.commerce.api.response.CustomerSubscriptionBenefitsResponse;
import com.lumintra.backend.commerce.application.CustomerSubscriptionEntitlementService;
import com.lumintra.backend.commerce.application.ListCustomerOwnedCharactersUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerOwnedCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CustomerOwnedCharacterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListCustomerOwnedCharactersUseCase listCustomerOwnedCharactersUseCase;

    @MockitoBean
    private CustomerSubscriptionEntitlementService customerSubscriptionEntitlementService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("GET /api/customer/characters/owned: when authenticated customer with items, returns 200 OK")
    void listOwnedCharacters_whenAuthenticatedCustomer_shouldReturn200OkWithList() throws Exception {
        Instant purchasedAt = Instant.parse("2026-11-15T17:00:00Z");

        CustomerOwnedCharacterResponse item = new CustomerOwnedCharacterResponse(
                5L,
                "Samurai Cat",
                "https://cdn.lumintra.shop/characters/5/preview.gif",
                16,
                60,
                1042L,
                28L,
                "Samurai Cat Animated Edition",
                new BigDecimal("50000.00"),
                purchasedAt
        );

        when(listCustomerOwnedCharactersUseCase.execute(42L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/customer/characters/owned")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].characterId").value(5))
                .andExpect(jsonPath("$.data[0].characterName").value("Samurai Cat"))
                .andExpect(jsonPath("$.data[0].previewGifUrl").value("https://cdn.lumintra.shop/characters/5/preview.gif"))
                .andExpect(jsonPath("$.data[0].frameCount").value(16))
                .andExpect(jsonPath("$.data[0].frameDelayMs").value(60))
                .andExpect(jsonPath("$.data[0].orderId").value(1042))
                .andExpect(jsonPath("$.data[0].storeProductId").value(28))
                .andExpect(jsonPath("$.data[0].productName").value("Samurai Cat Animated Edition"))
                .andExpect(jsonPath("$.data[0].purchasedPrice").value(50000.00))
                .andExpect(jsonPath("$.data[0].purchasedAt").value("2026-11-15T17:00:00Z"));

        verify(listCustomerOwnedCharactersUseCase).execute(42L);
    }

    @Test
    @DisplayName("GET /api/customer/characters/owned: when customer has no characters, returns 200 OK with empty array")
    void listOwnedCharacters_whenEmptyList_shouldReturn200OkWithEmptyArray() throws Exception {
        when(listCustomerOwnedCharactersUseCase.execute(42L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/customer/characters/owned")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(listCustomerOwnedCharactersUseCase).execute(42L);
    }

    @Test
    @DisplayName("GET /api/customer/characters/owned: when unauthenticated, returns 401 Unauthorized")
    void listOwnedCharacters_whenUnauthenticated_shouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/customer/characters/owned")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listCustomerOwnedCharactersUseCase);
    }

    @Test
    @DisplayName("GET /api/customer/characters/owned: when role is not CUSTOMER (e.g. DESIGNER), returns 403 Forbidden")
    void listOwnedCharacters_whenNonCustomerRole_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/customer/characters/owned")
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listCustomerOwnedCharactersUseCase);
    }

    @Test
    @DisplayName("GET /api/customer/characters/owned: when user subject is not a numeric id, returns 401 Unauthorized")
    void listOwnedCharacters_whenInvalidCustomerId_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/customer/characters/owned")
                        .with(SecurityMockMvcRequestPostProcessors.user("non-numeric").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listCustomerOwnedCharactersUseCase);
    }

    @Test
    @DisplayName("GET /api/customer/subscriptions/active: returns active subscription details when present")
    void getActiveSubscription_whenAuthenticatedCustomer_returnsActivePlan() throws Exception {
        Instant activatedAt = Instant.parse("2026-11-24T06:00:00Z");
        CustomerActiveSubscriptionResponse sub = new CustomerActiveSubscriptionResponse(
                10L, 16L, 42L, "VIP_LIFETIME", "Lumintra All-Access VIP Pass",
                true, null, true, 3, 10, activatedAt, null
        );
        when(customerSubscriptionEntitlementService.findActiveSubscription(42L))
                .thenReturn(java.util.Optional.of(sub));

        mockMvc.perform(get("/api/customer/subscriptions/active")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("VIP_LIFETIME"))
                .andExpect(jsonPath("$.data.allAccessCharacters").value(true));

        verify(customerSubscriptionEntitlementService).findActiveSubscription(42L);
    }

    @Test
    @DisplayName("GET /api/customer/subscriptions/benefits: returns aggregated benefits summary for customer")
    void getSubscriptionBenefits_whenAuthenticatedCustomer_returnsSummary() throws Exception {
        Instant activatedAt = Instant.parse("2026-11-24T06:00:00Z");
        CustomerActiveSubscriptionResponse activeSub = new CustomerActiveSubscriptionResponse(
                10L, 16L, 42L, "VIP_LIFETIME", "Gói VIP Mở Khóa Toàn Bộ Lumintra",
                true, null, true, 0, 0, 3, activatedAt, null, new BigDecimal("999999.00")
        );
        CustomerSubscriptionBenefitsResponse benefits = new CustomerSubscriptionBenefitsResponse(
                true,
                true,
                3,
                3,
                1,
                1,
                new BigDecimal("999999.00"),
                List.of(activeSub),
                List.of()
        );
        when(customerSubscriptionEntitlementService.getCustomerSubscriptionBenefits(42L))
                .thenReturn(benefits);

        mockMvc.perform(get("/api/customer/subscriptions/benefits")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasActiveSubscription").value(true))
                .andExpect(jsonPath("$.data.allAccessCharacters").value(true))
                .andExpect(jsonPath("$.data.maxMobileProfiles").value(3))
                .andExpect(jsonPath("$.data.maxCommissionRevisions").value(1))
                .andExpect(jsonPath("$.data.upgradeCreditAvailable").value(999999.0))
                .andExpect(jsonPath("$.data.activeSubscriptions", hasSize(1)));

        verify(customerSubscriptionEntitlementService).getCustomerSubscriptionBenefits(42L);
    }
}
