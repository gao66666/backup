package com.workspace.service;

/**
 * 空间角色。
 *
 * 角色从高到低:OWNER > ADMIN > EDITOR > VIEWER
 * 用 {@link #level()} 数值比较:level 越高权限越大。
 *
 * ⚠️ 数据库里仍是字符串(小写),用 {@link #from(String)} 解析。
 */
public enum Role {
    OWNER(4),
    ADMIN(3),
    EDITOR(2),
    VIEWER(1);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * 判断本角色是否至少达到 min 角色。
     */
    public boolean atLeast(Role min) {
        return this.level >= min.level();
    }

    /**
     * 数据库字符串 → enum。未知值抛 IllegalArgumentException。
     */
    public static Role from(String s) {
        if (s == null) {
            throw new IllegalArgumentException("role is null");
        }
        return Role.valueOf(s.toUpperCase());
    }
}