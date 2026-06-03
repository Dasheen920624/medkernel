-- MedKernel v1.0 GA · API-11 嵌入 API 契约补强（Oracle）
-- ROLLBACK: 若需回退，先删除 idx_embed_token_status_expired / idx_embed_token_hook，再删除 integration_mode、hook、hook_instance、consumed_at 字段；已消费令牌须按审计证据保留。

ALTER TABLE embed_launch_token ADD integration_mode VARCHAR2(16) DEFAULT 'IFRAME' NOT NULL;
ALTER TABLE embed_launch_token ADD hook VARCHAR2(64) NULL;
ALTER TABLE embed_launch_token ADD hook_instance VARCHAR2(128) NULL;
ALTER TABLE embed_launch_token ADD consumed_at TIMESTAMP NULL;

CREATE INDEX idx_embed_token_status_expired
    ON embed_launch_token (tenant_id, status, expired_at);

CREATE INDEX idx_embed_token_hook
    ON embed_launch_token (tenant_id, hook, hook_instance);

COMMENT ON COLUMN embed_launch_token.integration_mode IS '嵌入集成方式(IFRAME,SDK,API)';
COMMENT ON COLUMN embed_launch_token.hook IS 'CDS Hooks风格触发点编码';
COMMENT ON COLUMN embed_launch_token.hook_instance IS 'CDS Hooks触发实例ID';
COMMENT ON COLUMN embed_launch_token.consumed_at IS '令牌一次性消费时间';
COMMENT ON COLUMN embed_launch_token.status IS '状态(UNUSED,USED,EXPIRED,REVOKED)';
