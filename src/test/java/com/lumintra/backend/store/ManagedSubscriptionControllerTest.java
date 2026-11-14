package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.ManagedSubscriptionController;
import com.lumintra.backend.store.api.response.ManagedSubscriptionResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@WebMvcTest(ManagedSubscriptionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedSubscriptionControllerTest {
    private static final String PATH = "/api/management/admin/store/subscriptions";

    @Autowired MockMvc mvc;
    @MockitoBean SubscriptionDetailRepository subscriptions;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void managerReceivesSubscriptionPlansWithExactLifetimeField() throws Exception {
        when(subscriptions.listManagedPlans())
                .thenReturn(
                        List.of(
                                new ManagedSubscriptionResponse(
                                        7L,
                                        2L,
                                        "LIFETIME",
                                        "Lifetime",
                                        BigDecimal.TEN,
                                        true,
                                        null,
                                        true,
                                        ProductStatus.PUBLISHED,
                                        Instant.parse("2026-11-14T00:00:00Z"))));
        mvc.perform(get(PATH).with(user("1").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subscriptionDetailId").value(7))
                .andExpect(jsonPath("$.data[0].isLifetime").value(true));
    }

    @Test
    void rejectsGuestAndCustomer() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("1").roles("CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(subscriptions);
    }
}
