-- MedKernel v1.0 GA · GA-ENG-BASE-04 · 审计哈希链基线（H2 MODE=PostgreSQL）
-- 在 V2 已建好的 audit_event 上新增哈希链前驱列；新增 audit_chain_head 作为每租户链头锁锚点。

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS prev_event_id  VARCHAR(64)  NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS prev_signature VARCHAR(512) NULL;

CREATE TABLE IF NOT EXISTS audit_chain_head (
    tenant_id      VARCHAR(64)  PRIMARY KEY,
    last_event_id  VARCHAR(64)  NULL,
    last_signature VARCHAR(512) NOT NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit_chain_head IS '每租户审计哈希链链头';
