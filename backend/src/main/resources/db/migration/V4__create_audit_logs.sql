-- V4: 审计日志表
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    space_id UUID,                                                    -- 所属空间(按空间过滤)
    actor_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    details JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_audit_space ON audit_logs(space_id, created_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id, created_at);
CREATE INDEX idx_audit_target ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_action ON audit_logs(action, created_at);
