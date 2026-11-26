package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.UpdateStoreProductStatusController;
import com.lumintra.backend.store.api.response.UpdatedStoreProductStatusResponse;
import com.lumintra.backend.store.application.UpdateStoreProductStatusUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(UpdateStoreProductStatusController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UpdateStoreProductStatusControllerTest {
    private static final String PATH = "/api/management/admin/store/products/42/status";
    private static final String BODY = "{\"status\":\"PUBLISHED\"}";

    @Autowired MockMvc mvc;
    @MockitoBean UpdateStoreProductStatusUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void permitsManagersAndAdmins() throws Exception {
        when(useCase.execute(eq(42L), any()))
                .thenReturn(new UpdatedStoreProductStatusResponse(42L, ProductStatus.PUBLISHED));
        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(
                            patch(PATH)
                                    .with(user("1").roles(role))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        }
    }

    @Test
    void rejectsUnauthorizedCallers() throws Exception {
        mvc.perform(patch(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        patch(PATH)
                                .with(user("1").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsInvalidStatuses() throws Exception {
        for (String body :
                List.of(
                        "{}",
                        "{\"status\":\"DELETED\"}",
                        "{\"status\":\"UNKNOWN\"}",
                        "{\"status\":1}")) {
            mvc.perform(
                            patch(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsMissingProductTo404() throws Exception {
        when(useCase.execute(eq(42L), any())).thenThrow(new ProductNotFoundException());
        mvc.perform(
                        patch(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
