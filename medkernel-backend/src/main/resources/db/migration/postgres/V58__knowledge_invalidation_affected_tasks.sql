-- MedKernel v1.0 GA · SYS-08 知识紧急失效与影响任务（PostgreSQL）
-- ROLLBACK：如需回滚，先导出 mk_knowledge_invalidation 与 mk_knowledge_affected_case_task 证据，再删除两张新增表。

CREATE TABLE IF NOT EXISTS mk_knowledge_invalidation (
    id                         BIGSERIAL PRIMARY KEY,
    tenant_id                  VARCHAR(64)  NOT NULL,
    identity_id                BIGINT       NOT NULL,
    version_id                 BIGINT       NOT NULL,
    invalidation_type          VARCHAR(32)  NOT NULL,
    status                     VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    risk_level                 VARCHAR(16)  NOT NULL,
    reason                     TEXT         NOT NULL,
    organization_scope         VARCHAR(256) NOT NULL,
    applicable_scope           VARCHAR(256) NOT NULL,
    authorized_by              VARCHAR(64)  NOT NULL,
    invalidated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expedited_review_required  BOOLEAN      NOT NULL DEFAULT TRUE,
    trace_id                   VARCHAR(128) NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_mk_knowledge_invalidation_key
        UNIQUE (tenant_id, identity_id, version_id, invalidation_type),
    CONSTRAINT ck_mk_knowledge_invalidation_type CHECK (invalidation_type IN (
        'SUPERSEDED_REPLACEMENT','EMERGENCY_WITHDRAW','SOURCE_RECALL','SAFETY_ALERT'
    )),
    CONSTRAINT ck_mk_knowledge_invalidation_status CHECK (status IN ('OPEN','RESOLVED','CANCELLED')),
    CONSTRAINT ck_mk_knowledge_invalidation_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_mk_knowledge_invalidation_review CHECK (expedited_review_required IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_mk_knowledge_invalidation_identity
    ON mk_knowledge_invalidation (tenant_id, identity_id, invalidated_at);
CREATE INDEX IF NOT EXISTS idx_mk_knowledge_invalidation_status
    ON mk_knowledge_invalidation (tenant_id, status, invalidated_at);

CREATE TABLE IF NOT EXISTS mk_knowledge_affected_case_task (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          VARCHAR(64)  NOT NULL,
    task_key           VARCHAR(256) NOT NULL,
    invalidation_id    BIGINT       NOT NULL,
    identity_id        BIGINT       NOT NULL,
    version_id         BIGINT       NOT NULL,
    task_type          VARCHAR(32)  NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    target_type        VARCHAR(32)  NOT NULL,
    target_ref         VARCHAR(256) NOT NULL,
    reason             TEXT         NOT NULL,
    due_at             TIMESTAMPTZ  NOT NULL,
    assigned_to        VARCHAR(64)  NULL,
    trace_id           VARCHAR(128) NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_mk_knowledge_affected_task_key UNIQUE (tenant_id, task_key),
    CONSTRAINT ck_mk_knowledge_affected_task_type CHECK (task_type IN (
        'PHYSICIAN_REVIEW','PACKAGE_RESYNC','SYNC_ALERT'
    )),
    CONSTRAINT ck_mk_knowledge_affected_task_status CHECK (status IN ('OPEN','IN_PROGRESS','DONE','CANCELLED')),
    CONSTRAINT ck_mk_knowledge_affected_target_type CHECK (target_type IN (
        'KNOWLEDGE_VERSION','PACKAGE_DEPENDENCY','SYNC_TARGET','PATIENT_CASE','PATIENT_PATHWAY'
    ))
);

CREATE INDEX IF NOT EXISTS idx_mk_knowledge_affected_task_status
    ON mk_knowledge_affected_case_task (tenant_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_mk_knowledge_affected_task_version
    ON mk_knowledge_affected_case_task (tenant_id, version_id, task_type);

COMMENT ON TABLE mk_knowledge_invalidation IS 'SYS-08 知识失效记录，保存旧版原子替换、紧急限制、授权和审计证据';
COMMENT ON COLUMN mk_knowledge_invalidation.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_knowledge_invalidation.identity_id IS '知识身份 ID';
COMMENT ON COLUMN mk_knowledge_invalidation.version_id IS '被失效的知识版本 ID';
COMMENT ON COLUMN mk_knowledge_invalidation.invalidation_type IS '失效类型：原子替换、紧急撤回、来源召回或安全警示';
COMMENT ON COLUMN mk_knowledge_invalidation.status IS '失效处置状态';
COMMENT ON COLUMN mk_knowledge_invalidation.risk_level IS '被失效版本的风险等级';
COMMENT ON COLUMN mk_knowledge_invalidation.reason IS '失效原因和安全依据';
COMMENT ON COLUMN mk_knowledge_invalidation.organization_scope IS '失效生效组织范围';
COMMENT ON COLUMN mk_knowledge_invalidation.applicable_scope IS '失效生效适用人群或上下文';
COMMENT ON COLUMN mk_knowledge_invalidation.authorized_by IS '授权撤回的操作人';
COMMENT ON COLUMN mk_knowledge_invalidation.invalidated_at IS '失效触发时间';
COMMENT ON COLUMN mk_knowledge_invalidation.expedited_review_required IS '是否需要加急审核';
COMMENT ON COLUMN mk_knowledge_invalidation.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN mk_knowledge_invalidation.created_at IS '创建时间';
COMMENT ON COLUMN mk_knowledge_invalidation.created_by IS '创建人';
COMMENT ON COLUMN mk_knowledge_invalidation.updated_at IS '更新时间';
COMMENT ON COLUMN mk_knowledge_invalidation.updated_by IS '更新人';

COMMENT ON TABLE mk_knowledge_affected_case_task IS 'SYS-08 知识失效影响处置任务，记录医师复核、补同步和同步告警';
COMMENT ON COLUMN mk_knowledge_affected_case_task.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.task_key IS '任务幂等键，防止重复派发';
COMMENT ON COLUMN mk_knowledge_affected_case_task.invalidation_id IS '关联的知识失效记录 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.identity_id IS '知识身份 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.version_id IS '被失效的知识版本 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.task_type IS '任务类型：医师复核、包补同步或同步告警';
COMMENT ON COLUMN mk_knowledge_affected_case_task.status IS '任务处置状态';
COMMENT ON COLUMN mk_knowledge_affected_case_task.target_type IS '任务目标类型';
COMMENT ON COLUMN mk_knowledge_affected_case_task.target_ref IS '任务目标引用，不得伪造患者 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.reason IS '任务派发原因';
COMMENT ON COLUMN mk_knowledge_affected_case_task.due_at IS '任务截止时间';
COMMENT ON COLUMN mk_knowledge_affected_case_task.assigned_to IS '任务处理人或处理角色';
COMMENT ON COLUMN mk_knowledge_affected_case_task.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN mk_knowledge_affected_case_task.created_at IS '创建时间';
COMMENT ON COLUMN mk_knowledge_affected_case_task.created_by IS '创建人';
COMMENT ON COLUMN mk_knowledge_affected_case_task.updated_at IS '更新时间';
COMMENT ON COLUMN mk_knowledge_affected_case_task.updated_by IS '更新人';
