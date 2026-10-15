package com.eljaibo.pos.realtime.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketDeliveryInterceptorTest {
    private final WebSocketSessionRegistry sessions = mock(WebSocketSessionRegistry.class);
    private final WebSocketDeliveryInterceptor interceptor = new WebSocketDeliveryInterceptor(sessions);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final MessageHandler handler = mock(MessageHandler.class);

    @Test
    void dropsEventRevokedWhileWaitingOnTheOutboundExecutor() {
        Message<?> event = message(SimpMessageType.MESSAGE);
        when(sessions.isAuthorized("session")).thenReturn(true);
        assertThat(interceptor.preSend(event, channel)).isSameAs(event);

        when(sessions.isAuthorized("session")).thenReturn(false);
        assertThat(interceptor.beforeHandle(event, channel, handler)).isNull();
        assertThat(interceptor.preSend(event, channel)).isNull();
    }

    @Test
    void allowsActiveUsersEventsAndTransportControlFrames() {
        Message<?> event = message(SimpMessageType.MESSAGE);
        when(sessions.isAuthorized("session")).thenReturn(true);
        assertThat(interceptor.beforeHandle(event, channel, handler)).isSameAs(event);

        when(sessions.isAuthorized("session")).thenReturn(false);
        Message<?> control = message(SimpMessageType.DISCONNECT_ACK);
        assertThat(interceptor.preSend(control, channel)).isSameAs(control);
        assertThat(interceptor.beforeHandle(control, channel, handler)).isSameAs(control);
    }

    private Message<?> message(SimpMessageType type) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId("session");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
