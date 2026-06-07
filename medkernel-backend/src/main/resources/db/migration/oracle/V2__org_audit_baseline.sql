-- MedKernel v1.0 GA · Oracle 23ai baseline schema
-- 引擎一切之根：组织树 + 专病横切维度 + 闭包查询 + 审计留痕。

CREATE TABLE org_unit (
    id              VARCHAR2(26)  PRIMARY KEY,
    parent_id       VARCHAR2(26)  NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    org_path        VARCHAR2(1024) NOT NULL,
    level_code      VARCHAR2(32)  NOT NULL,
    code            VARCHAR2(128) NOT NULL,
    name            VARCHAR2(256) NOT NULL,
    name_pinyin     VARCHAR2(256) NULL,
    specialty_id    VARCHAR2(64)  NULL,
    status          VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_org_unit_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_org_unit_level CHECK (level_code IN ('TENANT','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT')),
    CONSTRAINT ck_org_unit_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

CREATE INDEX idx_org_unit_parent ON org_unit (parent_id);
CREATE INDEX idx_org_unit_tenant_lv ON org_unit (tenant_id, level_code);
CREATE INDEX idx_org_unit_path ON org_unit (tenant_id, org_path);

COMMENT ON TABLE org_unit IS '组织树：tenant/group/hospital/campus/site/department，专病通过 specialty_id 横切表达';
COMMENT ON COLUMN org_unit.id IS '组织节点字符串主键（ULID 形态）';
COMMENT ON COLUMN org_unit.parent_id IS '父级组织节点 ID；租户根为空';
COMMENT ON COLUMN org_unit.org_path IS '组织路径，按组织编码拼接，用于前缀过滤和可读审计';
COMMENT ON COLUMN org_unit.level_code IS '层级；Oracle 关键字 LEVEL 改名为 level_code';

CREATE TABLE org_closure (
    tenant_id       VARCHAR2(64)  NOT NULL,
    ancestor_id     VARCHAR2(26)  NOT NULL,
    descendant_id   VARCHAR2(26)  NOT NULL,
    depth           NUMBER(10)    NOT NULL,
    CONSTRAINT pk_org_closure PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    CONSTRAINT fk_org_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_closure_descendant FOREIGN KEY (descendant_id) REFERENCES org_unit (id) ON DELETE CASCADE,
    CONSTRAINT ck_org_closure_depth CHECK (depth >= 0)
);

CREATE INDEX idx_org_closure_ancestor ON org_closure (tenant_id, ancestor_id, depth);
CREATE INDEX idx_org_closure_descendant ON org_closure (tenant_id, descendant_id, depth);

COMMENT ON TABLE org_closure IS '组织闭包表：保存祖先与后代关系，支撑祖先链和后代集查询';
COMMENT ON COLUMN org_closure.tenant_id IS '租户 ID';
COMMENT ON COLUMN org_closure.ancestor_id IS '祖先组织节点 ID';
COMMENT ON COLUMN org_closure.descendant_id IS '后代组织节点 ID';
COMMENT ON COLUMN org_closure.depth IS '祖先到后代的层级距离；自身为 0';

CREATE TABLE audit_event (
    id              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        VARCHAR2(64)  NOT NULL,
    trace_id        VARCHAR2(128) NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id   VARCHAR2(64)  NULL,
    action          VARCHAR2(32)  NOT NULL,
    resource_type   VARCHAR2(128) NOT NULL,
    resource_id     VARCHAR2(128) NOT NULL,
    summary         VARCHAR2(512) NULL,
    payload_digest  VARCHAR2(128) NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    hospital_id     VARCHAR2(64)  NULL,
    department_id   VARCHAR2(64)  NULL,
    ip_address      VARCHAR2(64)  NULL,
    user_agent      VARCHAR2(512) NULL,
    signature       VARCHAR2(512) NULL,
    status          VARCHAR2(32)  DEFAULT 'RECORDED' NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_audit_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_audit_event_status CHECK (status IN ('RECORDED','SIGNED','TSA_SIGNED','REJECTED'))
);

CREATE INDEX idx_audit_event_resource ON audit_event (resource_type, resource_id, occurred_at);
CREATE INDEX idx_audit_event_actor    ON audit_event (actor_user_id, occurred_at);
CREATE INDEX idx_audit_event_tenant   ON audit_event (tenant_id, occurred_at);
CREATE INDEX idx_audit_event_trace    ON audit_event (trace_id);

COMMENT ON TABLE audit_event IS '统一审计事件：写操作、审核、发布、运行、反馈、导出、回滚均落库';
