-- MedKernel SYS-01 PR2 · 临床事件组织上下文持久化（人大金仓）
-- ROLLBACK: 如需回滚，先确认无异步临床事件依赖完整组织上下文，再删除 clinical_event.org_scope_json 列。

ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS org_scope_json TEXT NULL;

COMMENT ON COLUMN clinical_event.org_scope_json IS '接收临床事件时的组织上下文 JSON，用于异步派发规则/路径/CDSS 时恢复同源组织维度';
