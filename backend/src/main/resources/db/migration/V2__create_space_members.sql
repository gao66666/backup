-- V2: 创建空间成员表
CREATE TABLE space_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),                     -- 成员记录唯一标识
    space_id UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,   -- 所属空间ID
    user_id UUID NOT NULL,                                            -- 用户ID
    role VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'admin', 'editor', 'viewer')), -- 角色权限
    joined_at TIMESTAMPTZ DEFAULT now(),                              -- 加入时间
    invited_by UUID,                                                  -- 邀请人用户ID
    UNIQUE (space_id, user_id)                                        -- 同一空间内用户唯一
);

CREATE INDEX idx_members_user ON space_members(user_id);
CREATE INDEX idx_members_space_role ON space_members(space_id, role);