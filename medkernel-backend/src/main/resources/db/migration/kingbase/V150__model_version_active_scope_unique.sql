-- MedKernel T9.4 · 模型版本 ACTIVE 作用域唯一（KingbaseES）
-- ACTIVE 版本包必须携带租户+能力作用域键；RETIRED 必须清空，唯一约束负责并发发布裁决。
-- ROLLBACK：先删除 uk/ck 约束，再删除 active_scope_key 列。

ALTER TABLE mk_llm_model_version_bundle ADD COLUMN active_scope_key VARCHAR(192) NULL;

UPDATE mk_llm_model_version_bundle
SET active_scope_key = tenant_id || '|' || capability_code
WHERE status = 'ACTIVE';

ALTER TABLE mk_llm_model_version_bundle
    ADD CONSTRAINT ck_mk_llm_model_version_active_scope CHECK (
        (status = 'ACTIVE' AND active_scope_key IS NOT NULL
            AND active_scope_key = tenant_id || '|' || capability_code)
        OR (status = 'RETIRED' AND active_scope_key IS NULL)
    );

ALTER TABLE mk_llm_model_version_bundle
    ADD CONSTRAINT uk_mk_llm_model_version_active_scope UNIQUE (active_scope_key);

COMMENT ON COLUMN mk_llm_model_version_bundle.active_scope_key IS 'ACTIVE 模型版本包的租户与能力唯一作用域键；RETIRED 必须为空';
