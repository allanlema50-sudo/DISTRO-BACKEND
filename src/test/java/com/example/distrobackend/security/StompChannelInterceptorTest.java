package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

    @Mock
    private JwtAuthenticationService authenticationService;

    @Mock
    private TripAccessService tripAccessService;

    private StompChannelInterceptor interceptor;
    private Authentication authentication;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        interceptor = new StompChannelInterceptor(authenticationService, tripAccessService);
        tripId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.DRIVER, null, null,
                Instant.now().plusSeconds(60));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void rejectsClientPublishingToBrokerDestination() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, authentication,
                "/topic/trips/" + tripId + "/location");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel()))
                .isInstanceOf(MessagingException.class)
                .hasMessage("Client messages must target an application destination");
    }

    @Test
    void rejectsClientSendWithoutAuthentication() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, null, "/app/trips/location");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel()))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void permitsAuthorizedTripSubscription() {
        when(tripAccessService.canSubscribe(tripId,
                (AuthenticatedUser) authentication.getPrincipal())).thenReturn(true);
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, authentication,
                "/topic/trips/" + tripId + "/location");

        assertThat(interceptor.preSend(message, messageChannel())).isSameAs(message);
        verify(tripAccessService).canSubscribe(tripId,
                (AuthenticatedUser) authentication.getPrincipal());
    }

    @Test
    void rejectsUnauthorizedTripSubscription() {
        when(tripAccessService.canSubscribe(tripId,
                (AuthenticatedUser) authentication.getPrincipal())).thenReturn(false);
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, authentication,
                "/topic/trips/" + tripId + "/location");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel()))
                .isInstanceOf(MessagingException.class)
                .hasMessage("Unauthorized or invalid tracking subscription");
    }

    private static Message<byte[]> stompMessage(
            StompCommand command, Authentication authentication, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static org.springframework.messaging.MessageChannel messageChannel() {
        return new org.springframework.messaging.MessageChannel() {
            @Override
            public boolean send(Message<?> message) {
                return true;
            }

            @Override
            public boolean send(Message<?> message, long timeout) {
                return true;
            }
        };
    }
}
