-- MedKernel v1.0 GA · 统一资产生命周期与发布质量门（Oracle）
-- ROLLBACK：删除新增审计列，并恢复旧资产、发布计划与激活动作约束。

ALTER TABLE mk_version_asset_version DROP CONSTRAINT ck_mk_version_asset_version_status;
ALTER TABLE mk_version_asset_version
    ADD CONSTRAINT ck_mk_version_asset_version_status CHECK (status IN
        ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','DEPRECATED','RETIRED'));

ALTER TABLE mk_version_release_plan ADD (
    electronic_signature_id      VARCHAR2(128)  NULL,
    electronic_signature_subject VARCHAR2(256)  NULL,
    electronic_signature_hash    VARCHAR2(64)   NULL,
    electronic_signature_signed_at TIMESTAMP     NULL,
    quality_gate_summary         VARCHAR2(2048) NULL
);

ALTER TABLE mk_version_release_plan DROP CONSTRAINT ck_mk_version_release_plan_status;
ALTER TABLE mk_version_release_plan
    ADD CONSTRAINT ck_mk_version_release_plan_status CHECK (status IN
        ('IN_REVIEW','REJECTED','APPROVED','PUBLISHED','GRAY','ROLLED_BACK','FAILED'));

ALTER TABLE mk_version_activation_transaction DROP CONSTRAINT ck_mk_version_activation_transaction_action;
ALTER TABLE mk_version_activation_transaction
    ADD CONSTRAINT ck_mk_version_activation_transaction_action CHECK (action IN ('PUBLISH','ROLLBACK'));

COMMENT ON COLUMN mk_version_asset_version.status IS '统一生命周期：DRAFT 草稿、IN_REVIEW 评审中、APPROVED 已批准、PUBLISHED 已发布、DEPRECATED 已弃用、RETIRED 已退役';
COMMENT ON COLUMN mk_version_release_plan.status IS '发布计划状态：评审中、拒绝、批准、发布、灰度、已回滚或失败';
COMMENT ON COLUMN mk_version_release_plan.electronic_signature_id IS '电子签名业务 ID；平台或高风险发布必填';
COMMENT ON COLUMN mk_version_release_plan.electronic_signature_subject IS '签名主体，格式为签名人 ID 与姓名';
COMMENT ON COLUMN mk_version_release_plan.electronic_signature_hash IS '电子签名 SHA-256 摘要';
COMMENT ON COLUMN mk_version_release_plan.electronic_signature_signed_at IS '电子签名完成时间';
COMMENT ON COLUMN mk_version_release_plan.quality_gate_summary IS '平台发布质量门摘要：结构、术语字段、依赖、安全、影响模拟与同行评审';
COMMENT ON COLUMN mk_version_activation_transaction.action IS '生效事务动作：PUBLISH 发布或 ROLLBACK 回滚';
