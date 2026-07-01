package com.workspace.service;

import java.util.UUID;

/**
 * 当前请求的"鉴权上下文",用 ThreadLocal 存。
 *
 * 由 {@link SpaceAuthInterceptor} 在请求进来时写入,由业务代码读。
 * 业务代码用 {@link #requireAtLeast(Role)} 做细粒度权限校验。
 *
 * ⚠️ 仅在路由组 B(需要空间鉴权的端点)有效。路由组 A 不要访问。
 * ⚠️ 不支持一请求多空间 —— 一旦拿到就是当前请求唯一操作的 space。
 */
public final class RoleContext {

    public record Current(UUID userId, UUID spaceId, Role role) {}

    private static final ThreadLocal<Current> CURRENT = new ThreadLocal<>();

    private RoleContext() {}

    /** 拦截器调用:写入当前请求的鉴权信息。 */
    public static void set(UUID userId, UUID spaceId, Role role) {
        CURRENT.set(new Current(userId, spaceId, role));
    }

    /** 业务/Controller:读取当前请求的鉴权信息。 */
    public static Current current() {
        Current c = CURRENT.get();
        if (c == null) {
            throw new IllegalStateException(
                "No role context bound. " +
                "Are you calling from a route not protected by SpaceAuthInterceptor?");
        }
        return c;
    }

    /**
     * 业务代码做细粒度校验。当前角色达不到 min 就抛 ForbiddenException。
     * 当前上下文为空(没绑)同样抛 ForbiddenException。
     */
    public static void requireAtLeast(Role min) {
        Current c = CURRENT.get();
        if (c == null || !c.role().atLeast(min)) {
            throw new ForbiddenException(
                "Insufficient permissions: need " + min + ", have "
                    + (c == null ? "none" : c.role()));
        }
    }

    /** 拦截器在请求结束时调用,清 ThreadLocal(防止线程复用泄露)。 */
    public static void clear() {
        CURRENT.remove();
    }
}