package com.workspace.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

/**
 * nodes 表 Repository（jOOQ DSL）。
 */
@Repository
public class NodeRepository {

    private final DSLContext db;

    public NodeRepository(DSLContext db) {
        this.db = db;
    }

    public Record insert(UUID spaceId, UUID parentId, String type, String title,
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

        return findById(id).orElseThrow();
    }

    public Optional<Record> findById(UUID id) {
        return db.selectFrom(table("nodes"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional();
    }

    public java.util.List<Record> findAll() {
        return db.selectFrom(table("nodes"))
                .where(field("is_deleted").eq(false))
                .orderBy(field("sort_order").asc())
                .fetch();
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

    public java.util.List<Record> findBySpaceIdAndParentId(UUID spaceId, UUID parentId) {
        String sql;
        if (parentId == null) {
            sql = """
                SELECT n.*, EXISTS(SELECT 1 FROM nodes n2 WHERE n2.parent_id = n.id) AS has_children
                FROM nodes n
                WHERE n.space_id = ? AND n.parent_id IS NULL AND n.is_deleted = false
                ORDER BY n.sort_order ASC
                """;
            return db.fetch(sql, spaceId).into(Record.class);
        } else {
            sql = """
                SELECT n.*, EXISTS(SELECT 1 FROM nodes n2 WHERE n2.parent_id = n.id) AS has_children
                FROM nodes n
                WHERE n.space_id = ? AND n.parent_id = ? AND n.is_deleted = false
                ORDER BY n.sort_order ASC
                """;
            return db.fetch(sql, spaceId, parentId).into(Record.class);
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
}
