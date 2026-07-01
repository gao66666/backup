package com.workspace.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * audit_logs 表 Repository（jOOQ DSL）。
 * 只允许 INSERT，禁止 UPDATE/DELETE。
 */
@Repository
public class AuditLogRepository {

    private final DSLContext db;

    public AuditLogRepository(DSLContext db) {
        this.db = db;
    }

    public int insert(UUID actorId, String action, String targetType, UUID targetId, String details) {
        String sql = """
            INSERT INTO audit_logs (actor_id, action, target_type, target_id, details, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?)
            """;
        return db.execute(sql, actorId, action, targetType, targetId,
                details != null ? details : "{}", OffsetDateTime.now());
    }

    public int insertByActorId(UUID actorId, String action, String targetType, UUID targetId, String details) {
        return insert(actorId, action, targetType, targetId, details);
    }
}
