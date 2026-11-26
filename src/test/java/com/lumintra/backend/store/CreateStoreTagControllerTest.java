package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.CreateStoreTagController;
import com.lumintra.backend.store.api.request.CreateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.CreateStoreTagUseCase;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(CreateStoreTagController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CreateStoreTagControllerTest {
    private static final String PATH = "/api/management/admin/store/tags";

    @Autowired MockMvc mvc;
    @MockitoBean CreateStoreTagUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void allowsManagersAndAdminsAndTrimsName() throws Exception {
        when(useCase.execute(new CreateStoreTagRequest("Animals", null)))
                .thenReturn(new StoreTagResponse(7L, "Animals", null));
        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles(role))
                                    .contentType("application/json")
                                    .content("{\"name\":\"  Animals  \"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.tagId").value(7))
                    .andExpect(jsonPath("$.data.name").value("Animals"));
        }
    }

    @Test
    void rejectsUnauthorizedCallersAndInvalidNames() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content("{\"name\":\"Animals\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content("{\"name\":\"Animals\"}"))
                .andExpect(status().isForbidden());
        for (String payload :
                List.of("{}", "{\"name\":\"  \"}", "{\"name\":\"" + "x".repeat(101) + "\"}")) {
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(payload))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsDuplicateToConflict() throws Exception {
        when(useCase.execute(any())).thenThrow(new TagAlreadyExistsException());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content("{\"name\":\"ANIMALS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"));
    }
}
