package com.workspace.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 从请求 header 获取当前用户 ID。
 * 正式环境应替换为 JWT 解析。
 * Header: X-User-Id
 */
@Component
public class AuthService {

    private static final String USER_ID_HEADER = "X-User-Id";

    public UUID getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No request context");
        }
        HttpServletRequest request = attrs.getRequest();
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new IllegalStateException("Missing X-User-Id header");
        }
        return UUID.fromString(userIdStr);
    }

    public UUID getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
