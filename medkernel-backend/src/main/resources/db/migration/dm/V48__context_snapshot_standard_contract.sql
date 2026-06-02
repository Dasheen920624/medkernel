-- MedKernel v1.0 GA · API-01 标准上下文契约补强（达梦）
-- 为快照头补齐 request_id / org_path / package_version，支撑幂等、组织审计与包版本快照读回。

ALTER TABLE context_snapshot ADD request_id VARCHAR2(128) NULL;
ALTER TABLE context_snapshot ADD org_path VARCHAR2(512) NULL;
ALTER TABLE context_snapshot ADD package_version VARCHAR2(64) NULL;

CREATE INDEX idx_context_snapshot_tenant_request
    ON context_snapshot (tenant_id, request_id);
CREATE INDEX idx_context_snapshot_org_path
    ON context_snapshot (tenant_id, org_path, created_at);
CREATE INDEX idx_context_snapshot_package_version
    ON context_snapshot (tenant_id, package_version, created_at);
