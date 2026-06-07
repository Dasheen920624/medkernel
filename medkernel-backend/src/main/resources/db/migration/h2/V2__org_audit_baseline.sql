-- MedKernel v1.0 GA · H2 2.2 baseline schema（MODE=PostgreSQL 兼容）
-- 引擎一切之根：组织树 + 专病横切维度 + 闭包查询 + 审计留痕。

CREATE TABLE IF NOT EXISTS org_unit (
    id              VARCHAR(26)  PRIMARY KEY,
    parent_id       VARCHAR(26)  NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    org_path        VARCHAR(1024) NOT NULL,
    level_code      VARCHAR(32)  NOT NULL,
    code            VARCHAR(128) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    name_pinyin     VARCHAR(256) NULL,
    specialty_id    VARCHAR(64)  NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_org_unit_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_org_unit_level CHECK (level_code IN ('TENANT','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT')),
    CONSTRAINT ck_org_unit_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_org_unit_parent ON org_unit (parent_id);
CREATE INDEX IF NOT EXISTS idx_org_unit_tenant_lv ON org_unit (tenant_id, level_code);
CREATE INDEX IF NOT EXISTS idx_org_unit_path ON org_unit (tenant_id, org_path);

CREATE TABLE IF NOT EXISTS org_closure (
    tenant_id       VARCHAR(64)  NOT NULL,
    ancestor_id     VARCHAR(26)  NOT NULL,
    descendant_id   VARCHAR(26)  NOT NULL,
    depth           INT          NOT NULL,
    CONSTRAINT pk_org_closure PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    CONSTRAINT fk_org_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_closure_descendant FOREIGN KEY (descendant_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT ck_org_closure_depth CHECK (depth >= 0)
);

CREATE INDEX IF NOT EXISTS idx_org_closure_ancestor ON org_closure (tenant_id, ancestor_id, depth);
CREATE INDEX IF NOT EXISTS idx_org_closure_descendant ON org_closure (tenant_id, descendant_id, depth);

CREATE TABLE IF NOT EXISTS audit_event (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    trace_id        VARCHAR(128) NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    actor_user_id   VARCHAR(64)  NULL,
    action          VARCHAR(32)  NOT NULL,
    resource_type   VARCHAR(128) NOT NULL,
    resource_id     VARCHAR(128) NOT NULL,
    summary         VARCHAR(512) NULL,
    payload_digest  VARCHAR(128) NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    hospital_id     VARCHAR(64)  NULL,
    department_id   VARCHAR(64)  NULL,
    ip_address      VARCHAR(64)  NULL,
    user_agent      VARCHAR(512) NULL,
    signature       VARCHAR(512) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'RECORDED',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_audit_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_audit_event_status CHECK (status IN ('RECORDED','SIGNED','TSA_SIGNED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_audit_event_resource ON audit_event (resource_type, resource_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor    ON audit_event (actor_user_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_tenant   ON audit_event (tenant_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_trace    ON audit_event (trace_id);
