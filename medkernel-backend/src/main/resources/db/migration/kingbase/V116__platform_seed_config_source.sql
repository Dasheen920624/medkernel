-- MedKernel v1.0 GA · 配置中心平台基线来源
-- 回滚：回退前先将 PLATFORM_SEED 来源配置项迁回 API 或 YML_SEED，再恢复旧来源约束，避免现有数据违反约束。
ALTER TABLE mk_config_item DROP CONSTRAINT ck_config_item_source;
ALTER TABLE mk_config_item ADD CONSTRAINT ck_config_item_source
    CHECK (source IN ('YML_SEED','DB','IMPORT','API','PLATFORM_SEED'));

COMMENT ON COLUMN mk_config_item.source IS '配置来源：YML_SEED 表示启动配置种子，PLATFORM_SEED 表示平台治理基线种子，API/IMPORT/DB 表示运行期维护来源';
