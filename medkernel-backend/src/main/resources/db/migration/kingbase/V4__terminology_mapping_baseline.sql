-- MedKernel v1.0 GA · GA-ENG-API-04 字典映射 + 映射包发布 baseline (人大金仓 V9，兼容 PostgreSQL)

CREATE TABLE IF NOT EXISTS standard_term (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          VARCHAR(64)  NOT NULL,
    standard_system    VARCHAR(64)  NOT NULL,
    term_code          VARCHAR(128) NOT NULL,
    category           VARCHAR(32)  NOT NULL,
    display_name       VARCHAR(512) NOT NULL,
    normalized_name    VARCHAR(512) NULL,
    version_no         VARCHAR(64)  NOT NULL DEFAULT '1',
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    source_version_id  BIGINT       NULL,
    evidence_text      VARCHAR(1024) NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_standard_term_code UNIQUE (tenant_id, standard_system, term_code, version_no),
    CONSTRAINT ck_standard_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_standard_term_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE INDEX IF NOT EXISTS idx_standard_term_tenant_category ON standard_term (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_standard_term_tenant_updated ON standard_term (tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS local_term (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    source_system    VARCHAR(64)  NOT NULL,
    local_code       VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    local_name       VARCHAR(512) NOT NULL,
    normalized_name  VARCHAR(512) NULL,
    department_id    VARCHAR(64)  NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'UNMAPPED',
    first_seen_at    TIMESTAMPTZ  NULL,
    last_seen_at     TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_local_term_code UNIQUE (tenant_id, source_system, local_code, category),
    CONSTRAINT ck_local_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_local_term_status CHECK (status IN ('UNMAPPED','MAPPED','DISABLED'))
);
CREATE INDEX IF NOT EXISTS idx_local_term_tenant_source ON local_term (tenant_id, source_system, status);
CREATE INDEX IF NOT EXISTS idx_local_term_department ON local_term (tenant_id, department_id);

CREATE TABLE IF NOT EXISTS term_mapping (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    local_term_id     BIGINT       NOT NULL,
    standard_term_id  BIGINT       NOT NULL,
    source_system     VARCHAR(64)  NULL,
    category          VARCHAR(32)  NULL,
    confidence        DOUBLE PRECISION NULL,
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    evidence_text     VARCHAR(1024) NULL,
    confirmed_by      VARCHAR(64)  NULL,
    confirmed_at      TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_term_mapping_status CHECK (status IN ('DRAFT','CONFIRMED','SUPERSEDED','ROLLED_BACK')),
    CONSTRAINT ck_term_mapping_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_term_mapping_tenant_status ON term_mapping (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_term_mapping_local_standard ON term_mapping (tenant_id, local_term_id, standard_term_id);

CREATE TABLE IF NOT EXISTS mapping_candidate (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    local_term_id     BIGINT       NOT NULL,
    standard_term_id  BIGINT       NOT NULL,
    confidence        DOUBLE PRECISION NULL,
    candidate_source  VARCHAR(32)  NOT NULL DEFAULT 'RULE',
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    evidence_text     VARCHAR(1024) NULL,
    conflict_flag     BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    review_note       VARCHAR(500) NULL,
    reviewed_by       VARCHAR(64)  NULL,
    reviewed_at       TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mapping_candidate_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    CONSTRAINT ck_mapping_candidate_source CHECK (candidate_source IN ('RULE','AI','MANUAL','IMPORT')),
    CONSTRAINT ck_mapping_candidate_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_mapping_candidate_tenant_status ON mapping_candidate (tenant_id, status, risk_level);

CREATE TABLE IF NOT EXISTS mapping_conflict (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    conflict_type     VARCHAR(32)  NOT NULL,
    local_term_id     BIGINT       NULL,
    standard_term_id  BIGINT       NULL,
    mapping_id        BIGINT       NULL,
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    description       VARCHAR(1024) NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    resolved_by       VARCHAR(64)  NULL,
    resolved_at       TIMESTAMPTZ  NULL,
    resolution_note   VARCHAR(500) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mapping_conflict_type CHECK (conflict_type IN
        ('ONE_TO_MANY','MANY_TO_ONE','DISABLED_CODE','CROSS_SYSTEM_INCONSISTENT','HOMONYM','SYNONYM_MISMATCH')),
    CONSTRAINT ck_mapping_conflict_status CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    CONSTRAINT ck_mapping_conflict_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_mapping_conflict_tenant_status ON mapping_conflict (tenant_id, status, risk_level);


-- ===== 表 / 列中文注释（GA-ENG-API-04 术语映射模块） =====

COMMENT ON TABLE standard_term IS '标准术语字典：按租户隔离的 ICD-10/SNOMED CT/LOINC/RxNorm/ATC 等标准词条；(tenant_id, standard_system, term_code, version_no) 唯一';
COMMENT ON COLUMN standard_term.tenant_id        IS '租户 ID';
COMMENT ON COLUMN standard_term.standard_system  IS '术语系统：ICD10 / SNOMED_CT / LOINC / RXNORM / ATC 等';
COMMENT ON COLUMN standard_term.term_code        IS '标准编码（业务键）';
COMMENT ON COLUMN standard_term.category         IS '术语分类：DIAGNOSIS 诊断 / PROCEDURE 手术 / DRUG 药品 / DEVICE 器械 / LAB 检验 / EXAM 检查 / ORDER 医嘱 / INSURANCE 医保 / DEPARTMENT 科室 / DOCUMENT 文书 / FOLLOWUP 随访 / OTHER 其他';
COMMENT ON COLUMN standard_term.display_name     IS '展示名';
COMMENT ON COLUMN standard_term.normalized_name  IS '归一化名（用于匹配）';
COMMENT ON COLUMN standard_term.version_no       IS '术语版本号（同一 term_code 可有多版本）';
COMMENT ON COLUMN standard_term.status           IS '状态：ACTIVE 可被引用 / DISABLED 已禁用';
COMMENT ON COLUMN standard_term.source_version_id IS '关联知识资产版本 id（可空，便于追溯术语包来源）';
COMMENT ON COLUMN standard_term.evidence_text    IS '收录证据 / 备注';

COMMENT ON TABLE local_term IS '院内本地术语字典：HIS/LIS/PACS 等来源系统采集到的原始词条；(tenant_id, source_system, local_code, category) 唯一';
COMMENT ON COLUMN local_term.tenant_id        IS '租户 ID';
COMMENT ON COLUMN local_term.source_system    IS '来源系统标识（HIS / LIS / PACS 等）';
COMMENT ON COLUMN local_term.local_code       IS '本地编码（业务键）';
COMMENT ON COLUMN local_term.category         IS '术语分类（同 standard_term.category 取值）';
COMMENT ON COLUMN local_term.local_name       IS '院内原始名称';
COMMENT ON COLUMN local_term.normalized_name  IS '归一化名（用于匹配）';
COMMENT ON COLUMN local_term.department_id    IS '所属科室 ID（科室范围发布映射包时使用）';
COMMENT ON COLUMN local_term.status           IS '状态：UNMAPPED 未映射 / MAPPED 已映射 / DISABLED 已禁用';
COMMENT ON COLUMN local_term.first_seen_at    IS '首次出现时间（来源系统埋点）';
COMMENT ON COLUMN local_term.last_seen_at     IS '最近一次出现时间';

COMMENT ON TABLE term_mapping IS '本地术语→标准术语正式映射：候选确认后落库；仅 status=CONFIRMED 参与术语包构建';
COMMENT ON COLUMN term_mapping.tenant_id        IS '租户 ID';
COMMENT ON COLUMN term_mapping.local_term_id    IS '院内本地术语外键 → local_term.id';
COMMENT ON COLUMN term_mapping.standard_term_id IS '标准术语外键 → standard_term.id';
COMMENT ON COLUMN term_mapping.source_system    IS '来源系统标识（冗余自 local_term，便于按来源过滤）';
COMMENT ON COLUMN term_mapping.category         IS '术语分类（冗余自 local_term/standard_term）';
COMMENT ON COLUMN term_mapping.confidence       IS '置信度 0~1';
COMMENT ON COLUMN term_mapping.risk_level       IS '风险等级：LOW 低 / MEDIUM 中 / HIGH 高';
COMMENT ON COLUMN term_mapping.status           IS '状态：DRAFT 草稿 / CONFIRMED 已确认 / SUPERSEDED 被替换 / ROLLED_BACK 已回滚';
COMMENT ON COLUMN term_mapping.evidence_text    IS '确认证据';
COMMENT ON COLUMN term_mapping.confirmed_by     IS '确认人 user_id';
COMMENT ON COLUMN term_mapping.confirmed_at     IS '确认时间';

COMMENT ON TABLE mapping_candidate IS '映射候选项：规则/AI/人工/导入产生，待审核升级为 term_mapping';
COMMENT ON COLUMN mapping_candidate.tenant_id        IS '租户 ID';
COMMENT ON COLUMN mapping_candidate.local_term_id    IS '院内本地术语外键 → local_term.id';
COMMENT ON COLUMN mapping_candidate.standard_term_id IS '候选标准术语外键 → standard_term.id';
COMMENT ON COLUMN mapping_candidate.confidence       IS '置信度 0~1';
COMMENT ON COLUMN mapping_candidate.candidate_source IS '候选来源：RULE 规则 / AI 模型 / MANUAL 人工 / IMPORT 批量导入';
COMMENT ON COLUMN mapping_candidate.risk_level       IS '风险等级：LOW / MEDIUM / HIGH';
COMMENT ON COLUMN mapping_candidate.evidence_text    IS '候选证据 / 命中规则说明';
COMMENT ON COLUMN mapping_candidate.conflict_flag    IS '是否冲突候选（FALSE 否 / TRUE 是）';
COMMENT ON COLUMN mapping_candidate.status           IS '审核状态：PENDING 待审核 / CONFIRMED 已确认 / REJECTED 已驳回 / EXPIRED 已过期';
COMMENT ON COLUMN mapping_candidate.review_note      IS '审核备注';
COMMENT ON COLUMN mapping_candidate.reviewed_by      IS '审核人 user_id';
COMMENT ON COLUMN mapping_candidate.reviewed_at      IS '审核时间';

COMMENT ON TABLE mapping_conflict IS '术语映射冲突记录：一对多 / 多对一 / 跨体系不一致 / 同名异义等场景；按租户隔离，等待人工处置';
COMMENT ON COLUMN mapping_conflict.tenant_id        IS '租户 ID';
COMMENT ON COLUMN mapping_conflict.conflict_type    IS '冲突类型：ONE_TO_MANY 一对多 / MANY_TO_ONE 多对一 / DISABLED_CODE 标准码已禁用 / CROSS_SYSTEM_INCONSISTENT 跨体系不一致 / HOMONYM 同名异义 / SYNONYM_MISMATCH 同义不匹配';
COMMENT ON COLUMN mapping_conflict.local_term_id    IS '关联本地术语 → local_term.id（可空）';
COMMENT ON COLUMN mapping_conflict.standard_term_id IS '关联标准术语 → standard_term.id（可空）';
COMMENT ON COLUMN mapping_conflict.mapping_id       IS '关联正式映射 → term_mapping.id（可空）';
COMMENT ON COLUMN mapping_conflict.risk_level       IS '风险等级：LOW / MEDIUM / HIGH';
COMMENT ON COLUMN mapping_conflict.description      IS '冲突描述';
COMMENT ON COLUMN mapping_conflict.status           IS '处置状态：OPEN 未处置 / RESOLVED 已处置 / IGNORED 已忽略';
COMMENT ON COLUMN mapping_conflict.resolved_by      IS '处置人 user_id';
COMMENT ON COLUMN mapping_conflict.resolved_at      IS '处置时间';
COMMENT ON COLUMN mapping_conflict.resolution_note  IS '处置说明';
