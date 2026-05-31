-- MedKernel v1.0 GA · GA-ENG-BASE-01/04 baseline schema for PostgreSQL 15+
-- 引擎一切之根：组织七层树 + 闭包查询 + 审计留痕。后续 E1/E2/E3 任务在此基础上累积领域表。

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
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_org_unit_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_org_unit_level CHECK (level_code IN ('TENANT','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT','SPECIALTY')),
    CONSTRAINT ck_org_unit_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_org_unit_parent ON org_unit (parent_id);
CREATE INDEX IF NOT EXISTS idx_org_unit_tenant_lv ON org_unit (tenant_id, level_code);
CREATE INDEX IF NOT EXISTS idx_org_unit_path ON org_unit (tenant_id, org_path);

COMMENT ON TABLE org_unit IS '组织七层树：tenant/group/hospital/campus/site/department/specialty';
COMMENT ON COLUMN org_unit.id IS '组织节点字符串主键（ULID 形态）';
COMMENT ON COLUMN org_unit.parent_id IS '父级组织节点 ID；租户根为空';
COMMENT ON COLUMN org_unit.org_path IS '组织路径，按组织编码拼接，用于前缀过滤和可读审计';
COMMENT ON COLUMN org_unit.level_code IS '组织层级：租户根、集团、医院、院区、服务点、科室、专病';

CREATE TABLE IF NOT EXISTS org_closure (
    tenant_id       VARCHAR(64)  NOT NULL,
    ancestor_id     VARCHAR(26)  NOT NULL,
    descendant_id   VARCHAR(26)  NOT NULL,
    depth           INTEGER      NOT NULL,
    CONSTRAINT pk_org_closure PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    CONSTRAINT fk_org_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_closure_descendant FOREIGN KEY (descendant_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT ck_org_closure_depth CHECK (depth >= 0)
);

CREATE INDEX IF NOT EXISTS idx_org_closure_ancestor ON org_closure (tenant_id, ancestor_id, depth);
CREATE INDEX IF NOT EXISTS idx_org_closure_descendant ON org_closure (tenant_id, descendant_id, depth);

COMMENT ON TABLE org_closure IS '组织闭包表：保存祖先与后代关系，支撑祖先链和后代集查询';
COMMENT ON COLUMN org_closure.tenant_id IS '租户 ID';
COMMENT ON COLUMN org_closure.ancestor_id IS '祖先组织节点 ID';
COMMENT ON COLUMN org_closure.descendant_id IS '后代组织节点 ID';
COMMENT ON COLUMN org_closure.depth IS '祖先到后代的层级距离；自身为 0';

CREATE TABLE IF NOT EXISTS audit_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    trace_id        VARCHAR(128) NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
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
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_audit_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_audit_event_status CHECK (status IN ('RECORDED','SIGNED','TSA_SIGNED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_audit_event_resource ON audit_event (resource_type, resource_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor    ON audit_event (actor_user_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_tenant   ON audit_event (tenant_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_trace    ON audit_event (trace_id);

COMMENT ON TABLE audit_event IS '统一审计事件：写操作、审核、发布、运行、反馈、导出、回滚均落库';
