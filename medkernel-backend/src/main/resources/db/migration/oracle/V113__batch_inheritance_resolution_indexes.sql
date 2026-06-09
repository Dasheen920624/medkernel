-- 批量继承解析索引：按租户一次读取资产身份集合与组织闭包覆盖集合。

CREATE INDEX idx_av_batch_identity
    ON mk_version_asset_version (tenant_id, asset_identity, status, asset_type);

CREATE INDEX idx_io_batch_scope
    ON mk_version_inheritance_override (
        tenant_id, org_path, lifecycle_status, asset_identity
    );
