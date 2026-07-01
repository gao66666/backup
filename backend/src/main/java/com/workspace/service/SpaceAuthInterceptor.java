package com.workspace.service;

import com.workspace.util.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由组 B 的拦截器:
 *   1. 读 X-User-Id 拿当前 userId(认证,Monsora 网关已前置做了真鉴权)
 *   2. 从请求里解析出当前操作的 spaceId(从 query 或 path)
 *   3. 查 DB 拿该用户在该空间的角色
 *   4. 把 (userId, spaceId, role) 塞 RoleContext
 *   5. 不是成员 → 403
 *
 * ⚠️ 当前实现假定一请求一空间,且该空间必须能从 URL 解析出。
 */
@Component
public class SpaceAuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final SpaceMemberService spaceMemberService;

    public SpaceAuthInterceptor(AuthService authService, SpaceMemberService spaceMemberService) {
        this.authService = authService;
        this.spaceMemberService = spaceMemberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        try {
            // 1. 取 userId(Monsora 网关已鉴权,这里只读)
            UUID userId;
            try {
                userId = authService.getCurrentUserId();
            } catch (Exception e) {
                writeError(res, HttpStatus.UNAUTHORIZED, "Missing X-User-Id header");
                return false;
            }

            // 2. 解析当前操作的 spaceId
            UUID spaceId;
            try {
                spaceId = extractSpaceId(req);
            } catch (Exception e) {
                writeError(res, HttpStatus.BAD_REQUEST,
                    "Cannot extract spaceId from request: " + e.getMessage());
                return false;
            }

            // 3. 查角色
            String roleStr = spaceMemberService.getRole(userId, spaceId);
            if (roleStr == null) {
                writeError(res, HttpStatus.FORBIDDEN, "Not a member of this space");
                return false;
            }
            Role role = Role.from(roleStr);

            // 4. 写入 ThreadLocal
            RoleContext.set(userId, spaceId, role);
            return true;
        } catch (ForbiddenException e) {
            writeError(res, HttpStatus.FORBIDDEN, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        // 防止线程复用导致 ThreadLocal 泄露
        RoleContext.clear();
    }

    // ---- helpers ----

    /**
     * 从 query (?spaceId=xxx) 或 path (/api/spaces/{id}) 取 spaceId。
     * 注意:路由组 B 的所有端点必须保证能解析出 spaceId,否则 400。
     */
    static UUID extractSpaceId(HttpServletRequest req) {
        // 优先 query
        String q = req.getParameter("spaceId");
        if (q != null && !q.isBlank()) {
            return UUID.fromString(q);
        }
        // 再看 path:/api/spaces/{id}(单空间操作)
        String path = req.getRequestURI();
        Matcher m = SPACE_PATH_PATTERN.matcher(path);
        if (m.find()) {
            return UUID.fromString(m.group(1));
        }
        throw new IllegalArgumentException(
            "URL " + path + " has no spaceId in query and doesn't match /api/spaces/{id}");
    }

    // /api/spaces/{id} 这种"单空间操作"路径(/api/spaces 自己没 {id} 不匹配)
    private static final Pattern SPACE_PATH_PATTERN = Pattern.compile("^/api/spaces/([^/]+)$");

    private static void writeError(HttpServletResponse res, HttpStatus status, String msg) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}");
    }
}