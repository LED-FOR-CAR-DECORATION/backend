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
import com.lumintra.backend.store.api.controller.ManagedBundleController;
import com.lumintra.backend.store.infrastructure.persistence.repository.ManagedBundleRepository;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagedBundleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedBundleControllerTest {
    private static final String PATH = "/api/management/admin/store/bundles";

    @Autowired MockMvc mvc;
    @MockitoBean ManagedBundleRepository bundles;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void bindsFilterAndPaginationForManager() throws Exception {
        var page = org.springframework.data.domain.PageRequest.of(1, 2);
        when(bundles.list("ARCHIVED", page))
                .thenReturn(org.springframework.data.domain.Page.empty(page));
        mvc.perform(
                        get(PATH)
                                .param("page", "1")
                                .param("size", "2")
                                .param("status", "ARCHIVED")
                                .with(user("1").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1));
        verify(bundles).list("ARCHIVED", page);
    }

    @Test
    void rejectsGuestsCustomersAndInvalidFilters() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("1").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).param("status", "INVALID").with(user("1").roles("MANAGER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("size", "0").with(user("1").roles("MANAGER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bundles);
    }
}
