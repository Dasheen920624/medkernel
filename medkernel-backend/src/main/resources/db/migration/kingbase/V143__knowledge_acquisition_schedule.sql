-- MedKernel 第二阶段 P2-C · AIK-STD-14 公域资料自动获取调度（KingbaseES）
-- 新项目基线：仅声明正式调度字段，不做旧来源兼容回填。

ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN schedule_enabled_flag CHAR(1) NOT NULL DEFAULT 'N';
ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN schedule_interval_minutes INTEGER NULL;
ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN next_check_at TIMESTAMP NULL;
ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN last_check_at TIMESTAMP NULL;
ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN default_format VARCHAR(24) NULL;
ALTER TABLE mk_knowledge_acquisition_source
    ADD COLUMN generation_plan_json TEXT NULL;

ALTER TABLE mk_knowledge_acquisition_source
    ADD CONSTRAINT ck_mk_knowledge_acquisition_source_schedule CHECK (schedule_enabled_flag IN ('Y','N'));
ALTER TABLE mk_knowledge_acquisition_source
    ADD CONSTRAINT ck_mk_knowledge_acquisition_interval CHECK (
        schedule_interval_minutes IS NULL OR schedule_interval_minutes > 0
    );
ALTER TABLE mk_knowledge_acquisition_source
    ADD CONSTRAINT ck_mk_knowledge_acquisition_default_format CHECK (
        default_format IS NULL OR default_format IN ('STRUCTURED_TEXT','PDF','WORD')
    );

CREATE INDEX idx_mk_knowledge_acquisition_schedule_due
    ON mk_knowledge_acquisition_source (schedule_enabled_flag, next_check_at, tenant_id);

COMMENT ON COLUMN mk_knowledge_acquisition_source.schedule_enabled_flag IS '公域资料自动获取调度开关，默认关闭';
COMMENT ON COLUMN mk_knowledge_acquisition_source.schedule_interval_minutes IS '公域资料自动获取调度间隔分钟数';
COMMENT ON COLUMN mk_knowledge_acquisition_source.next_check_at IS '下一次公域资料自动获取检查时间';
COMMENT ON COLUMN mk_knowledge_acquisition_source.last_check_at IS '最近一次公域资料自动获取检查时间';
COMMENT ON COLUMN mk_knowledge_acquisition_source.default_format IS '调度获取时默认文档格式';
COMMENT ON COLUMN mk_knowledge_acquisition_source.generation_plan_json IS '调度获取成功后的候选生成计划 JSON';
