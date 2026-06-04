-- MedKernel v1.0 GA · SVC-CLINICAL-03 待办转交说明（PostgreSQL）
-- ROLLBACK：如需回滚，先导出 mk_engine_workflow_todo 转交审计证据，再删除 transfer_reason 列。

ALTER TABLE mk_engine_workflow_todo
    ADD COLUMN IF NOT EXISTS transfer_reason TEXT NULL;

COMMENT ON COLUMN mk_engine_workflow_todo.transfer_reason IS '待办转交说明，记录转交原因和交接要求';
