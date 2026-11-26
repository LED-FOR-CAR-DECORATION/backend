package com.lumintra.backend.commission;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.commission.api.controller.CommissionRevisionConfirmationController;
import com.lumintra.backend.commission.api.request.ConfirmRevisionCardRequest;
import com.lumintra.backend.commission.api.response.ConfirmRevisionCardResponse;
import com.lumintra.backend.commission.application.ConfirmRevisionCardUseCase;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.LockModeType;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

class RevisionConfirmationSwaggerTest {
    @Test
    void documentsEndpointDtosAndTransactionalLockContract() throws Exception {
        var controller = CommissionRevisionConfirmationController.class;
        assertThat(controller.getAnnotation(Tag.class)).isNotNull();
        var method =
                controller.getMethod(
                        "confirm",
                        Authentication.class,
                        Long.class,
                        ConfirmRevisionCardRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(a -> a.responseCode())
                .contains("200", "400", "401", "403", "404", "409", "500");
        for (var dto :
                new Class<?>[] {
                    ConfirmRevisionCardRequest.class, ConfirmRevisionCardResponse.class
                }) {
            assertThat(dto.getAnnotation(Schema.class)).isNotNull();
            for (var component : dto.getRecordComponents()) {
                assertThat(dto.getDeclaredField(component.getName()).getAnnotation(Schema.class))
                        .isNotNull();
            }
        }
        assertThat(
                        CommissionRequestRepository.class
                                .getMethod("findForRevisionConfirmation", Long.class)
                                .getAnnotation(Lock.class)
                                .value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(
                        ConfirmRevisionCardUseCase.class
                                .getMethod("execute", Long.class, Long.class, Long.class)
                                .getAnnotation(Transactional.class))
                .isNotNull();
    }
}
