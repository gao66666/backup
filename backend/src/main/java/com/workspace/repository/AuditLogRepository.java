package com.workspace.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

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

    public int insert(UUID spaceId, UUID actorId, String action, String targetType, UUID targetId, String details) {
        String sql = """
            INSERT INTO audit_logs (space_id, actor_id, action, target_type, target_id, details, created_at)
            VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?::jsonb, ?::timestamptz)
            """;
        return db.execute(sql, spaceId, actorId, action, targetType, targetId,
                details != null ? details : "{}", OffsetDateTime.now());
    }

    // ── 查询 ──

    /**
     * 按空间查活动日志，按 created_at DESC 排列。
     */
    public List<Map<String, Object>> findBySpace(UUID spaceId, int limit, int offset) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE space_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        return db.fetch(sql, spaceId, limit, offset).stream()
                .map(this::recordToMap)
                .toList();
    }

    public int countBySpace(UUID spaceId) {
        return db.fetchOne("SELECT count(*) FROM audit_logs WHERE space_id = ?::uuid", spaceId)
                .get(0, int.class);
    }

    /**
     * 按节点查历史（target_type='node' AND target_id=?）
     */
    public List<Map<String, Object>> findByNode(UUID nodeId, int limit, int offset) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE target_type = 'node' AND target_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        return db.fetch(sql, nodeId, limit, offset).stream()
                .map(this::recordToMap)
                .toList();
    }

    public int countByNode(UUID nodeId) {
        return db.fetchOne(
                "SELECT count(*) FROM audit_logs WHERE target_type = 'node' AND target_id = ?::uuid",
                nodeId).get(0, int.class);
    }

    /**
     * 按用户查（actor_id=?），可选 space 过滤。
     */
    public List<Map<String, Object>> findByUser(UUID userId, UUID spaceId, int limit, int offset) {
        if (spaceId != null) {
            String sql = """
                SELECT * FROM audit_logs
                WHERE actor_id = ?::uuid AND space_id = ?::uuid
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
            return db.fetch(sql, userId, spaceId, limit, offset).stream()
                    .map(this::recordToMap)
                    .toList();
        } else {
            String sql = """
                SELECT * FROM audit_logs
                WHERE actor_id = ?::uuid
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
            return db.fetch(sql, userId, limit, offset).stream()
                    .map(this::recordToMap)
                    .toList();
        }
    }

    public int countByUser(UUID userId, UUID spaceId) {
        if (spaceId != null) {
            return db.fetchOne(
                    "SELECT count(*) FROM audit_logs WHERE actor_id = ?::uuid AND space_id = ?::uuid",
                    userId, spaceId).get(0, int.class);
        } else {
            return db.fetchOne(
                    "SELECT count(*) FROM audit_logs WHERE actor_id = ?::uuid",
                    userId).get(0, int.class);
        }
    }

    private Map<String, Object> recordToMap(org.jooq.Record record) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (var field : record.fields()) {
            Object value = record.getValue(field);
            if (value != null && value.getClass().getSimpleName().contains("JSON")) {
                value = value.toString();
            }
            map.put(field.getName(), value);
        }
        return map;
    }
}
