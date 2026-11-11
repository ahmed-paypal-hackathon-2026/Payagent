package com.payagent.realtime;

import com.payagent.auth.JwtTokenProvider;
import com.payagent.realtime.model.DealEventEnvelope;
import com.payagent.realtime.security.WebSocketAuthChannelInterceptor;
import com.payagent.realtime.service.AuditEventService;
import com.payagent.realtime.service.DealWebSocketBroker;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.port.DealPort;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketRealtimeTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private DealPort dealPort;

    @MockBean
    private AuditEventService auditEventService;

    @Autowired
    private DealWebSocketBroker dealWebSocketBroker;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider, dealPort, auditEventService);
    }

    @Test
    void connect_validJwt_authenticatesPrincipal() {
        UUID userId = UUID.randomUUID();
        String email = "buyer@test.local";
        String token = jwtTokenProvider.createToken(userId, email);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = mock(MessageChannel.class);
        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo(email);
    }

    @Test
    void connect_missingJwt_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = mock(MessageChannel.class);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    void connect_invalidJwt_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-garbage-token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = mock(MessageChannel.class);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    @Test
    void subscribe_dealChannel_participantAllowed_andTriggersReplay() {
        UUID dealId = UUID.randomUUID();
        String email = "buyer@test.local";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/deals/" + dealId);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(email, null));
        accessor.addNativeHeader("afterSeq", "3");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();

        verify(dealPort).assertParty(dealId, email);
        verify(auditEventService).replayForSubscriber(dealId, 3L, email);
    }

    @Test
    void subscribe_missingPrincipal_isRejected() {
        UUID dealId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/deals/" + dealId);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthenticated SUBSCRIBE");
    }

    @Test
    void subscribe_dealChannel_nonParticipantRejected() {
        UUID dealId = UUID.randomUUID();
        String intruderEmail = "intruder@test.local";
        doThrow(new SettlementForbiddenException("Not authorized")).when(dealPort).assertParty(dealId, intruderEmail);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/deals/" + dealId);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(intruderEmail, null));

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized to subscribe");
    }

    @Test
    void broadcastDealEvent_sendsEnvelopeToTopic() {
        UUID dealId = UUID.randomUUID();
        DealEventEnvelope envelope = new DealEventEnvelope(
                dealId,
                1L,
                "TERMS_AGREED",
                Map.of("status", "AGREED"),
                Instant.now()
        );

        // Validates no exception on broadcast
        dealWebSocketBroker.broadcastDealEvent(dealId, envelope);
    }
}
