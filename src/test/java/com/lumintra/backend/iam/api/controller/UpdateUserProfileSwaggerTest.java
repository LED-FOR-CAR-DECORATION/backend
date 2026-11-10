package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.UpdateCurrentProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.*;

class UpdateUserProfileSwaggerTest {
    @Test @DisplayName("N: PUT documents success, errors and Bearer authentication")
    void contract() throws Exception {
        var method=UpdateUserProfileController.class.getMethod("update",Authentication.class,UpdateCurrentProfileRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");
        assertThat(method.getAnnotation(ApiResponses.class).value()).extracting(v->v.responseCode()).containsExactlyInAnyOrder("200","400","401","403","404","500");
    }

    @Test @DisplayName("N: Request exposes only editable fields and documents every field")
    void safeRequest() {
        assertThat(UpdateCurrentProfileRequest.class.getRecordComponents()).extracting(v->v.getName()).containsExactly("name","phone","avatar");
        for(var field:UpdateCurrentProfileRequest.class.getDeclaredFields()) assertThat(field.getAnnotation(Schema.class)).isNotNull();
    }
}
