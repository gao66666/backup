-- V1: 创建工作空间表
CREATE TABLE spaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),                     -- 空间唯一标识
    name VARCHAR(255) NOT NULL,                                        -- 空间名称
    owner_id UUID NOT NULL,                                           -- 所有者用户ID
    root_node_id UUID NOT NULL UNIQUE,                                -- 空间根节点ID，创建空间时同步生成
    created_at TIMESTAMPTZ DEFAULT now()                              -- 创建时间
);