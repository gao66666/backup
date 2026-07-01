package com.workspace.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 取当前请求的 userId。
 *
 * 优先从 SecurityContext(JwtAuthFilter 塞的)读;
 * 没有就 throw IllegalStateException(由 SpaceAuthInterceptor 转 401)。
 */
@Component
public class AuthService {

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user in SecurityContext");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        if (principal instanceof String s) {
            return UUID.fromString(s);
        }
        return UUID.fromString(principal.toString());
    }

    public UUID getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}