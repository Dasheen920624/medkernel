-- MedKernel v1.0 GA · P0-1.3 平台权威层组织层级（PostgreSQL）
-- 放宽 org_unit.level_code 校验以容纳 PLATFORM 平台权威层（高于所有租户的只读权威源，设计附录 O / 附录 G·D1）；
-- 平台空间约定 __platform__ 租户的顶层组织节点据此可落库，继承解析前置平台基线。
-- ROLLBACK：确认无 PLATFORM 层 org_unit 记录后，删除 ck_org_unit_level 并恢复为 TENANT/GROUP/HOSPITAL/CAMPUS/SITE/DEPARTMENT/SPECIALTY。

ALTER TABLE org_unit DROP CONSTRAINT ck_org_unit_level;
ALTER TABLE org_unit ADD CONSTRAINT ck_org_unit_level
    CHECK (level_code IN ('PLATFORM','TENANT','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT','SPECIALTY'));

COMMENT ON COLUMN org_unit.level_code IS '组织层级：平台权威层、租户根、集团、医院、院区、服务点、科室、专病';
