-- MedKernel v1.0 GA · API-09 随访客户面契约补强（H2 PostgreSQL 兼容模式）
-- ROLLBACK: 若需回退，先删除 uk_followup_*_idempotency 索引，再删除本迁移新增的幂等、问卷模板、答案、提交时间与执行人字段；已产生的返院任务和回流事件须按审计策略归档后处理。

ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;

ALTER TABLE followup_task ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;

ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS plan_id VARCHAR(64) NULL;
ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS questionnaire_template_id VARCHAR(128) NULL;
ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS answer_data CLOB NULL;
ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;
ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP NULL;
ALTER TABLE followup_questionnaire ADD COLUMN IF NOT EXISTS executor_id VARCHAR(64) NULL;

ALTER TABLE followup_event ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_followup_plan_idempotency
    ON followup_plan (tenant_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_followup_task_idempotency
    ON followup_task (tenant_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_followup_questionnaire_idempotency
    ON followup_questionnaire (tenant_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_followup_event_idempotency
    ON followup_event (tenant_id, event_type, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_followup_task_status_due
    ON followup_task (tenant_id, status, due_date);

CREATE INDEX IF NOT EXISTS idx_followup_questionnaire_plan
    ON followup_questionnaire (tenant_id, plan_id);

COMMENT ON COLUMN followup_plan.idempotency_key IS '计划生成幂等键：同租户同键只生成一次随访计划';
COMMENT ON COLUMN followup_task.idempotency_key IS '任务派发幂等键：同租户同键只生成一次随访任务';
COMMENT ON COLUMN followup_questionnaire.plan_id IS '问卷所属随访计划ID';
COMMENT ON COLUMN followup_questionnaire.questionnaire_template_id IS '问卷模板ID';
COMMENT ON COLUMN followup_questionnaire.answer_data IS '结构化作答数据JSON';
COMMENT ON COLUMN followup_questionnaire.idempotency_key IS '问卷下发或作答幂等键';
COMMENT ON COLUMN followup_questionnaire.submitted_at IS '问卷作答提交时间';
COMMENT ON COLUMN followup_questionnaire.executor_id IS '问卷下发或作答执行人ID';
COMMENT ON COLUMN followup_event.idempotency_key IS '异常回院、通知请求或结果回流幂等键';
COMMENT ON COLUMN followup_task.status IS '状态(PENDING,IN_PROGRESS,COMPLETED,ABNORMAL_RETURN,OVERDUE,CANCELLED)';
COMMENT ON COLUMN followup_task.task_type IS '任务类型(QUESTIONNAIRE,EXAM,LAB,OUTPATIENT,RETURN_VISIT)';
COMMENT ON COLUMN followup_event.event_type IS '事件类型(ABNORMAL_RETURN,NOTIFICATION_REQUESTED,RESULT_INFLOW)';
