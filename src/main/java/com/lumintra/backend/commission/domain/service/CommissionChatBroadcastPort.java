package com.lumintra.backend.commission.domain.service;

import com.lumintra.backend.commission.api.response.CommissionMessageResponse;

/**
 * Outbound port for pushing newly persisted commission chat messages to subscribed WebSocket clients.
 */
public interface CommissionChatBroadcastPort {
    void broadcastMessage(Long commissionId, CommissionMessageResponse message);
}