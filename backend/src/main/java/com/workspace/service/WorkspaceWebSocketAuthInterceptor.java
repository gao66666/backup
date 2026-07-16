package com.workspace.service;

import com.workspace.util.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 空间事件 WebSocket 的 STOMP 鉴权。
 *
 * CONNECT 帧校验 Bearer JWT；SUBSCRIBE 帧再次校验用户是否仍是目标空间成员。
 * 浏览器只能订阅空间事件，节点写操作继续统一走现有 REST API。
 */
@Component
public class WorkspaceWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern SPACE_NODE_TOPIC = Pattern.compile(
            "^/topic/spaces/([0-9a-fA-F-]{36})/nodes$"
    );

    private final JwtService jwtService;
    private final SpaceMemberService spaceMemberService;

    public WorkspaceWebSocketAuthInterceptor(
            JwtService jwtService,
            SpaceMemberService spaceMemberService
    ) {
        this.jwtService = jwtService;
        this.spaceMemberService = spaceMemberService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(message, accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(message, accessor);
            return message;
        }

        if (
                StompCommand.UNSUBSCRIBE.equals(command)
                || StompCommand.DISCONNECT.equals(command)
        ) {
            return message;
        }

        throw new MessageDeliveryException(
                message,
                "Workspace event socket is subscribe-only"
        );
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException(
                    message,
                    "Missing WebSocket Authorization Bearer token"
            );
        }

        String token = authorization.substring("Bearer ".length()).trim();
        try {
            UUID userId = jwtService.verify(token);
            Principal principal = new UsernamePasswordAuthenticationToken(
                    userId.toString(),
                    token,
                    List.of()
            );
            accessor.setUser(principal);
        } catch (RuntimeException error) {
            throw new MessageDeliveryException(
                    message,
                    "Invalid WebSocket Authorization Bearer token",
                    error
            );
        }
    }

    private void authorizeSubscription(
            Message<?> message,
            StompHeaderAccessor accessor
    ) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new MessageDeliveryException(
                    message,
                    "WebSocket session is not authenticated"
            );
        }

        String destination = accessor.getDestination();
        Matcher matcher = destination == null
                ? null
                : SPACE_NODE_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessageDeliveryException(
                    message,
                    "Unsupported workspace event subscription"
            );
        }

        UUID userId;
        UUID spaceId;
        try {
            userId = UUID.fromString(principal.getName());
            spaceId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException error) {
            throw new MessageDeliveryException(
                    message,
                    "Invalid workspace event subscription identity",
                    error
            );
        }

        if (spaceMemberService.getRole(userId, spaceId) == null) {
            throw new MessageDeliveryException(
                    message,
                    "Not a member of the subscribed space"
            );
        }
    }
}
