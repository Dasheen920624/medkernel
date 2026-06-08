-- MedKernel v1.0 GA · H-6 引擎领域事件统一协同来源（Oracle）
-- ROLLBACK：如需回滚，先确认无 RULE_EVENT / PATHWAY_EVENT 待办通知与新质控告警，或导出审计证据后删除相关行，再恢复旧约束。

ALTER TABLE mk_engine_workflow_todo DROP CONSTRAINT ck_workflow_todo_source_type;

ALTER TABLE mk_engine_workflow_todo ADD CONSTRAINT ck_workflow_todo_source_type
    CHECK (source_type IN (
        'FOLLOWUP_TASK','SAFETY_REVIEW','RECOMMENDATION_CARD','PATHWAY_NODE',
        'RULE_EVENT','PATHWAY_EVENT','NURSING_TASK','REPORT_INTERPRETATION','BEDSIDE_KNOWLEDGE'
    ));

ALTER TABLE mk_engine_notification DROP CONSTRAINT ck_notification_source_type;

ALTER TABLE mk_engine_notification ADD CONSTRAINT ck_notification_source_type
    CHECK (source_type IN (
        'FOLLOWUP_EVENT','SAFETY_REVIEW','WORKFLOW_TODO','SYNC_EVENT','RULE_EVENT','PATHWAY_EVENT'
    ));

ALTER TABLE mk_quality_dashboard_alert DROP CONSTRAINT ck_quality_dashboard_alert_type;

ALTER TABLE mk_quality_dashboard_alert ADD CONSTRAINT ck_quality_dashboard_alert_type
    CHECK (alert_type IN (
        'HIGH_RISK_FINDING','OVERDUE_RECTIFICATION','RULE_OVERRIDE','PATHWAY_VARIANCE','CLOCK_SLA_BREACH'
    ));

COMMENT ON COLUMN mk_engine_workflow_todo.source_type IS '待办来源类型，包含随访、安全、推荐、路径节点、规则事件、路径事件和推荐派生任务';
COMMENT ON COLUMN mk_engine_notification.source_type IS '通知来源类型，包含随访事件、安全复核、协同待办、临床同步、规则事件和路径事件';
COMMENT ON COLUMN mk_quality_dashboard_alert.alert_type IS '质控预警类型，包含高风险发现、逾期整改、规则越权、路径变异和关键时钟超时';
