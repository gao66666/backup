package com.workspace.controller;

import com.workspace.service.ForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * 全局异常处理。
 *
 * - {@link ForbiddenException} → 403
 * - 其他异常暂不接管,留给 Spring 默认处理(500)
 *
 * 之前在 NodeController / SpaceController / SpaceMemberController 各有一个
 * 重复的 @ExceptionHandler(ForbiddenException),集中到这里。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }
}