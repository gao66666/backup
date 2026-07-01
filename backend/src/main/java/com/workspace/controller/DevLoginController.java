package com.workspace.controller;

import com.workspace.util.JwtService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 开发期专用:签发 JWT。
 *
 * ⚠️ 只在 dev profile 启用(@Profile("dev")),生产环境这个 controller 不存在。
 * 真合并 Monsora 时,这个文件应该删掉,用 Monsora 自己的登录端点。
 *
 * GET /api/dev/login?userId=xxx  → { token: "..." }
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevLoginController {

    private final JwtService jwtService;

    public DevLoginController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/login")
    public ResponseEntity<?> login(@RequestParam String userId) {
        UUID uid;
        try {
            uid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId must be a UUID"));
        }
        String token = jwtService.issue(uid);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "token", token,
            "userId", uid.toString()
        )));
    }
}