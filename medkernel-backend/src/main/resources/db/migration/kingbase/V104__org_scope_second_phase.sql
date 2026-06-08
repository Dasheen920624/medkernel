-- MedKernel v1.0 GA · P4-1.0b 组织树二期补全（Kingbase）
-- 正向迁移：将 GROUP 归一为 REGION，将 HOSPITAL/SITE 归一为 FACILITY+facility_type，
--           并启用 WARD 与 mk_org_secondary_membership，形成唯一组织层级模型。
-- ROLLBACK: 回退需先备份生产数据，删除 mk_org_secondary_membership 与 facility_type，
--           再按离线映射将 REGION/FACILITY/WARD 数据恢复为旧层级。

ALTER TABLE org_unit ADD COLUMN IF NOT EXISTS facility_type VARCHAR(32) NULL;

UPDATE org_unit
   SET facility_type = 'HOSPITAL'
 WHERE level_code = 'HOSPITAL'
   AND facility_type IS NULL;

UPDATE org_unit
   SET facility_type = 'STATION'
 WHERE level_code = 'SITE'
   AND facility_type IS NULL;

UPDATE org_unit SET level_code = 'REGION' WHERE level_code = 'GROUP';
UPDATE org_unit SET level_code = 'FACILITY' WHERE level_code IN ('HOSPITAL','SITE');

ALTER TABLE org_unit DROP CONSTRAINT ck_org_unit_level;
ALTER TABLE org_unit ADD CONSTRAINT ck_org_unit_level
    CHECK (level_code IN ('PLATFORM','TENANT','REGION','FACILITY','CAMPUS','DEPARTMENT','WARD'));

ALTER TABLE org_unit ADD CONSTRAINT ck_org_unit_facility_type
    CHECK (
        (level_code = 'FACILITY' AND facility_type IN ('HOSPITAL','COMMUNITY_HEALTH_CENTER','TOWNSHIP_CLINIC','STATION','OTHER'))
        OR (level_code <> 'FACILITY' AND facility_type IS NULL)
    );

UPDATE release_plan SET scope_type = 'REGION' WHERE scope_type = 'GROUP';
UPDATE release_plan SET scope_type = 'FACILITY' WHERE scope_type IN ('HOSPITAL','SITE');
ALTER TABLE release_plan DROP CONSTRAINT ck_release_plan_scope_type;
ALTER TABLE release_plan ADD CONSTRAINT ck_release_plan_scope_type
    CHECK (scope_type IN ('ALL','REGION','FACILITY','CAMPUS','DEPARTMENT','WARD'));

UPDATE mk_version_release_plan SET scope_type = 'REGION' WHERE scope_type = 'GROUP';
UPDATE mk_version_release_plan SET scope_type = 'FACILITY' WHERE scope_type IN ('HOSPITAL','SITE');
ALTER TABLE mk_version_release_plan DROP CONSTRAINT ck_mk_version_release_plan_scope;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_plan_scope
    CHECK (scope_type IN ('ALL','REGION','FACILITY','CAMPUS','DEPARTMENT','WARD'));

CREATE TABLE IF NOT EXISTS mk_org_secondary_membership (
    tenant_id           VARCHAR(64) NOT NULL,
    child_id            VARCHAR(26) NOT NULL,
    secondary_parent_id VARCHAR(26) NOT NULL,
    relation_code       VARCHAR(64) NOT NULL DEFAULT 'MATRIX',
    priority            INTEGER NOT NULL DEFAULT 100,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    CONSTRAINT pk_mk_org_secondary_membership PRIMARY KEY (tenant_id, child_id, secondary_parent_id),
    CONSTRAINT fk_mk_org_secondary_child FOREIGN KEY (child_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_mk_org_secondary_parent FOREIGN KEY (secondary_parent_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT ck_mk_org_secondary_not_self CHECK (child_id <> secondary_parent_id),
    CONSTRAINT ck_mk_org_secondary_priority CHECK (priority >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mk_org_secondary_child ON mk_org_secondary_membership (tenant_id, child_id, priority);
CREATE INDEX IF NOT EXISTS idx_mk_org_secondary_parent ON mk_org_secondary_membership (tenant_id, secondary_parent_id);

COMMENT ON COLUMN org_unit.level_code IS '组织层级：平台权威层、租户根、区域/联合体、机构、院区、科室、病区；专病通过 specialty_id 横切表达';
COMMENT ON COLUMN org_unit.facility_type IS '机构类型：医院、社区卫生服务中心、乡镇卫生院、服务站或其他；仅 FACILITY 层填写';
COMMENT ON COLUMN release_plan.scope_type IS '发布组织作用范围类型：ALL 全量、REGION 区域、FACILITY 机构、CAMPUS 院区、DEPARTMENT 科室、WARD 病区；专病由 specialty_id 横切表达';
COMMENT ON COLUMN mk_version_release_plan.scope_type IS '版本发布组织范围类型：ALL 全量、REGION 区域、FACILITY 机构、CAMPUS 院区、DEPARTMENT 科室、WARD 病区';
COMMENT ON TABLE mk_org_secondary_membership IS '组织次级归属边：表达矩阵归属，不改变主父链和 org_path';
COMMENT ON COLUMN mk_org_secondary_membership.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_org_secondary_membership.child_id IS '子组织节点 ID';
COMMENT ON COLUMN mk_org_secondary_membership.secondary_parent_id IS '次级父组织节点 ID';
COMMENT ON COLUMN mk_org_secondary_membership.relation_code IS '次级归属关系编码，如 SPECIALTY_CENTER / MATRIX';
COMMENT ON COLUMN mk_org_secondary_membership.priority IS '同一子节点多条次级归属的稳定排序优先级，数值越小越优先';
COMMENT ON COLUMN mk_org_secondary_membership.created_at IS '创建时间';
COMMENT ON COLUMN mk_org_secondary_membership.created_by IS '创建人';
