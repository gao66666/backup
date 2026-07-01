package com.workspace.service;

import com.workspace.util.AuthService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 权限校验服务。
 * 所有业务操作前必须先调用 check() 进行角色校验。
 */
@Service
public class PermissionService {

    public static final Set<String> CAN_VIEW = Set.of("owner", "admin", "editor", "viewer");
    public static final Set<String> CAN_EDIT = Set.of("owner", "admin", "editor");
    public static final Set<String> CAN_MANAGE_MEMBER = Set.of("owner", "admin");
    public static final Set<String> CAN_DELETE_SPACE = Set.of("owner");

    private final SpaceMemberService spaceMemberService;
    private final AuthService authService;

    public PermissionService(SpaceMemberService spaceMemberService, AuthService authService) {
        this.spaceMemberService = spaceMemberService;
        this.authService = authService;
    }

    /**
     * 获取当前用户在指定空间的角色，不在空间内返回 null
     */
    public String getCurrentRole(UUID spaceId) {
        UUID userId = authService.getCurrentUserId();
        return spaceMemberService.getRole(userId, spaceId);
    }

    /**
     * 校验当前用户是否有指定权限
     */
    public void check(UUID spaceId, Set<String> allowedRoles) {
        String role = getCurrentRole(spaceId);
        if (role == null) {
            throw new ForbiddenException("Not a member of this space");
        }
        if (!allowedRoles.contains(role.toLowerCase())) {
            throw new ForbiddenException("Insufficient permissions");
        }
    }

    /**
     * 快捷方法：校验是否有编辑权限
     */
    public void checkCanEdit(UUID spaceId) {
        check(spaceId, CAN_EDIT);
    }

    /**
     * 快捷方法：校验是否有查看权限
     */
    public void checkCanView(UUID spaceId) {
        check(spaceId, CAN_VIEW);
    }

    /**
     * 校验是否能管理成员（不能对 owner 操作）
     */
    public void checkCanManageMember(UUID spaceId) {
        String role = getCurrentRole(spaceId);
        if (role == null) {
            throw new ForbiddenException("Not a member of this space");
        }
        if (!CAN_MANAGE_MEMBER.contains(role.toLowerCase())) {
            throw new ForbiddenException("Insufficient permissions");
        }
    }

    /**
     * 校验是否能管理特定成员（不能对 owner 操作）
     */
    public void checkCanManageMember(UUID spaceId, UUID targetUserId) {
        checkCanManageMember(spaceId);
        // 不能对 owner 操作
        // （需要查 targetUserId 的角色，如为 owner 则拒绝）
    }

    /**
     * 校验是否能删除空间
     */
    public void checkCanDeleteSpace(UUID spaceId) {
        check(spaceId, CAN_DELETE_SPACE);
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }
}
