package com.payagent.realtime.service;

import com.payagent.realtime.model.DealEventEnvelope;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class DealWebSocketBroker {

    private static final Logger log = LoggerFactory.getLogger(DealWebSocketBroker.class);

    private final SimpMessagingTemplate messagingTemplate;

    public DealWebSocketBroker(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastDealEvent(UUID dealId, DealEventEnvelope envelope) {
        String destination = "/topic/deals/" + dealId;
        log.info("Broadcasting deal event to {}: seq={}, type={}", destination, envelope.seq(), envelope.eventType());
        messagingTemplate.convertAndSend(destination, envelope);
    }

    public void sendToUser(String userEmail, String destination, Object payload) {
        if (userEmail != null && !userEmail.isBlank()) {
            messagingTemplate.convertAndSendToUser(userEmail, destination, payload);
        }
    }
}
