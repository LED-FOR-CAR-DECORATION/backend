package com.lumintra.backend.commission.infrastructure.websocket;

import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class StompCommissionChatBroadcastAdapter implements CommissionChatBroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(StompCommissionChatBroadcastAdapter.class);
    private static final String TOPIC_PREFIX = "/topic/commissions/";

    private final SimpMessagingTemplate messagingTemplate;
    public StompCommissionChatBroadcastAdapter(
            @Autowired(required = false)
            SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastMessage(Long commissionId, CommissionMessageResponse message) {
        if (commissionId == null || message == null || messagingTemplate == null) {
            return;
        }
        Runnable sendTask = () -> {
            String destination = TOPIC_PREFIX + commissionId;
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Broadcasted commission chat message [commissionId={}] [messageId={}] [destination={}]",
                    commissionId, message.messageId(), destination);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
        } else {
            sendTask.run();
        }
    }
}