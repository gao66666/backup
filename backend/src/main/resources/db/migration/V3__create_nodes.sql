-- V3: 创建节点表（万物皆节点）
CREATE TABLE nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),                     -- 节点唯一标识
    space_id UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,   -- 所属空间ID
    parent_id UUID REFERENCES nodes(id) ON DELETE CASCADE,            -- 父节点ID，根节点此字段为 NULL
    type VARCHAR(50) NOT NULL CHECK (type IN (                        -- 节点类型
        'collection', 'doc', 'image', 'video', 'audio'
    )),
    title VARCHAR(500) NOT NULL DEFAULT 'Untitled',                   -- 节点标题
    content JSONB,                                                    -- 节点内容，按 type 存储不同结构
    properties JSONB DEFAULT '{}',                                    -- 扩展属性（is_pinned, icon, color 等）
    caption TEXT,                                                   -- 描述
    sort_order DOUBLE PRECISION DEFAULT 0,                            -- 排序权重，浮点数支持拖拽排序
    is_deleted BOOLEAN DEFAULT FALSE,                                 -- 软删除标记
    deleted_at TIMESTAMPTZ,                                           -- 删除时间
    created_by UUID NOT NULL,                                         -- 创建者用户ID
    created_at TIMESTAMPTZ DEFAULT now(),                             -- 创建时间
    updated_at TIMESTAMPTZ DEFAULT now()                              -- 更新时间
);

-- 树形结构索引：按 space_id + parent_id + sort_order 排序
CREATE INDEX idx_nodes_tree ON nodes(space_id, parent_id, sort_order) WHERE is_deleted = FALSE;

-- 类型索引：按 space_id + type 查询
CREATE INDEX idx_nodes_type ON nodes(space_id, type) WHERE is_deleted = FALSE;

-- 软删除索引：回收站查询
CREATE INDEX idx_nodes_deleted ON nodes(space_id, is_deleted, deleted_at);

-- content JSONB GIN 索引：支持全文搜索
CREATE INDEX idx_nodes_content_gin ON nodes USING GIN (content jsonb_path_ops);

-- properties 常用字段索引（is_pinned, icon, color 等）
CREATE INDEX idx_nodes_properties ON nodes USING GIN (properties);