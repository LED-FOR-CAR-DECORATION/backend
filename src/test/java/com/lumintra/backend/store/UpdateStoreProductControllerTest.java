package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.UpdateStoreProductController;
import com.lumintra.backend.store.api.response.UpdatedStoreProductResponse;
import com.lumintra.backend.store.application.UpdateStoreProductUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

@WebMvcTest(UpdateStoreProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UpdateStoreProductControllerTest {
    private static final String PATH = "/api/management/admin/store/products/42";
    private static final String BODY = "{\"productName\":\"LED\",\"basePrice\":0}";

    @Autowired MockMvc mvc;
    @MockitoBean UpdateStoreProductUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void permitsManagersAndAdmins() throws Exception {
        when(useCase.execute(eq(42L), any()))
                .thenReturn(
                        new UpdatedStoreProductResponse(
                                42L,
                                "LED",
                                ProductType.LED,
                                BigDecimal.ZERO,
                                null,
                                null,
                                List.of(),
                                ProductStatus.PUBLISHED,
                                List.of()));
        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(
                            put(PATH)
                                    .with(user("1").roles(role))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productId").value(42));
        }
    }

    @Test
    void rejectsUnauthorizedCallers() throws Exception {
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        put(PATH)
                                .with(user("1").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsInvalidPriceTagsAndGallery() throws Exception {
        for (String ending : List.of(":-1}", ":0,\"tagIds\":[0]}", ":0,\"galleryUrls\":[null]}")) {
            mvc.perform(
                            put(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(BODY.replace(":0}", ending)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsMissingProductAndTagTo404() throws Exception {
        when(useCase.execute(eq(42L), any()))
                .thenThrow(new ProductNotFoundException())
                .thenThrow(new StoreProductTagNotFoundException());
        for (String code : List.of("PRODUCT_NOT_FOUND", "TAG_NOT_FOUND")) {
            mvc.perform(
                            put(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(code));
        }
    }
}
