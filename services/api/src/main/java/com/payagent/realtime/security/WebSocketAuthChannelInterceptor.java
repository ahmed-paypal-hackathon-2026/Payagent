package com.payagent.realtime.security;

import com.payagent.auth.JwtTokenProvider;
import com.payagent.realtime.service.AuditEventService;
import com.payagent.settlement.port.DealPort;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final DealPort dealPort;
    private final AuditEventService auditEventService;

    public WebSocketAuthChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            DealPort dealPort,
            @Lazy AuditEventService auditEventService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.dealPort = dealPort;
        this.auditEventService = auditEventService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getPasscode();
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new IllegalArgumentException("Missing Authorization header for STOMP connection");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("STOMP CONNECT rejected: invalid JWT token");
            throw new IllegalArgumentException("Invalid JWT token for STOMP connection");
        }

        String email = jwtTokenProvider.getEmail(token);
        Principal principal = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        if (accessor.isMutable()) {
            accessor.setUser(principal);
            log.info("STOMP CONNECT authenticated user: {}", email);
            return message;
        } else {
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            mutableAccessor.copyHeaders(accessor.toMap());
            mutableAccessor.setUser(principal);
            mutableAccessor.setLeaveMutable(true);
            log.info("STOMP CONNECT authenticated user: {} (mutable copy)", email);
            return org.springframework.messaging.support.MessageBuilder.createMessage(
                    message.getPayload(),
                    mutableAccessor.getMessageHeaders()
            );
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Principal user = accessor.getUser();
        String callerEmail = user != null ? user.getName() : null;

        if (destination.startsWith("/topic/deals/")) {
            String dealIdStr = destination.substring("/topic/deals/".length());
            try {
                UUID dealId = UUID.fromString(dealIdStr);
                if (callerEmail != null) {
                    dealPort.assertParty(dealId, callerEmail);
                    log.info("User {} authorized to subscribe to deal {}", callerEmail, dealId);

                    // Check for afterSeq header to trigger replay
                    String afterSeqStr = accessor.getFirstNativeHeader("afterSeq");
                    if (afterSeqStr != null && !afterSeqStr.isBlank()) {
                        long afterSeq = Long.parseLong(afterSeqStr);
                        auditEventService.replayForSubscriber(dealId, afterSeq, callerEmail);
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid deal UUID in destination: {}", destination);
                throw new IllegalArgumentException("Invalid deal ID in subscription destination");
            } catch (Exception e) {
                log.warn("Subscription forbidden for user {} on {}: {}", callerEmail, destination, e.getMessage());
                throw new SecurityException("Forbidden: Not authorized to subscribe to " + destination);
            }
        }
    }
}
