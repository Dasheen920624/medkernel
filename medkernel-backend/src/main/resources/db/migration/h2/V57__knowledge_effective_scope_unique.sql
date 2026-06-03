-- MedKernel v1.0 GA · SYS-08 知识完整适用域唯一约束（H2）
-- ROLLBACK：确认无 SYS-08 知识版本适用域依赖后，删除 uk_knowledge_asset_version_active_scope、idx_knowledge_av_effective_scope 和三列适用域字段。

ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS organization_scope VARCHAR(256) NULL;
ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS applicable_scope VARCHAR(256) NULL;
ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS active_scope_key VARCHAR(768) NULL;

UPDATE knowledge_asset_version
SET organization_scope = 'tenant:' || tenant_id
WHERE organization_scope IS NULL;

UPDATE knowledge_asset_version
SET applicable_scope = 'ALL'
WHERE applicable_scope IS NULL;

UPDATE knowledge_asset_version
SET active_scope_key = CASE
    WHEN status = 'ACTIVE' THEN CAST(identity_id AS VARCHAR) || '|' || organization_scope || '|' || applicable_scope
    ELSE 'version:' || CAST(id AS VARCHAR)
END
WHERE active_scope_key IS NULL;

ALTER TABLE knowledge_asset_version ALTER COLUMN organization_scope SET NOT NULL;
ALTER TABLE knowledge_asset_version ALTER COLUMN applicable_scope SET NOT NULL;
ALTER TABLE knowledge_asset_version ALTER COLUMN active_scope_key SET NOT NULL;

ALTER TABLE knowledge_asset_version
    ADD CONSTRAINT uk_knowledge_asset_version_active_scope UNIQUE (tenant_id, active_scope_key);

CREATE INDEX IF NOT EXISTS idx_knowledge_av_effective_scope
    ON knowledge_asset_version (tenant_id, identity_id, organization_scope, applicable_scope, status);

COMMENT ON COLUMN knowledge_asset_version.organization_scope IS '知识版本组织适用域，参与 SYS-08 同一适用域唯一权威判定';
COMMENT ON COLUMN knowledge_asset_version.applicable_scope IS '知识版本适用人群或临床上下文范围，缺省 ALL，参与同一适用域唯一权威判定';
COMMENT ON COLUMN knowledge_asset_version.active_scope_key IS 'ACTIVE 时为知识身份、组织域、适用域拼接键；非 ACTIVE 使用 version:<id> 保持唯一';
