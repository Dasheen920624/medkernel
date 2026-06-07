-- MedKernel v1.0 GA · PostgreSQL Flyway baseline
-- 真实 schema 将在 GA-CORE-03 / GA-TENANT-01 等业务域任务中逐步加入
-- 本文件作为 Flyway 探测点的占位，确保启动不报"empty migration directory"

CREATE TABLE IF NOT EXISTS medkernel_meta (
    id          BIGSERIAL PRIMARY KEY,
    schema_ver  VARCHAR(32) NOT NULL,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note        TEXT
);

COMMENT ON TABLE medkernel_meta IS '数据库迁移基线元数据';

INSERT INTO medkernel_meta (schema_ver, note)
VALUES ('1.0.0-baseline', 'GA-CORE-03 baseline placeholder');
