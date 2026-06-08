-- MedKernel v1.0 GA · 平台/租户发布权限分离与覆盖评审状态（Oracle）
-- ROLLBACK：确认无覆盖治理依赖后，移除 lifecycle_status 列与新增权限目录记录。

ALTER TABLE mk_version_inheritance_override ADD (
    lifecycle_status VARCHAR2(32) DEFAULT 'PUBLISHED' NOT NULL
);

ALTER TABLE mk_version_inheritance_override
    ADD CONSTRAINT ck_mk_version_inheritance_override_lifecycle
    CHECK (lifecycle_status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','DEPRECATED','RETIRED'));

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('platform.publish', 'ACTION', 'platform', '发布 / 激活平台权威资产版本', 'HIGH', 'migration-v106', 'migration-v106');

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('tenant.override', 'ACTION', 'tenant', '发布租户 / 机构资产覆盖', 'HIGH', 'migration-v106', 'migration-v106');

COMMENT ON COLUMN mk_version_inheritance_override.lifecycle_status IS '覆盖生命周期：DRAFT 草稿 / IN_REVIEW 待评审 / APPROVED 已通过 / PUBLISHED 已发布 / DEPRECATED 已弃用 / RETIRED 已退役；仅 PUBLISHED 参与解析';
