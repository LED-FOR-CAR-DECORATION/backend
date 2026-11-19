package com.lumintra.backend.commission;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.commission.api.controller.CommissionRevisionCardController;
import com.lumintra.backend.commission.api.request.PostRevisionCardRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RevisionCardSwaggerTest {
    @Test
    void documentsContractAndRestrictsControllerToStaff() throws Exception {
        var type = CommissionRevisionCardController.class;
        assertThat(type.getAnnotation(Tag.class)).isNotNull();
        var method =
                type.getDeclaredMethod(
                        "post",
                        org.springframework.security.core.Authentication.class,
                        Long.class,
                        PostRevisionCardRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(annotation -> annotation.responseCode())
                .contains("201", "400", "401", "403", "404", "409");
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STAFF')");
        assertThat(PostRevisionCardRequest.class.getAnnotation(Schema.class)).isNotNull();
    }
}
