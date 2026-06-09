-- MedKernel v1.0 GA · 受限平台包租户授权（达梦）
-- ROLLBACK：删除 mk_pkg_package_entitlement，移除 knowledge_package.access_policy。

ALTER TABLE knowledge_package ADD access_policy VARCHAR2(32) DEFAULT 'OPEN' NOT NULL;
ALTER TABLE knowledge_package
    ADD CONSTRAINT ck_knowledge_package_access_policy
    CHECK (access_policy IN ('OPEN','ENTITLED'));

CREATE TABLE mk_pkg_package_entitlement (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    entitlement_id      VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    platform_tenant_id  VARCHAR2(64)  NOT NULL,
    platform_package_id VARCHAR2(64)  NOT NULL,
    package_identity    VARCHAR2(256) NOT NULL,
    status              VARCHAR2(32)  NOT NULL,
    granted_at          TIMESTAMP     NOT NULL,
    expires_at          TIMESTAMP     NOT NULL,
    reason              CLOB          NOT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_package_entitlement_id UNIQUE (entitlement_id),
    CONSTRAINT uk_package_entitlement_tenant_package UNIQUE (tenant_id, platform_package_id),
    CONSTRAINT fk_package_entitlement_platform_package
        FOREIGN KEY (platform_tenant_id, platform_package_id)
        REFERENCES knowledge_package (tenant_id, package_id),
    CONSTRAINT ck_package_entitlement_status CHECK (status IN ('GRANTED','REVOKED')),
    CONSTRAINT ck_package_entitlement_expiry CHECK (expires_at > granted_at)
);

CREATE INDEX idx_package_entitlement_package_status
    ON mk_pkg_package_entitlement (platform_package_id, status, expires_at);
CREATE INDEX idx_package_entitlement_tenant_status
    ON mk_pkg_package_entitlement (tenant_id, status, expires_at);

COMMENT ON COLUMN knowledge_package.access_policy IS '平台包访问策略：OPEN 开放 / ENTITLED 按租户授权';
COMMENT ON TABLE mk_pkg_package_entitlement IS '受限平台知识包的租户授权事实';
COMMENT ON COLUMN mk_pkg_package_entitlement.entitlement_id IS '授权业务 ID';
COMMENT ON COLUMN mk_pkg_package_entitlement.tenant_id IS '获授权客户租户 ID';
COMMENT ON COLUMN mk_pkg_package_entitlement.platform_tenant_id IS '平台知识包归属租户 ID';
COMMENT ON COLUMN mk_pkg_package_entitlement.platform_package_id IS '受限平台知识包业务 ID';
COMMENT ON COLUMN mk_pkg_package_entitlement.package_identity IS '平台知识包稳定身份，格式为编码@版本';
COMMENT ON COLUMN mk_pkg_package_entitlement.status IS '授权状态：GRANTED 已授权 / REVOKED 已撤销';
COMMENT ON COLUMN mk_pkg_package_entitlement.granted_at IS '本次授权或续期生效时间';
COMMENT ON COLUMN mk_pkg_package_entitlement.expires_at IS '授权到期时间';
COMMENT ON COLUMN mk_pkg_package_entitlement.reason IS '授权、续期或撤销原因';
COMMENT ON COLUMN mk_pkg_package_entitlement.created_at IS '首次创建时间';
COMMENT ON COLUMN mk_pkg_package_entitlement.created_by IS '首次创建人';
COMMENT ON COLUMN mk_pkg_package_entitlement.updated_at IS '最近更新时间';
COMMENT ON COLUMN mk_pkg_package_entitlement.updated_by IS '最近更新人';
COMMENT ON COLUMN mk_pkg_package_entitlement.trace_id IS '链路追踪 ID';
