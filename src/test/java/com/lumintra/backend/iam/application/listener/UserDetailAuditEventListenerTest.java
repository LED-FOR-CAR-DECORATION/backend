package com.lumintra.backend.iam.application.listener;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.lumintra.backend.iam.domain.event.UserDetailViewedEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserDetailAuditEventListenerTest.Config.class)
class UserDetailAuditEventListenerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(UserDetailAuditEventListener.class)
    static class Config {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {}

                @Override
                protected void doCommit(DefaultTransactionStatus status) {}

                @Override
                protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName(
            "N: Audit log is emitted once after commit, never before commit, on rollback or without"
                    + " a transaction")
    void committedAuditOnly() {
        Logger logger = (Logger) LoggerFactory.getLogger(UserDetailAuditEventListener.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var event =
                new UserDetailViewedEvent(1L, 42L, "ADMIN", Instant.parse("2026-11-10T12:00:00Z"));
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        try {
            transaction.executeWithoutResult(
                    status -> {
                        events.publishEvent(event);
                        assertThat(appender.list).isEmpty();
                    });
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains(
                            "Audit: Internal user detail viewed",
                            "[viewerAccountId=1]",
                            "[targetAccountId=42]",
                            "[viewerRole=ADMIN]");
            appender.list.clear();
            transaction.executeWithoutResult(
                    status -> {
                        events.publishEvent(event);
                        status.setRollbackOnly();
                    });
            assertThat(appender.list).isEmpty();
            events.publishEvent(event);
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
