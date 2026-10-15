package com.eljaibo.pos.realtime.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

/** Outbound authorization is memory-only; revoked sessions cannot consume queued events. */
public class WebSocketDeliveryInterceptor implements ExecutorChannelInterceptor {
    private final WebSocketSessionRegistry sessions;

    public WebSocketDeliveryInterceptor(WebSocketSessionRegistry sessions) { this.sessions = sessions; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE
                && !sessions.isAuthorized(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()))) {
            return null;
        }
        return message;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        // Recheck on the executor thread: the session may have been revoked while queued.
        return preSend(message, channel);
    }
}
