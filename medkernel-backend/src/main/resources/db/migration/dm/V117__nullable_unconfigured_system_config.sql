-- MedKernel v1.0 GA · 配置中心未配置值跨方言语义
-- 回滚：回填当前值和历史表的所有 NULL 后恢复 NOT NULL；未配置项应先删除或改为明确受管值。
ALTER TABLE mk_config_item MODIFY (config_value NULL);
ALTER TABLE mk_config_history MODIFY (after_value NULL);

COMMENT ON COLUMN mk_config_item.config_value IS '配置值；NULL 或空字符串表示尚未配置，读取层统一返回空字符串';
COMMENT ON COLUMN mk_config_history.after_value IS '变更后配置值；NULL 或空字符串表示回滚到尚未配置状态';
