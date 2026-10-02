package com.example.distrobackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TRIP_TOPIC = Pattern.compile(
            "^/topic/trips/[0-9a-fA-F-]{36}/location$");

    private final JwtAuthenticationService authenticationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            try {
                Authentication authentication = authenticationService.authenticateBearer(authorization);
                accessor.setUser(authentication);
            } catch (RuntimeException ex) {
                throw new MessagingException("WebSocket authentication failed", ex);
            }
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null
                    || destination == null
                    || !TRIP_TOPIC.matcher(destination).matches()) {
                throw new MessagingException("Unauthorized or invalid tracking subscription");
            }
        }

        return message;
    }
}
