-- MedKernel v1.0 GA · API-07 推荐/CDSS 客户面契约补强（PostgreSQL）
-- ROLLBACK: 若需回退，先删除 uk_rec_feedback_idempotency 与 idempotency_key，再将 ck_rec_fatigue_signal 恢复为不含 SUPPRESSED 的枚举；已有 SUPPRESSED 信号必须按审计策略归档后处理。

ALTER TABLE recommendation_feedback ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_rec_feedback_idempotency
    ON recommendation_feedback (tenant_id, card_id, idempotency_key);

ALTER TABLE recommendation_fatigue_signal DROP CONSTRAINT IF EXISTS ck_rec_fatigue_signal;
ALTER TABLE recommendation_fatigue_signal ADD CONSTRAINT ck_rec_fatigue_signal
    CHECK (signal_type IN (
        'SHOWN','SILENT_RECORDED','VIEWED','ACCEPTED',
        'REJECTED','DEFERRED','DISMISSED','SUPPRESSED'
    ));

COMMENT ON COLUMN recommendation_feedback.idempotency_key IS '反馈幂等键：同租户同推荐卡同键只记录一次反馈';
COMMENT ON COLUMN recommendation_fatigue_signal.signal_type IS '信号类型：SHOWN 已展示 / SILENT_RECORDED 静默试运行 / VIEWED 用户查看 / ACCEPTED 用户采纳 / REJECTED 用户不采纳 / DEFERRED 稍后处理 / DISMISSED 关闭忽略 / SUPPRESSED 疲劳治理抑制';
