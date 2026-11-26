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
import com.lumintra.backend.store.api.controller.StoreTagController;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.ListStoreTagsUseCase;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(StoreTagController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class StoreTagControllerTest {
    private static final String PATH = "/api/store/tags";

    @Autowired MockMvc mvc;
    @MockitoBean ListStoreTagsUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void allowsGuestsCustomersAndManagers() throws Exception {
        when(useCase.execute())
                .thenReturn(
                        new StoreTagResponse[] {new StoreTagResponse(7L, "Animals", "Animal art")});
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tagId").value(7))
                .andExpect(jsonPath("$.data[0].name").value("Animals"))
                .andExpect(jsonPath("$.data[0].description").value("Animal art"));
        for (String role : List.of("CUSTOMER", "MANAGER")) {
            mvc.perform(get(PATH).with(user("1").roles(role))).andExpect(status().isOk());
        }
    }
}
