package com.lumintra.backend.commission;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.commission.api.controller.CommissionRevisionCancellationController;
import com.lumintra.backend.commission.application.CancelRevisionCheckoutUseCase;
import com.lumintra.backend.payment.application.CreatePaymentSessionUseCase;
import com.lumintra.backend.payment.domain.model.CancelPaymentSessionCommand;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

class RevisionCancellationSwaggerTest {
    @Test
    void documentsHttpAndTransactionalCancellationContract() throws Exception {
        var c = CommissionRevisionCancellationController.class;
        assertThat(c.getAnnotation(Tag.class)).isNotNull();
        var method = c.getMethod("cancel", Authentication.class, Long.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(a -> a.responseCode())
                .contains("204", "400", "401", "403", "404", "409", "500");
        assertThat(
                        CancelRevisionCheckoutUseCase.class
                                .getMethod("execute", Long.class, Long.class)
                                .getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(
                        CreatePaymentSessionUseCase.class
                                .getMethod("cancelSession", CancelPaymentSessionCommand.class)
                                .getAnnotation(Transactional.class))
                .isNotNull();
        var update =
                PaymentTransactionRepository.class
                        .getMethod("markAsExpiredIfPending", Long.class, String.class)
                        .getAnnotation(Modifying.class);
        assertThat(update.clearAutomatically()).isTrue();
        assertThat(update.flushAutomatically()).isTrue();
    }
}
