package com.lumintra.backend.store;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.api.controller.ManagedStoreProductController;
import com.lumintra.backend.store.api.response.CreatedStoreProductResponse;
import com.lumintra.backend.store.application.CreateStoreProductUseCase;
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

@WebMvcTest(ManagedStoreProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedStoreProductControllerTest {
    private static final String PATH = "/api/management/admin/store/products";
    private static final String BODY =
            "{\"productName\":\"LED\",\"productType\":\"LED\",\"basePrice\":0}";

    @Autowired MockMvc mvc;
    @MockitoBean CreateStoreProductUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void managerAndAdminReceiveCreatedProduct() throws Exception {
        when(useCase.execute(any()))
                .thenReturn(
                        new CreatedStoreProductResponse(
                                42L,
                                "LED",
                                ProductType.LED,
                                BigDecimal.ZERO,
                                null,
                                null,
                                List.of(),
                                ProductStatus.UNPUBLISHED,
                                List.of(),
                                null,
                                null));
        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles(role))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.productId").value(42))
                    .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        }
    }

    @Test
    void rejectsUnauthorizedCallers() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void validatesTypeLinksPriceTagsAndNestedLedDetails() throws Exception {
        for (String body :
                List.of(
                        BODY.replace("\"LED\",\"basePrice\"", "\"CHARACTER\",\"basePrice\""),
                        BODY.replace(":0}", ":-1}"),
                        BODY.replace(":0}", ":0,\"tagIds\":[0]}"),
                        BODY.replace(
                                ":0}",
                                ":0,\"ledDetails\":{\"modelName\":\"LED\",\"stockQuantity\":-1}}"))) {
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void translatesReferenceFailures() throws Exception {
        for (Reason reason : Reason.values()) {
            doThrow(new StoreProductReferenceException(reason, "Invalid reference"))
                    .when(useCase)
                    .execute(any());
            int expected = reason == Reason.CHARACTER_VERSION_ALREADY_LINKED ? 409 : 404;
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().is(expected))
                    .andExpect(jsonPath("$.code").value(reason.name()));
        }
    }
}
