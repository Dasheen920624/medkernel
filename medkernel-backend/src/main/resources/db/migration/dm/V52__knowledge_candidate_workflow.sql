-- MedKernel v1.0 GA · KNOW-02 知识候选识别与审核去重工作流（达梦）
-- ROLLBACK：确认无 PENDING_REPLACEMENT_REVIEW 候选和审核记录后，删除新增表并恢复知识版本状态约束。

ALTER TABLE knowledge_asset_version DROP CONSTRAINT ck_knowledge_asset_version_status;
ALTER TABLE knowledge_asset_version ADD CONSTRAINT ck_knowledge_asset_version_status CHECK (status IN
    ('DRAFT','CANDIDATE','UNDER_REVIEW','PENDING_REPLACEMENT_REVIEW','ACTIVE','SUPERSEDED','WITHDRAWN','REJECTED'));

CREATE TABLE mk_knowledge_candidate_classification (
    id                    NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id             VARCHAR2(64)  NOT NULL,
    org_path              VARCHAR2(512) NULL,
    identity_id           NUMBER(19)    NOT NULL,
    candidate_version_id  NUMBER(19)    NULL,
    active_version_id     NUMBER(19)    NULL,
    classification        VARCHAR2(32)  NOT NULL,
    review_status         VARCHAR2(32)  NOT NULL,
    content_hash          VARCHAR2(128) NOT NULL,
    basis                 VARCHAR2(2048) NOT NULL,
    diff_summary          VARCHAR2(2048) NULL,
    created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_knowledge_candidate_classification CHECK (classification IN
        ('NEW_ASSET','SAME_IDENTITY_NEW_VERSION','DUPLICATE','CONFLICT')),
    CONSTRAINT ck_knowledge_candidate_review_status CHECK (review_status IN
        ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED'))
);

CREATE INDEX idx_candidate_classification_identity ON mk_knowledge_candidate_classification (tenant_id, identity_id, created_at);
CREATE INDEX idx_candidate_classification_status ON mk_knowledge_candidate_classification (tenant_id, review_status);
CREATE INDEX idx_candidate_classification_candidate ON mk_knowledge_candidate_classification (tenant_id, candidate_version_id);

CREATE TABLE mk_knowledge_review_assignment (
    id                           NUMBER(19)   IDENTITY PRIMARY KEY,
    tenant_id                    VARCHAR2(64) NOT NULL,
    org_path                     VARCHAR2(512) NULL,
    candidate_classification_id  NUMBER(19)   NOT NULL,
    identity_id                  NUMBER(19)   NOT NULL,
    candidate_version_id         NUMBER(19)   NOT NULL,
    assigned_to                  VARCHAR2(64) NOT NULL,
    review_status                VARCHAR2(32) NOT NULL,
    decision                     VARCHAR2(16) NULL,
    reason                       VARCHAR2(1024) NULL,
    decided_by                   VARCHAR2(64) NULL,
    decided_at                   TIMESTAMP    NULL,
    created_at                   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by                   VARCHAR2(64) DEFAULT 'system' NOT NULL,
    updated_at                   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by                   VARCHAR2(64) DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_review_assignment_review_status CHECK (review_status IN
        ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED')),
    CONSTRAINT ck_review_assignment_decision CHECK (decision IS NULL OR decision IN ('APPROVE','REJECT'))
);

CREATE INDEX idx_review_assignment_identity ON mk_knowledge_review_assignment (tenant_id, identity_id, created_at);
CREATE INDEX idx_review_assignment_status ON mk_knowledge_review_assignment (tenant_id, review_status);
CREATE INDEX idx_review_assignment_candidate ON mk_knowledge_review_assignment (tenant_id, candidate_version_id);

COMMENT ON TABLE mk_knowledge_candidate_classification IS '知识候选新旧识别结果：记录新建、同身份新版、重复、冲突及其判定依据';
COMMENT ON COLUMN mk_knowledge_candidate_classification.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_knowledge_candidate_classification.org_path IS '七层组织作用域路径快照，用于审核队列按集团、医院、科室或专病切分';
COMMENT ON COLUMN mk_knowledge_candidate_classification.identity_id IS '知识身份 ID';
COMMENT ON COLUMN mk_knowledge_candidate_classification.candidate_version_id IS '候选知识版本 ID；重复候选不落版本时为空';
COMMENT ON COLUMN mk_knowledge_candidate_classification.active_version_id IS '对照的当前 ACTIVE 版本 ID';
COMMENT ON COLUMN mk_knowledge_candidate_classification.classification IS '候选分类：NEW_ASSET 新建 / SAME_IDENTITY_NEW_VERSION 同身份新版 / DUPLICATE 重复 / CONFLICT 冲突';
COMMENT ON COLUMN mk_knowledge_candidate_classification.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / DUPLICATE_SKIPPED 重复跳过 / APPROVED 通过 / REJECTED 拒绝';
COMMENT ON COLUMN mk_knowledge_candidate_classification.content_hash IS '候选内容 SHA-256 指纹';
COMMENT ON COLUMN mk_knowledge_candidate_classification.basis IS '分类依据，记录命中的身份、内容指纹、来源分级或冲突说明';
COMMENT ON COLUMN mk_knowledge_candidate_classification.diff_summary IS '候选与当前权威版本的对照摘要';

COMMENT ON TABLE mk_knowledge_review_assignment IS '知识候选审核分派与结论记录：仅待替换审核候选进入此表，重复候选不建待办';
COMMENT ON COLUMN mk_knowledge_review_assignment.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.org_path IS '七层组织作用域路径快照，用于审核任务按集团、医院、科室或专病切分';
COMMENT ON COLUMN mk_knowledge_review_assignment.candidate_classification_id IS '候选分类记录 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.identity_id IS '知识身份 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.candidate_version_id IS '候选知识版本 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.assigned_to IS '审核分派用户 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / APPROVED 通过 / REJECTED 拒绝';
COMMENT ON COLUMN mk_knowledge_review_assignment.decision IS '审核结论：APPROVE 通过 / REJECT 拒绝';
COMMENT ON COLUMN mk_knowledge_review_assignment.reason IS '审核理由或拒绝原因';
COMMENT ON COLUMN mk_knowledge_review_assignment.decided_by IS '作出审核结论的用户 ID';
COMMENT ON COLUMN mk_knowledge_review_assignment.decided_at IS '审核结论时间';
