package com.workspace.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * yjs_documents 表 Repository。
 *
 * 每个 doc 节点最多对应一行完整 Yjs 二进制状态。所有写入通过单条 UPSERT
 * 完成，避免先查后写带来的并发竞争。
 */
@Repository
public class YjsDocumentRepository {

    public record LoadedDocument(
            byte[] state,
            Short schemaVersion,
            Long revision,
            Integer byteSize,
            OffsetDateTime updatedAt
    ) {}

    public record StoredDocument(
            long revision,
            int byteSize,
            OffsetDateTime updatedAt
    ) {}

    private final DSLContext db;

    public YjsDocumentRepository(DSLContext db) {
        this.db = db;
    }

    /**
     * 节点存在但尚无协作状态时返回 state=null；节点不存在、不属于空间、已删除
     * 或不是 doc 类型时返回 null。
     */
    public LoadedDocument find(UUID spaceId, UUID nodeId) {
        Record record = db.fetchOne("""
            SELECT
                yd.ydoc_state,
                yd.schema_version,
                yd.revision,
                yd.byte_size,
                yd.updated_at
            FROM nodes AS n
            LEFT JOIN yjs_documents AS yd ON yd.node_id = n.id
            WHERE n.id = ?::uuid
              AND n.space_id = ?::uuid
              AND n.type = 'doc'
              AND n.is_deleted = false
            """, nodeId, spaceId);

        if (record == null) {
            return null;
        }

        return new LoadedDocument(
                record.get("ydoc_state", byte[].class),
                record.get("schema_version", Short.class),
                record.get("revision", Long.class),
                record.get("byte_size", Integer.class),
                record.get("updated_at", OffsetDateTime.class)
        );
    }

    /**
     * 仅当目标节点属于指定空间、未删除且类型为 doc 时写入。
     */
    public StoredDocument upsert(UUID spaceId, UUID nodeId, byte[] state, short schemaVersion) {
        Record record = db.fetchOne("""
            INSERT INTO yjs_documents (
                node_id,
                ydoc_state,
                schema_version
            )
            SELECT
                n.id,
                ?::bytea,
                ?::smallint
            FROM nodes AS n
            WHERE n.id = ?::uuid
              AND n.space_id = ?::uuid
              AND n.type = 'doc'
              AND n.is_deleted = false
            ON CONFLICT (node_id)
            DO UPDATE SET
                ydoc_state = EXCLUDED.ydoc_state,
                schema_version = EXCLUDED.schema_version,
                revision = yjs_documents.revision + 1,
                updated_at = now()
            RETURNING revision, byte_size, updated_at
            """, state, schemaVersion, nodeId, spaceId);

        if (record == null) {
            return null;
        }

        return new StoredDocument(
                record.get("revision", Long.class),
                record.get("byte_size", Integer.class),
                record.get("updated_at", OffsetDateTime.class)
        );
    }

    public boolean isStorageReady() {
        Record record = db.fetchOne("""
                SELECT to_regclass('public.yjs_documents') IS NOT NULL AS ready
                """);
        Boolean ready = record == null ? null : record.get("ready", Boolean.class);
        return Boolean.TRUE.equals(ready);
    }
}
