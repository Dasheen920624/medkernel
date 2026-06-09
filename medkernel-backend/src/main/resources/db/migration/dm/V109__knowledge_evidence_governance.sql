-- MedKernel v1.0 GA · 知识循证与复审治理（达梦）
-- ROLLBACK：删除复审到期索引、周期约束及 knowledge_asset_version 新增字段。

ALTER TABLE knowledge_asset_version ADD review_cycle_months INTEGER DEFAULT 12 NOT NULL;
ALTER TABLE knowledge_asset_version ADD next_review_at TIMESTAMP NULL;
ALTER TABLE knowledge_asset_version
    ADD CONSTRAINT ck_knowledge_av_review_cycle CHECK (review_cycle_months BETWEEN 1 AND 60);

UPDATE knowledge_asset_version
SET next_review_at = ADD_MONTHS(
    COALESCE(reviewed_at, activated_at, updated_at),
    review_cycle_months
)
WHERE status = 'ACTIVE'
  AND next_review_at IS NULL;

CREATE INDEX idx_knowledge_av_review_due
    ON knowledge_asset_version (tenant_id, status, next_review_at);

COMMENT ON COLUMN knowledge_asset_version.review_cycle_months IS '权威知识复审周期，单位为月，允许 1 至 60';
COMMENT ON COLUMN knowledge_asset_version.next_review_at IS '版本激活评审后计算的下次复审到期时间';

ALTER TABLE knowledge_identity DROP CONSTRAINT ck_knowledge_identity_status;
ALTER TABLE knowledge_identity
    ADD CONSTRAINT ck_knowledge_identity_status CHECK (status IN ('ACTIVE','DEPRECATED','WITHDRAWN','ARCHIVED'));

ALTER TABLE knowledge_supersession ADD successor_identity_id BIGINT NULL;
ALTER TABLE knowledge_supersession ADD grace_period_end TIMESTAMP NULL;
ALTER TABLE knowledge_supersession ADD migration_guidance VARCHAR2(1000) NULL;
ALTER TABLE knowledge_supersession DROP CONSTRAINT ck_knowledge_supersession_type;
ALTER TABLE knowledge_supersession
    ADD CONSTRAINT ck_knowledge_supersession_type CHECK (transition_type IN
        ('ACTIVATE','REPLACE','WITHDRAW','RESTORE','ROLLBACK','DEPRECATE','RETIRE'));

CREATE INDEX idx_supersession_successor
    ON knowledge_supersession (tenant_id, successor_identity_id);
CREATE INDEX idx_supersession_grace
    ON knowledge_supersession (transition_type, grace_period_end);

COMMENT ON COLUMN knowledge_supersession.successor_identity_id IS '弃用知识身份的稳定后继身份 ID';
COMMENT ON COLUMN knowledge_supersession.grace_period_end IS '旧身份允许迁移引用的宽限期结束时间';
COMMENT ON COLUMN knowledge_supersession.migration_guidance IS '旧身份引用方迁移到后继身份的明确指引';
