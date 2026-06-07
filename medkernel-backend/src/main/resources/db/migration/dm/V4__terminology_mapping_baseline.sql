-- MedKernel v1.0 GA · GA-ENG-API-04 字典映射 + 映射包发布 baseline (达梦 8)

CREATE TABLE standard_term (
    id                 NUMBER(19)    IDENTITY PRIMARY KEY,
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
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_standard_term_code UNIQUE (tenant_id, standard_system, term_code, version_no),
    CONSTRAINT ck_standard_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_standard_term_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE INDEX idx_standard_term_tenant_category ON standard_term (tenant_id, category);
CREATE INDEX idx_standard_term_tenant_updated ON standard_term (tenant_id, updated_at);

CREATE TABLE local_term (
    id               NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR2(64)  NOT NULL,
    source_system    VARCHAR2(64)  NOT NULL,
    local_code       VARCHAR2(128) NOT NULL,
    category         VARCHAR2(32)  NOT NULL,
    local_name       VARCHAR2(512) NOT NULL,
    normalized_name  VARCHAR2(512) NULL,
    department_id    VARCHAR2(64)  NULL,
    status           VARCHAR2(32)  DEFAULT 'UNMAPPED' NOT NULL,
    first_seen_at    TIMESTAMP     NULL,
    last_seen_at     TIMESTAMP     NULL,
    created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_local_term_code UNIQUE (tenant_id, source_system, local_code, category),
    CONSTRAINT ck_local_term_category CHECK (category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_local_term_status CHECK (status IN ('UNMAPPED','MAPPED','DISABLED'))
);
CREATE INDEX idx_local_term_tenant_source ON local_term (tenant_id, source_system, status);
CREATE INDEX idx_local_term_department ON local_term (tenant_id, department_id);

CREATE TABLE term_mapping (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    local_term_id     NUMBER(19)    NOT NULL,
    standard_term_id  NUMBER(19)    NOT NULL,
    source_system     VARCHAR2(64)  NULL,
    category          VARCHAR2(32)  NULL,
    confidence        DOUBLE        NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    evidence_text     VARCHAR2(1024) NULL,
    confirmed_by      VARCHAR2(64)  NULL,
    confirmed_at      TIMESTAMP     NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_term_mapping_status CHECK (status IN ('DRAFT','CONFIRMED','SUPERSEDED','ROLLED_BACK')),
    CONSTRAINT ck_term_mapping_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX idx_term_mapping_tenant_status ON term_mapping (tenant_id, status);
CREATE INDEX idx_term_mapping_local_standard ON term_mapping (tenant_id, local_term_id, standard_term_id);

CREATE TABLE mapping_candidate (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    local_term_id     NUMBER(19)    NOT NULL,
    standard_term_id  NUMBER(19)    NOT NULL,
    confidence        DOUBLE        NULL,
    candidate_source  VARCHAR2(32)  DEFAULT 'RULE' NOT NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    evidence_text     VARCHAR2(1024) NULL,
    conflict_flag     NUMBER(1)     DEFAULT 0 NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'PENDING' NOT NULL,
    review_note       VARCHAR2(500) NULL,
    reviewed_by       VARCHAR2(64)  NULL,
    reviewed_at       TIMESTAMP     NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_mapping_candidate_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    CONSTRAINT ck_mapping_candidate_source CHECK (candidate_source IN ('RULE','AI','MANUAL','IMPORT')),
    CONSTRAINT ck_mapping_candidate_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_mapping_candidate_conflict CHECK (conflict_flag IN (0,1))
);
CREATE INDEX idx_mapping_candidate_tenant_status ON mapping_candidate (tenant_id, status, risk_level);

CREATE TABLE mapping_conflict (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    conflict_type     VARCHAR2(32)  NOT NULL,
    local_term_id     NUMBER(19)    NULL,
    standard_term_id  NUMBER(19)    NULL,
    mapping_id        NUMBER(19)    NULL,
    risk_level        VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    description       VARCHAR2(1024) NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'OPEN' NOT NULL,
    resolved_by       VARCHAR2(64)  NULL,
    resolved_at       TIMESTAMP     NULL,
    resolution_note   VARCHAR2(500) NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_mapping_conflict_type CHECK (conflict_type IN
        ('ONE_TO_MANY','MANY_TO_ONE','DISABLED_CODE','CROSS_SYSTEM_INCONSISTENT','HOMONYM','SYNONYM_MISMATCH')),
    CONSTRAINT ck_mapping_conflict_status CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    CONSTRAINT ck_mapping_conflict_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX idx_mapping_conflict_tenant_status ON mapping_conflict (tenant_id, status, risk_level);

CREATE TABLE term_mapping_package (
    id                       NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id                VARCHAR2(64)  NOT NULL,
    package_code             VARCHAR2(128) NOT NULL,
    package_version          VARCHAR2(64)  NOT NULL,
    display_name             VARCHAR2(256) NOT NULL,
    scope_level              VARCHAR2(32)  NOT NULL,
    scope_code               VARCHAR2(64)  NOT NULL,
    status                   VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    mapping_count            NUMBER(10)    DEFAULT 0 NOT NULL,
    content_hash             VARCHAR2(128) NOT NULL,
    gray_scope_json          VARCHAR2(2048) NULL,
    published_by             VARCHAR2(64)  NULL,
    published_at             TIMESTAMP     NULL,
    rollback_from_package_id NUMBER(19)    NULL,
    created_at               TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by               VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at               TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by               VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_term_mapping_package UNIQUE (tenant_id, package_code, package_version, scope_level, scope_code),
    CONSTRAINT ck_term_mapping_package_status CHECK (status IN ('DRAFT','GRAY','PUBLISHED','SUPERSEDED','ROLLED_BACK','ARCHIVED'))
);
CREATE INDEX idx_term_pkg_tenant_status ON term_mapping_package (tenant_id, status);
CREATE INDEX idx_term_pkg_scope ON term_mapping_package (tenant_id, package_code, scope_level, scope_code);

CREATE TABLE term_mapping_package_item (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
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
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL
);
CREATE INDEX idx_term_pkg_item_package ON term_mapping_package_item (tenant_id, package_id);
CREATE INDEX idx_term_pkg_item_anchor
    ON term_mapping_package_item (tenant_id, source_system, local_code, target_dictionary_key, category);

CREATE TABLE term_mapping_package_release (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    package_id        NUMBER(19)    NOT NULL,
    target_package_id NUMBER(19)    NULL,
    event_type        VARCHAR2(32)  NOT NULL,
    release_mode      VARCHAR2(16)  NOT NULL,
    reason            VARCHAR2(500) NOT NULL,
    gray_scope_json   VARCHAR2(2048) NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT ck_term_pkg_release_event CHECK (event_type IN ('PUBLISH','ROLLBACK')),
    CONSTRAINT ck_term_pkg_release_mode CHECK (release_mode IN ('GRAY','FULL'))
);
CREATE INDEX idx_term_pkg_release_package ON term_mapping_package_release (tenant_id, package_id, created_at);
