-- MedKernel v1.0 GA · EMR-LEVEL-01 电子病历评级目标与项目映射（H2）
-- ROLLBACK：如需回滚，先导出电子病历评级目标、项目差距和整改任务证据，再删除本迁移三张 mk_emr_level_* 表。

CREATE TABLE IF NOT EXISTS mk_emr_level_target (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_id            VARCHAR(128) NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    hospital_org_id      VARCHAR(64)  NOT NULL,
    target_level         INT          NOT NULL,
    standard_version     VARCHAR(64)  NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    total_item_count     INT          NOT NULL DEFAULT 0,
    satisfied_item_count INT          NOT NULL DEFAULT 0,
    gap_item_count       INT          NOT NULL DEFAULT 0,
    progress_rate        DECIMAL(7,4) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_emr_level_target_id UNIQUE (tenant_id, target_id),
    CONSTRAINT uk_emr_level_target_scope UNIQUE (tenant_id, hospital_org_id, standard_version),
    CONSTRAINT ck_emr_level_target_level CHECK (target_level IN (4,5,6)),
    CONSTRAINT ck_emr_level_target_status CHECK (status IN ('DRAFT','PUBLISHED','ACTIVE')),
    CONSTRAINT ck_emr_level_target_counts CHECK (
        total_item_count >= 0
        AND satisfied_item_count >= 0
        AND gap_item_count >= 0
        AND progress_rate >= 0
        AND progress_rate <= 1
    )
);

CREATE INDEX IF NOT EXISTS idx_emr_level_target_scope
    ON mk_emr_level_target (tenant_id, hospital_org_id, status);

CREATE TABLE IF NOT EXISTS mk_emr_level_item (
    id                        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id                   VARCHAR(128) NOT NULL,
    tenant_id                 VARCHAR(64)  NOT NULL,
    target_id                 VARCHAR(128) NOT NULL,
    standard_version          VARCHAR(64)  NOT NULL,
    item_code                 VARCHAR(128) NOT NULL,
    item_name                 VARCHAR(256) NOT NULL,
    required_level            INT          NOT NULL,
    capability_code           VARCHAR(128) NOT NULL,
    capability_name           VARCHAR(256) NOT NULL,
    capability_status         VARCHAR(32)  NOT NULL,
    evidence_ref              VARCHAR(512) NULL,
    evidence_summary          CLOB         NOT NULL,
    responsible_department_id VARCHAR(64)  NULL,
    due_at                    TIMESTAMP    NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128) NULL,
    CONSTRAINT uk_emr_level_item_id UNIQUE (tenant_id, item_id),
    CONSTRAINT uk_emr_level_item_capability UNIQUE (tenant_id, target_id, item_code, capability_code),
    CONSTRAINT ck_emr_level_item_required_level CHECK (required_level IN (4,5,6)),
    CONSTRAINT ck_emr_level_item_status CHECK (capability_status IN ('SATISFIED','GAP','MISSING_EVIDENCE'))
);

CREATE INDEX IF NOT EXISTS idx_emr_level_item_target
    ON mk_emr_level_item (tenant_id, target_id, capability_status);

CREATE TABLE IF NOT EXISTS mk_emr_level_gap (
    id                        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gap_id                    VARCHAR(128) NOT NULL,
    tenant_id                 VARCHAR(64)  NOT NULL,
    target_id                 VARCHAR(128) NOT NULL,
    item_id                   VARCHAR(128) NOT NULL,
    item_code                 VARCHAR(128) NOT NULL,
    capability_code           VARCHAR(128) NOT NULL,
    capability_status         VARCHAR(32)  NOT NULL,
    gap_status                VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    gap_reason                CLOB         NOT NULL,
    responsible_department_id VARCHAR(64)  NOT NULL,
    due_at                    TIMESTAMP    NOT NULL,
    rectification_task_id     VARCHAR(128) NOT NULL,
    evidence_ref              VARCHAR(512) NULL,
    closed_at                 TIMESTAMP    NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128) NULL,
    CONSTRAINT uk_emr_level_gap_id UNIQUE (tenant_id, gap_id),
    CONSTRAINT uk_emr_level_gap_item UNIQUE (tenant_id, target_id, item_id),
    CONSTRAINT ck_emr_level_gap_capability CHECK (capability_status IN ('GAP','MISSING_EVIDENCE')),
    CONSTRAINT ck_emr_level_gap_status CHECK (gap_status IN ('OPEN','RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_emr_level_gap_target
    ON mk_emr_level_gap (tenant_id, target_id, gap_status);
CREATE INDEX IF NOT EXISTS idx_emr_level_gap_task
    ON mk_emr_level_gap (tenant_id, rectification_task_id);

COMMENT ON TABLE mk_emr_level_target IS 'EMR-LEVEL-01 电子病历评级目标表，保存机构目标级别、标准版本和真实进度';
COMMENT ON TABLE mk_emr_level_item IS 'EMR-LEVEL-01 电子病历评级标准项能力映射表，记录目标级别内项目与系统能力证据';
COMMENT ON TABLE mk_emr_level_gap IS 'EMR-LEVEL-01 电子病历评级差距表，保存缺口原因并关联真实整改任务';
COMMENT ON COLUMN mk_emr_level_target.target_level IS '目标电子病历评级级别，仅允许 4、5、6';
COMMENT ON COLUMN mk_emr_level_target.progress_rate IS '按目标级别内标准项证据计算的进度比例';
COMMENT ON COLUMN mk_emr_level_item.capability_status IS '能力点状态，缺证据时为 MISSING_EVIDENCE';
COMMENT ON COLUMN mk_emr_level_item.evidence_ref IS '证明能力满足的真实证据引用';
COMMENT ON COLUMN mk_emr_level_gap.rectification_task_id IS '联动创建的整改任务 ID';
COMMENT ON COLUMN mk_emr_level_gap.gap_reason IS '不能计入评级进度的真实差距原因';
COMMENT ON COLUMN mk_emr_level_gap.trace_id IS '链路追踪 ID';
