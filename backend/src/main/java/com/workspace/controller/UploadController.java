package com.workspace.controller;

import com.workspace.service.StorageService;
import com.workspace.util.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 简化版上传端点(不分块)。
 *
 * POST /api/uploads  multipart/form-data  file=<file>
 *   → 200 { data: { url, key, size, contentType } }
 *
 * 鉴权:只要求已登录(从 JWT 拿 userId),不要求某个 space 的成员。
 * (因为上传后业务可以决定挂到哪个节点/空间,跟上传本身解耦)
 *
 * ⚠️ 真合并 Monsora 时按分块上传协议重写。
 * ⚠️ MinIO bucket 必须是 public-read 才能直接用返回的 URL;否则需要 presigned URL。
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final StorageService storage;
    private final AuthService authService;

    public UploadController(StorageService storage, AuthService authService) {
        this.storage = storage;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        // 只要已登录就行(JwtAuthFilter 没塞 role 就抛 IllegalStateException,这里只是触发一下)
        authService.getCurrentUserId();
        try {
            Map<String, Object> result = storage.upload(file);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "upload failed: " + e.getMessage()));
        }
    }
}