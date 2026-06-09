-- MedKernel v1.0 GA · GA-ENG-API-04 字典映射 + 映射包发布 baseline (H2 2.2)

CREATE TABLE IF NOT EXISTS standard_term (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_standard_term_code UNIQUE (tenant_id, standard_system, term_code, version_no),
    CONSTRAINT ck_standard_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_standard_term_status CHECK (status IN ('ACTIVE','DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_standard_term_tenant_category ON standard_term (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_standard_term_tenant_updated ON standard_term (tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS local_term (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    source_system    VARCHAR(64)  NOT NULL,
    local_code       VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    local_name       VARCHAR(512) NOT NULL,
    normalized_name  VARCHAR(512) NULL,
    department_id    VARCHAR(64)  NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'UNMAPPED',
    first_seen_at    TIMESTAMP    NULL,
    last_seen_at     TIMESTAMP    NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_local_term_code UNIQUE (tenant_id, source_system, local_code, category),
    CONSTRAINT ck_local_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_local_term_status CHECK (status IN ('UNMAPPED','MAPPED','DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_local_term_tenant_source ON local_term (tenant_id, source_system, status);
CREATE INDEX IF NOT EXISTS idx_local_term_department ON local_term (tenant_id, department_id);

CREATE TABLE IF NOT EXISTS term_mapping (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    confirmed_at      TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_term_mapping_status CHECK (status IN ('DRAFT','CONFIRMED','SUPERSEDED','ROLLED_BACK')),
    CONSTRAINT ck_term_mapping_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_term_mapping_tenant_status ON term_mapping (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_term_mapping_local_standard ON term_mapping (tenant_id, local_term_id, standard_term_id);

CREATE TABLE IF NOT EXISTS mapping_candidate (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    reviewed_at       TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mapping_candidate_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    CONSTRAINT ck_mapping_candidate_source CHECK (candidate_source IN ('RULE','AI','MANUAL','IMPORT')),
    CONSTRAINT ck_mapping_candidate_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_mapping_candidate_tenant_status ON mapping_candidate (tenant_id, status, risk_level);

CREATE TABLE IF NOT EXISTS mapping_conflict (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    conflict_type     VARCHAR(32)  NOT NULL,
    local_term_id     BIGINT       NULL,
    standard_term_id  BIGINT       NULL,
    mapping_id        BIGINT       NULL,
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    description       VARCHAR(1024) NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    resolved_by       VARCHAR(64)  NULL,
    resolved_at       TIMESTAMP    NULL,
    resolution_note   VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mapping_conflict_type CHECK (conflict_type IN
        ('ONE_TO_MANY','MANY_TO_ONE','DISABLED_CODE','CROSS_SYSTEM_INCONSISTENT','HOMONYM','SYNONYM_MISMATCH')),
    CONSTRAINT ck_mapping_conflict_status CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    CONSTRAINT ck_mapping_conflict_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_mapping_conflict_tenant_status ON mapping_conflict (tenant_id, status, risk_level);
