package com.lumintra.backend.iam.application.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.lumintra.backend.iam.domain.event.InternalUserCreatedEvent;
import com.lumintra.backend.notification.application.InternalUserNotificationListener;
import com.lumintra.backend.notification.domain.service.InternalUserEmailProvider;
import com.lumintra.backend.shared.exception.notification.InternalUserEmailUnavailableException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

import java.time.Instant;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = InternalUserCreationListenersTest.Config.class)
class InternalUserCreationListenersTest {
    @Configuration
    @EnableTransactionManagement
    @Import({InternalUserAuditEventListener.class, InternalUserNotificationListener.class})
    static class Config {
        @Bean
        InternalUserEmailProvider provider() {
            return mock(InternalUserEmailProvider.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() {
                    return new Object();
                }

                protected void doBegin(Object t, TransactionDefinition d) {}

                protected void doCommit(DefaultTransactionStatus s) {}

                protected void doRollback(DefaultTransactionStatus s) {}
            };
        }
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager manager;
    @Autowired InternalUserEmailProvider provider;

    @Test
    @DisplayName(
            "N: Audit and email run only after commit; rollback and missing transactions suppress"
                    + " side effects, failed delivery never leaks credentials")
    void committedSideEffects() {
        reset(provider);
        var event =
                new InternalUserCreatedEvent(
                        42L,
                        1L,
                        "STAFF",
                        "staff@example.com",
                        "Private!Password123",
                        "delivery-1",
                        Instant.now());
        var audit = (Logger) LoggerFactory.getLogger(InternalUserAuditEventListener.class);
        var notification = (Logger) LoggerFactory.getLogger(InternalUserNotificationListener.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        audit.addAppender(capture);
        notification.addAppender(capture);
        try {
            var tx = new TransactionTemplate(manager);
            tx.executeWithoutResult(
                    s -> {
                        events.publishEvent(event);
                        verifyNoInteractions(provider);
                        assertThat(capture.list).isEmpty();
                    });
            verify(provider).sendCredentials(event.email(), event.password(), event.deliveryId());
            assertThat(capture.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.startsWith("Audit: Internal user created"));
            reset(provider);
            capture.list.clear();
            tx.executeWithoutResult(
                    s -> {
                        events.publishEvent(event);
                        s.setRollbackOnly();
                    });
            events.publishEvent(event);
            verifyNoInteractions(provider);
            assertThat(capture.list).isEmpty();
            doThrow(
                            new InternalUserEmailUnavailableException(
                                    new RuntimeException(event.password() + event.email())))
                    .when(provider)
                    .sendCredentials(anyString(), anyString(), anyString());
            tx.executeWithoutResult(s -> events.publishEvent(event));
            assertThat(capture.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.startsWith("Audit: Internal user created"))
                    .anyMatch(m -> m.startsWith("Internal account email delivery failed"));
            for (var entry : capture.list) {
                assertThat(entry.getFormattedMessage())
                        .doesNotContain(event.password(), event.email());
                assertThat(entry.getThrowableProxy()).isNull();
            }
            assertThat(
                            InternalUserNotificationListener.class
                                    .getMethod(
                                            "onInternalUserCreated", InternalUserCreatedEvent.class)
                                    .getAnnotation(
                                            org.springframework.scheduling.annotation.Async.class))
                    .isNotNull();
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(ex);
        } finally {
            audit.detachAppender(capture);
            notification.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    @DisplayName(
            "A: Notification delivery failure is recorded without logging the recipient or"
                    + " generated password")
    void deliveryFailureIsSafe() {
        reset(provider);
        var event =
                new InternalUserCreatedEvent(
                        42L,
                        1L,
                        "STAFF",
                        "private@example.com",
                        "Secret!123",
                        "delivery-2",
                        Instant.now());
        doThrow(new InternalUserEmailUnavailableException(new RuntimeException(event.password())))
                .when(provider)
                .sendCredentials(anyString(), anyString(), anyString());
        var logger = (Logger) LoggerFactory.getLogger(InternalUserNotificationListener.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            new InternalUserNotificationListener(provider).onInternalUserCreated(event);
            verify(provider).sendCredentials(event.email(), event.password(), event.deliveryId());
            assertThat(capture.list).hasSize(1);
            assertThat(capture.list.getFirst().getFormattedMessage())
                    .contains("accountId=42")
                    .doesNotContain(event.email(), event.password());
            assertThat(capture.list.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
