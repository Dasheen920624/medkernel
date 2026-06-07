-- MedKernel v1.0 GA · API-01 标准上下文契约补强（H2）
-- 为快照头补齐 request_id / org_path，支撑幂等与组织审计；统一 package_version 已由基线定义。

ALTER TABLE context_snapshot ADD COLUMN IF NOT EXISTS request_id VARCHAR(128) NULL;
ALTER TABLE context_snapshot ADD COLUMN IF NOT EXISTS org_path VARCHAR(512) NULL;

CREATE INDEX IF NOT EXISTS idx_context_snapshot_tenant_request
    ON context_snapshot (tenant_id, request_id);
CREATE INDEX IF NOT EXISTS idx_context_snapshot_org_path
    ON context_snapshot (tenant_id, org_path, created_at);
CREATE INDEX IF NOT EXISTS idx_context_snapshot_package_version
    ON context_snapshot (tenant_id, package_version, created_at);

COMMENT ON COLUMN context_snapshot.request_id IS '请求级幂等 ID，对齐 API-01 request_id';
COMMENT ON COLUMN context_snapshot.org_path IS '请求组织路径快照，用于组织作用域审计';
COMMENT ON COLUMN context_snapshot.package_version IS '请求绑定的统一配置包版本快照';
