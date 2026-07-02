package com.workspace.service;

import com.workspace.repository.AuditLogRepository;
import com.workspace.util.AuthService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 审计日志服务。
 * 所有敏感操作调用 log() 记录审计日志。
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;

    public AuditService(AuditLogRepository auditLogRepository, AuthService authService) {
        this.auditLogRepository = auditLogRepository;
        this.authService = authService;
    }

    public void log(UUID spaceId, String action, String targetType, UUID targetId, String details) {
        try {
            UUID actorId = authService.getCurrentUserId();
            auditLogRepository.insert(spaceId, actorId, action, targetType, targetId, details);
        } catch (Exception e) {
            // 审计日志失败不应影响业务操作
        }
    }

    public void log(UUID spaceId, String action, String targetType, UUID targetId) {
        log(spaceId, action, targetType, targetId, null);
    }

    // ── 查询 ──

    public java.util.List<java.util.Map<String, Object>> findBySpace(UUID spaceId, int page, int size) {
        int offset = (page - 1) * size;
        return auditLogRepository.findBySpace(spaceId, size, offset);
    }

    public int countBySpace(UUID spaceId) {
        return auditLogRepository.countBySpace(spaceId);
    }

    public java.util.List<java.util.Map<String, Object>> findByNode(UUID nodeId, int page, int size) {
        int offset = (page - 1) * size;
        return auditLogRepository.findByNode(nodeId, size, offset);
    }

    public int countByNode(UUID nodeId) {
        return auditLogRepository.countByNode(nodeId);
    }

    public java.util.List<java.util.Map<String, Object>> findByUser(UUID userId, UUID spaceId, int page, int size) {
        int offset = (page - 1) * size;
        return auditLogRepository.findByUser(userId, spaceId, size, offset);
    }

    public int countByUser(UUID userId, UUID spaceId) {
        return auditLogRepository.countByUser(userId, spaceId);
    }
}
