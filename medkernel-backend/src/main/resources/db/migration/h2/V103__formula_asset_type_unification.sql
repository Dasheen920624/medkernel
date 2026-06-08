-- MedKernel v1.0 GA · P13-4 受控公式纳入统一资产类型（H2）
-- ROLLBACK：确认无 FORMULA 资产版本、包条目、模板项、继承覆盖、发布计划、激活事务或重放绑定后，删除本迁移新增 CHECK 并恢复上一版资产类型集合。

ALTER TABLE mk_version_asset_version DROP CONSTRAINT ck_mk_version_asset_version_type;
ALTER TABLE mk_version_asset_version ADD CONSTRAINT ck_mk_version_asset_version_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE package_item DROP CONSTRAINT ck_package_item_asset_type;
ALTER TABLE package_item ADD CONSTRAINT ck_package_item_asset_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE mk_pkg_pilot_template_item DROP CONSTRAINT ck_pkg_tpli_type;
ALTER TABLE mk_pkg_pilot_template_item ADD CONSTRAINT ck_pkg_tpli_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE mk_version_inheritance_override DROP CONSTRAINT ck_mk_version_inheritance_override_type;
ALTER TABLE mk_version_inheritance_override ADD CONSTRAINT ck_mk_version_inheritance_override_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE mk_version_release_plan DROP CONSTRAINT ck_mk_version_release_plan_type;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_plan_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE mk_version_activation_transaction DROP CONSTRAINT ck_mk_version_activation_transaction_type;
ALTER TABLE mk_version_activation_transaction ADD CONSTRAINT ck_mk_version_activation_transaction_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE mk_version_replay_binding DROP CONSTRAINT ck_mk_version_replay_binding_type;
ALTER TABLE mk_version_replay_binding ADD CONSTRAINT ck_mk_version_replay_binding_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

COMMENT ON COLUMN mk_version_asset_version.asset_type IS '资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险、条件片段、值集、受控公式、医嘱集、动作卡、子路径';
COMMENT ON COLUMN package_item.asset_type IS '包内资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险、条件片段、值集、受控公式、医嘱集、动作卡、子路径';
COMMENT ON COLUMN mk_pkg_pilot_template_item.asset_type IS '模板资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险、条件片段、值集、受控公式、医嘱集、动作卡、子路径';
COMMENT ON COLUMN mk_version_inheritance_override.asset_type IS '继承覆盖资产类型（统一枚举）';
COMMENT ON COLUMN mk_version_release_plan.asset_type IS '发布计划资产类型（统一枚举）';
COMMENT ON COLUMN mk_version_activation_transaction.asset_type IS '激活事务资产类型（统一枚举）';
COMMENT ON COLUMN mk_version_replay_binding.asset_type IS '历史重放资产类型（统一枚举）';
