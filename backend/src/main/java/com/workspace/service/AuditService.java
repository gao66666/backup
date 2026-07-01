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

    public void log(String action, String targetType, UUID targetId, String details) {
        try {
            UUID actorId = authService.getCurrentUserId();
            auditLogRepository.insert(actorId, action, targetType, targetId, details);
        } catch (Exception e) {
            // 审计日志失败不应影响业务操作
        }
    }

    public void log(String action, String targetType, UUID targetId) {
        log(action, targetType, targetId, null);
    }
}
