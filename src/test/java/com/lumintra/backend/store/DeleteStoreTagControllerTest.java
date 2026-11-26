package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.DeleteStoreTagController;
import com.lumintra.backend.store.application.DeleteStoreTagUseCase;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(DeleteStoreTagController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DeleteStoreTagControllerTest {
    private static final String PATH = "/api/management/admin/store/tags/42";

    @Autowired MockMvc mvc;
    @MockitoBean DeleteStoreTagUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void allowsAdminAndManagerAndReturnsNoBody() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER")) {
            mvc.perform(delete(PATH).with(user("1").roles(role)))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
        verify(useCase, times(2)).execute(42L);
    }

    @Test
    void rejectsUnauthorizedCallersAndNonPositiveId() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH).with(user("1").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH.replace("/42", "/0")).with(user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsMissingOrDeletedProductTo404() throws Exception {
        doThrow(new StoreProductTagNotFoundException()).when(useCase).execute(42L);
        mvc.perform(delete(PATH).with(user("1").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }
}
