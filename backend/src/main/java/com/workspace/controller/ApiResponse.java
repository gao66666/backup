package com.workspace.controller;

import java.util.Map;
import java.util.UUID;

/**
 * 统一 API 响应格式: { data: ..., requestId: ... }
 *
 * 所有 controller 的成功响应都走这个包装,前端按 json.data 拿数据。
 * 失败响应(notFound / 403 / 500)不强制走这个,保持 Spring Boot 默认。
 */
public final class ApiResponse {

    private ApiResponse() {}

    public static Map<String, Object> ok(Object data) {
        return Map.of(
            "data", data,
            "requestId", "mock-" + UUID.randomUUID().toString()
        );
    }
}