package com.eljaibo.pos.realtime.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.UUID;

/** Batch-check before a broadcast so another instance's deactivation is seen before fan-out. */
public class WebSocketPublicationInterceptor implements ChannelInterceptor {
    private final WebSocketSessionRegistry sessions;

    public WebSocketPublicationInterceptor(WebSocketSessionRegistry sessions) { this.sessions = sessions; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        String prefix = "/topic/branches/";
        String suffix = "/sales";
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE
                && destination != null && destination.startsWith(prefix) && destination.endsWith(suffix)) {
            sessions.recheckBranch(UUID.fromString(destination.substring(prefix.length(), destination.length() - suffix.length())));
        }
        return message;
    }
}
