-- MedKernel v1.0 GA · P0-1.4 资产类型枚举归一（H2 PostgreSQL 兼容模式）
-- VersionedAssetType 与 PackageItemAssetType 合并为单一枚举，补 FOLLOWUP/FIELD_CATALOG/RECOMMENDATION/SAFETY/CDSS_RISK（设计 unified-asset-versioning / 附录 G·D2）；
-- 放宽 mk_version_asset_version.asset_type 与 package_item.asset_type 两处 CHECK 至统一 11 值集合，值名兼容、存量数据不受影响。
-- ROLLBACK：确认无新增类型（FOLLOWUP/FIELD_CATALOG/RECOMMENDATION/SAFETY/CDSS_RISK，及 package_item 的 PACKAGE）资产后，删除两 CHECK 并恢复各自原 6 值集合。

ALTER TABLE mk_version_asset_version DROP CONSTRAINT ck_mk_version_asset_version_type;
ALTER TABLE mk_version_asset_version ADD CONSTRAINT ck_mk_version_asset_version_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK'));

ALTER TABLE package_item DROP CONSTRAINT ck_package_item_asset_type;
ALTER TABLE package_item ADD CONSTRAINT ck_package_item_asset_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK'));

COMMENT ON COLUMN mk_version_asset_version.asset_type IS '资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险';
COMMENT ON COLUMN package_item.asset_type IS '包内资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险';
