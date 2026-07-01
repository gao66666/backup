package com.workspace.repository;

import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

/**
 * nodes 表 Repository（jOOQ DSL）。
 *
 * 所有读方法返回 Map<String, Object>（不是 Record），因为:
 *   - jOOQ 的 .into(Record.class) 在某些版本不支持,会抛 MappingException
 *   - JSONB 字段在转 Map 时要 .toString(),否则 Jackson 序列化失败
 */
@Repository
public class NodeRepository {

    private final DSLContext db;

    public NodeRepository(DSLContext db) {
        this.db = db;
    }

    public Map<String, Object> insert(UUID spaceId, UUID parentId, String type, String title,
                                     String content, String properties, String caption,
                                     Double sortOrder, UUID createdBy) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        db.insertInto(table("nodes"),
                        field("id", UUID.class),
                        field("space_id", UUID.class),
                        field("parent_id", UUID.class),
                        field("type", String.class),
                        field("title", String.class),
                        field("content", SQLDataType.JSONB.nullable()),
                        field("properties", SQLDataType.JSONB.nullable()),
                        field("caption", String.class),
                        field("sort_order", Double.class),
                        field("is_deleted", Boolean.class),
                        field("created_by", UUID.class),
                        field("created_at", OffsetDateTime.class),
                        field("updated_at", OffsetDateTime.class))
                .values(id, spaceId, parentId, type, title,
                        cast(content != null ? content : "{}", SQLDataType.JSONB),
                        cast(properties != null ? properties : "{}", SQLDataType.JSONB),
                        caption, sortOrder != null ? sortOrder : 0.0,
                        false, createdBy, now, now)
                .execute();

        return findById(id);
    }

    public Map<String, Object> findById(UUID id) {
        return db.selectFrom(table("nodes"))
                .where(field("id", UUID.class).eq(id))
                .fetchOne(r -> recordToMap(r));
    }

    /**
     * 按 id 查节点,带现算的 has_children。
     * 用于 MOVE/DELETE 后让前端拿到父节点最新状态。
     */
    public Map<String, Object> findByIdWithHasChildren(UUID id) {
        String sql = """
            SELECT n.*, EXISTS(SELECT 1 FROM nodes n2 WHERE n2.parent_id = n.id AND n2.is_deleted = false) AS has_children
            FROM nodes n
            WHERE n.id = ?
            """;
        return db.fetchOne(sql, id) == null ? null : recordToMap(db.fetchOne(sql, id));
    }

    public List<Map<String, Object>> findAll() {
        return db.selectFrom(table("nodes"))
                .where(field("is_deleted").eq(false))
                .orderBy(field("sort_order").asc())
                .fetch(r -> recordToMap(r));
    }

    public int update(UUID id, String title, String content, String properties,
                      String caption, Double sortOrder) {
        return db.update(table("nodes"))
                .set(field("title", String.class), title)
                .set(field("content", SQLDataType.JSONB), content != null ? cast(content, SQLDataType.JSONB) : null)
                .set(field("properties", SQLDataType.JSONB), properties != null ? cast(properties, SQLDataType.JSONB) : null)
                .set(field("caption", String.class), caption)
                .set(field("sort_order", Double.class), sortOrder)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public int deleteById(UUID id) {
        return db.update(table("nodes"))
                .set(field("is_deleted", Boolean.class), true)
                .set(field("deleted_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public List<Map<String, Object>> findBySpaceIdAndParentId(UUID spaceId, UUID parentId) {
        String sql;
        if (parentId == null) {
            sql = """
                SELECT n.*, EXISTS(SELECT 1 FROM nodes n2 WHERE n2.parent_id = n.id) AS has_children
                FROM nodes n
                WHERE n.space_id = ? AND n.parent_id IS NULL AND n.is_deleted = false
                ORDER BY n.sort_order ASC
                """;
            return db.fetch(sql, spaceId).stream()
                    .map(this::recordToMap)
                    .collect(Collectors.toList());
        } else {
            sql = """
                SELECT n.*, EXISTS(SELECT 1 FROM nodes n2 WHERE n2.parent_id = n.id) AS has_children
                FROM nodes n
                WHERE n.space_id = ? AND n.parent_id = ? AND n.is_deleted = false
                ORDER BY n.sort_order ASC
                """;
            return db.fetch(sql, spaceId, parentId).stream()
                    .map(this::recordToMap)
                    .collect(Collectors.toList());
        }
    }

    public int updateParentAndSort(UUID id, UUID newParentId, Double sortOrder) {
        return db.update(table("nodes"))
                .set(field("parent_id", UUID.class), newParentId)
                .set(field("sort_order", Double.class), sortOrder)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    /**
     * 把 jOOQ Record 转 Map<String, Object>。
     * JSONB 字段(类型名含 "JSON")转 String,否则 Jackson 序列化失败。
     */
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