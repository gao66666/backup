package com.workspace.service;

import com.workspace.util.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceWebSocketAuthInterceptorTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SPACE_ID =
            UUID.fromString("0e6e7d23-cf09-4418-8bfa-72bea947361f");

    @Mock
    private JwtService jwtService;

    @Mock
    private SpaceMemberService spaceMemberService;

    private WorkspaceWebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WorkspaceWebSocketAuthInterceptor(
                jwtService,
                spaceMemberService
        );
    }

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        when(jwtService.verify("valid-token")).thenReturn(USER_ID);
        Message<byte[]> message = message(
                StompCommand.CONNECT,
                null,
                "Bearer valid-token",
                false
        );

        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertEquals(USER_ID.toString(), accessor.getUser().getName());
    }

    @Test
    void permitsMemberSubscription() {
        when(spaceMemberService.getRole(USER_ID, SPACE_ID)).thenReturn("viewer");
        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE,
                "/topic/spaces/" + SPACE_ID + "/nodes",
                null,
                true
        );

        assertEquals(message, interceptor.preSend(message, null));
    }

    @Test
    void rejectsNonMemberSubscription() {
        when(spaceMemberService.getRole(USER_ID, SPACE_ID)).thenReturn(null);
        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE,
                "/topic/spaces/" + SPACE_ID + "/nodes",
                null,
                true
        );

        MessageDeliveryException error = assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message, null)
        );
        assertTrue(error.getMessage().contains("Not a member"));
    }

    @Test
    void rejectsClientSendFrames() {
        Message<byte[]> message = message(
                StompCommand.SEND,
                "/app/nodes",
                null,
                true
        );

        MessageDeliveryException error = assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message, null)
        );
        assertTrue(error.getMessage().contains("subscribe-only"));
    }

    private static Message<byte[]> message(
            StompCommand command,
            String destination,
            String authorization,
            boolean authenticated
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    USER_ID.toString(),
                    "",
                    List.of()
            ));
        }
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}
