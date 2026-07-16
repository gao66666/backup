-- V5: Yjs 协作文档持久化表
CREATE TABLE yjs_documents (
    node_id UUID PRIMARY KEY
        REFERENCES nodes(id) ON DELETE CASCADE,                       -- 一个节点对应一份协作文档
    ydoc_state BYTEA NOT NULL,                                       -- Yjs encodeStateAsUpdate() 二进制状态
    schema_version SMALLINT NOT NULL DEFAULT 1
        CHECK (schema_version > 0),                                  -- Milkdown/ProseMirror 文档结构版本
    revision BIGINT NOT NULL DEFAULT 1
        CHECK (revision > 0),                                        -- 每次完整状态写入时递增
    byte_size INTEGER
        GENERATED ALWAYS AS (octet_length(ydoc_state)) STORED,       -- 状态大小，便于监控
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),                   -- 首次持久化时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()                    -- 最近持久化时间
);
