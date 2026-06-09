-- MedKernel v1.0 GA · GA-ENG-API-04 字典映射 + 映射包发布 baseline (Oracle 23ai)

CREATE TABLE standard_term (
    id                 NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          VARCHAR2(64)  NOT NULL,
    standard_system    VARCHAR2(64)  NOT NULL,
    term_code          VARCHAR2(128) NOT NULL,
    category           VARCHAR2(32)  NOT NULL,
    display_name       VARCHAR2(512) NOT NULL,
    normalized_name    VARCHAR2(512) NULL,
    version_no         VARCHAR2(64)  DEFAULT '1' NOT NULL,
    status             VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    source_version_id  NUMBER(19)    NULL,
    evidence_text      VARCHAR2(1024) NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_standard_term_code UNIQUE (tenant_id, standard_system, term_code, version_no),
    CONSTRAINT ck_standard_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_standard_term_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE INDEX idx_standard_term_tenant_category ON standard_term (tenant_id, category);
CREATE INDEX idx_standard_term_tenant_updated ON standard_term (tenant_id, updated_at);

CREATE TABLE local_term (
    id               NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR2(64)  NOT NULL,
    source_system    VARCHAR2(64)  NOT NULL,
    local_code       VARCHAR2(128) NOT NULL,
    category         VARCHAR2(32)  NOT NULL,
    local_name       VARCHAR2(512) NOT NULL,
    normalized_name  VARCHAR2(512) NULL,
    department_id    VARCHAR2(64)  NULL,
    status           VARCHAR2(32)  DEFAULT 'UNMAPPED' NOT NULL,
    first_seen_at    TIMESTAMP WITH TIME ZONE NULL,
    last_seen_at     TIMESTAMP WITH TIME ZONE NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_local_term_code UNIQUE (tenant_id, source_system, local_code, category),
    CONSTRAINT ck_local_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_local_term_status CHECK (status IN ('UNMAPPED','MAPPED','DISABLED'))
);
CREATE INDEX idx_local_term_tenant_source ON local_term (tenant_id, source_system, status);
CREATE INDEX idx_local_term_department ON local_term (tenant_id, department_id);

CREATE TABLE term_mapping (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    local_term_id     NUMBER(19)    NOT NULL,
    standard_term_id  NUMBER(19)    NOT NULL,
    source_system     VARCHAR2(64)  NULL,
    category          VARCHAR2(32)  NULL,
    confidence        BINARY_DOUBLE NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    evidence_text     VARCHAR2(1024) NULL,
    confirmed_by      VARCHAR2(64)  NULL,
    confirmed_at      TIMESTAMP WITH TIME ZONE NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_term_mapping_status CHECK (status IN ('DRAFT','CONFIRMED','SUPERSEDED','ROLLED_BACK')),
    CONSTRAINT ck_term_mapping_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX idx_term_mapping_tenant_status ON term_mapping (tenant_id, status);
CREATE INDEX idx_term_mapping_local_standard ON term_mapping (tenant_id, local_term_id, standard_term_id);

CREATE TABLE mapping_candidate (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    local_term_id     NUMBER(19)    NOT NULL,
    standard_term_id  NUMBER(19)    NOT NULL,
    confidence        BINARY_DOUBLE NULL,
    candidate_source  VARCHAR2(32)  DEFAULT 'RULE' NOT NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    evidence_text     VARCHAR2(1024) NULL,
    conflict_flag     NUMBER(1)     DEFAULT 0 NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'PENDING' NOT NULL,
    review_note       VARCHAR2(500) NULL,
    reviewed_by       VARCHAR2(64)  NULL,
    reviewed_at       TIMESTAMP WITH TIME ZONE NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_mapping_candidate_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    CONSTRAINT ck_mapping_candidate_source CHECK (candidate_source IN ('RULE','AI','MANUAL','IMPORT')),
    CONSTRAINT ck_mapping_candidate_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_mapping_candidate_conflict CHECK (conflict_flag IN (0,1))
);
CREATE INDEX idx_mapping_candidate_tenant_status ON mapping_candidate (tenant_id, status, risk_level);

CREATE TABLE mapping_conflict (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    conflict_type     VARCHAR2(32)  NOT NULL,
    local_term_id     NUMBER(19)    NULL,
    standard_term_id  NUMBER(19)    NULL,
    mapping_id        NUMBER(19)    NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    description       VARCHAR2(1024) NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'OPEN' NOT NULL,
    resolved_by       VARCHAR2(64)  NULL,
    resolved_at       TIMESTAMP WITH TIME ZONE NULL,
    resolution_note   VARCHAR2(500) NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_mapping_conflict_type CHECK (conflict_type IN
        ('ONE_TO_MANY','MANY_TO_ONE','DISABLED_CODE','CROSS_SYSTEM_INCONSISTENT','HOMONYM','SYNONYM_MISMATCH')),
    CONSTRAINT ck_mapping_conflict_status CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    CONSTRAINT ck_mapping_conflict_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX idx_mapping_conflict_tenant_status ON mapping_conflict (tenant_id, status, risk_level);

CREATE TABLE term_mapping_package (
    id                       NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                VARCHAR2(64)  NOT NULL,
    package_code             VARCHAR2(128) NOT NULL,
    package_version          VARCHAR2(64)  NOT NULL,
    display_name             VARCHAR2(256) NOT NULL,
    scope_level              VARCHAR2(32)  NOT NULL,
    scope_code               VARCHAR2(64)  NOT NULL,
    status                   VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    mapping_count            NUMBER(10)    DEFAULT 0 NOT NULL,
    content_hash             VARCHAR2(128) NOT NULL,
    published_by             VARCHAR2(64)  NULL,
    published_at             TIMESTAMP WITH TIME ZONE NULL,
    rollback_from_package_id NUMBER(19)    NULL,
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by               VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by               VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_term_mapping_package UNIQUE (tenant_id, package_code, package_version, scope_level, scope_code),
    CONSTRAINT ck_term_mapping_package_status CHECK (status IN ('DRAFT','GRAY','PUBLISHED','SUPERSEDED','ROLLED_BACK','ARCHIVED'))
);
CREATE INDEX idx_term_pkg_tenant_status ON term_mapping_package (tenant_id, status);
CREATE INDEX idx_term_pkg_scope ON term_mapping_package (tenant_id, package_code, scope_level, scope_code);

CREATE TABLE term_mapping_package_item (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    package_id        NUMBER(19)    NULL,
    mapping_id        NUMBER(19)    NOT NULL,
    local_term_id     NUMBER(19)    NOT NULL,
    standard_term_id  NUMBER(19)    NOT NULL,
    source_system     VARCHAR2(64)  NOT NULL,
    local_code        VARCHAR2(128) NOT NULL,
    target_dictionary_key VARCHAR2(128) NOT NULL,
    standard_code     VARCHAR2(128) NOT NULL,
    category          VARCHAR2(32)  NULL,
    mapping_snapshot  VARCHAR2(4000) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL
);
CREATE INDEX idx_term_pkg_item_package ON term_mapping_package_item (tenant_id, package_id);
CREATE INDEX idx_term_pkg_item_anchor
    ON term_mapping_package_item (tenant_id, source_system, local_code, target_dictionary_key, category);

CREATE TABLE term_mapping_package_release (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    package_id        NUMBER(19)    NOT NULL,
    target_package_id NUMBER(19)    NULL,
    event_type        VARCHAR2(32)  NOT NULL,
    release_mode      VARCHAR2(16)  NOT NULL,
    reason            VARCHAR2(500) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_term_pkg_release_event CHECK (event_type IN ('PUBLISH','ROLLBACK')),
    CONSTRAINT ck_term_pkg_release_mode CHECK (release_mode IN ('GRAY','FULL'))
);
CREATE INDEX idx_term_pkg_release_package ON term_mapping_package_release (tenant_id, package_id, created_at);

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
COMMENT ON COLUMN mapping_candidate.conflict_flag    IS '是否冲突候选（0 否 / 1 是）';
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

COMMENT ON TABLE term_mapping_package IS '术语映射包版本：把 CONFIRMED 映射打包发布到指定组织作用域；(tenant_id, package_code, package_version, scope_level, scope_code) 唯一';
COMMENT ON COLUMN term_mapping_package.tenant_id                IS '租户 ID';
COMMENT ON COLUMN term_mapping_package.package_code             IS '包业务编码';
COMMENT ON COLUMN term_mapping_package.package_version          IS '包版本号';
COMMENT ON COLUMN term_mapping_package.display_name             IS '包展示名';
COMMENT ON COLUMN term_mapping_package.scope_level              IS '组织作用域层级：HOSPITAL / DEPARTMENT 等';
COMMENT ON COLUMN term_mapping_package.scope_code               IS '组织作用域编码（医院或科室 ID）';
COMMENT ON COLUMN term_mapping_package.status                   IS '生命周期：DRAFT 草稿 / GRAY 灰度发布中 / PUBLISHED 全量发布 / SUPERSEDED 被替换 / ROLLED_BACK 已回滚 / ARCHIVED 已归档';
COMMENT ON COLUMN term_mapping_package.mapping_count            IS '包内 CONFIRMED 映射条数';
COMMENT ON COLUMN term_mapping_package.content_hash             IS '包内容 SHA-256 摘要，构包时定格';
COMMENT ON COLUMN term_mapping_package.published_by             IS '发布人 user_id';
COMMENT ON COLUMN term_mapping_package.published_at             IS '发布时间';
COMMENT ON COLUMN term_mapping_package.rollback_from_package_id IS '回滚来源包 → term_mapping_package.id（可空）';

COMMENT ON TABLE term_mapping_package_item IS '术语映射包内单条映射快照：构包时定格，保留 mapping_snapshot 不可变 JSON';
COMMENT ON COLUMN term_mapping_package_item.tenant_id        IS '租户 ID';
COMMENT ON COLUMN term_mapping_package_item.package_id       IS '所属术语映射包 → term_mapping_package.id';
COMMENT ON COLUMN term_mapping_package_item.mapping_id       IS '关联正式映射 → term_mapping.id';
COMMENT ON COLUMN term_mapping_package_item.local_term_id    IS '构包时冻结的院内术语 ID';
COMMENT ON COLUMN term_mapping_package_item.standard_term_id IS '构包时冻结的标准术语 ID';
COMMENT ON COLUMN term_mapping_package_item.source_system    IS '构包时冻结的院内来源系统';
COMMENT ON COLUMN term_mapping_package_item.local_code       IS '构包时冻结的院内编码';
COMMENT ON COLUMN term_mapping_package_item.target_dictionary_key IS '构包时冻结的目标标准字典';
COMMENT ON COLUMN term_mapping_package_item.standard_code    IS '构包时冻结的标准编码';
COMMENT ON COLUMN term_mapping_package_item.category         IS '构包时冻结的术语分类';
COMMENT ON COLUMN term_mapping_package_item.mapping_snapshot IS '映射 JSON 快照（构包时不可变）';

COMMENT ON TABLE term_mapping_package_release IS '术语映射包发布事件流水：PUBLISH / ROLLBACK 仅追加写入，用于审计与发布链路重建';
COMMENT ON COLUMN term_mapping_package_release.tenant_id         IS '租户 ID';
COMMENT ON COLUMN term_mapping_package_release.package_id        IS '当前操作包 → term_mapping_package.id';
COMMENT ON COLUMN term_mapping_package_release.target_package_id IS '目标包 → term_mapping_package.id（仅 ROLLBACK 事件使用）';
COMMENT ON COLUMN term_mapping_package_release.event_type        IS '事件类型：PUBLISH 发布 / ROLLBACK 回滚';
COMMENT ON COLUMN term_mapping_package_release.release_mode      IS '发布模式：GRAY 灰度 / FULL 全量';
COMMENT ON COLUMN term_mapping_package_release.reason            IS '发布或回滚原因（必填留痕）';
