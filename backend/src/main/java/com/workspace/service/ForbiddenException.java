package com.workspace.service;

/**
 * 鉴权失败异常 —— 不在成员里 / 角色不够。
 * 由 {@link SpaceAuthInterceptor} 或业务层抛出,
 * 由 Controller 的 {@code @ExceptionHandler} 统一转 403。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}