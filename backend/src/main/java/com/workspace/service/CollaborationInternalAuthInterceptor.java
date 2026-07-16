package com.workspace.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 保护 Hocuspocus 与 Spring Boot 之间的内部持久化接口。
 */
@Component
public class CollaborationInternalAuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Collaboration-Token";

    private final byte[] expectedTokenHash;

    public CollaborationInternalAuthInterceptor(
            @Value("${collaboration.internal-token}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "collaboration.internal-token must not be blank");
        }
        this.expectedTokenHash = hash(internalToken);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        String suppliedToken = request.getHeader(TOKEN_HEADER);
        boolean authorized = suppliedToken != null
                && MessageDigest.isEqual(expectedTokenHash, hash(suppliedToken));

        if (authorized) {
            return true;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Invalid collaboration service token\"}");
        return false;
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
