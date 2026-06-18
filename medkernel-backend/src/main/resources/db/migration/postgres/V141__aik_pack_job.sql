-- MedKernel 第二阶段 P2-C · AIK-STD-07 知识包装配作业（PostgreSQL）
-- 新项目基线：只记录当前 AIK 装配清单与包引用，不做旧包兼容回填。

CREATE TABLE IF NOT EXISTS mk_aik_pack_job (
    id               BIGSERIAL    PRIMARY KEY,
    job_id           VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    package_id       VARCHAR(64)  NOT NULL,
    package_code     VARCHAR(128) NOT NULL,
    package_version  VARCHAR(64)  NOT NULL,
    item_count       INTEGER      NOT NULL,
    asset_manifest   TEXT         NOT NULL,
    manifest_sha256  VARCHAR(64)  NOT NULL,
    status           VARCHAR(24)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)  NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64)  NULL,
    trace_id         VARCHAR(128) NULL,
    CONSTRAINT uk_aik_pack_job_id UNIQUE (tenant_id, job_id),
    CONSTRAINT uk_aik_pack_job_package UNIQUE (tenant_id, package_id),
    CONSTRAINT fk_aik_pack_job_package
        FOREIGN KEY (tenant_id, package_id) REFERENCES knowledge_package (tenant_id, package_id),
    CONSTRAINT ck_aik_pack_job_status CHECK (status IN ('PACKAGED','FAILED')),
    CONSTRAINT ck_aik_pack_job_count CHECK (item_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_aik_pack_job_package
    ON mk_aik_pack_job (tenant_id, package_code, package_version);
CREATE INDEX IF NOT EXISTS idx_aik_pack_job_status
    ON mk_aik_pack_job (tenant_id, status, updated_at);

COMMENT ON TABLE mk_aik_pack_job IS 'AIK-STD-07 知识包装配作业：记录已审知识资产清单、PKG-01 包引用和清单摘要';
COMMENT ON COLUMN mk_aik_pack_job.job_id IS 'AIK 装配作业唯一编码';
COMMENT ON COLUMN mk_aik_pack_job.package_id IS '装配形成的 PKG-01 知识包 ID';
COMMENT ON COLUMN mk_aik_pack_job.asset_manifest IS '入包知识资产清单 JSON，包含身份、版本和内容指纹';
COMMENT ON COLUMN mk_aik_pack_job.manifest_sha256 IS '资产清单 SHA-256 摘要';
COMMENT ON COLUMN mk_aik_pack_job.status IS '装配作业状态：已打包或失败';
