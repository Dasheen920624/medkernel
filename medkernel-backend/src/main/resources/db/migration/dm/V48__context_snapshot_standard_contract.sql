-- MedKernel v1.0 GA · API-01 标准上下文契约补强（达梦）
-- 为快照头补齐 request_id / org_path，支撑幂等与组织审计；统一 package_version 已由基线定义。

ALTER TABLE context_snapshot ADD request_id VARCHAR2(128) NULL;
ALTER TABLE context_snapshot ADD org_path VARCHAR2(512) NULL;

CREATE INDEX idx_context_snapshot_tenant_request
    ON context_snapshot (tenant_id, request_id);
CREATE INDEX idx_context_snapshot_org_path
    ON context_snapshot (tenant_id, org_path, created_at);
CREATE INDEX idx_context_snapshot_package_version
    ON context_snapshot (tenant_id, package_version, created_at);
