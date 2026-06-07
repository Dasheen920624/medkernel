-- MedKernel v1.0 GA · Oracle 23ai Flyway baseline
-- 真实 schema 将在 GA-CORE-03 / GA-TENANT-01 等任务中加入

CREATE TABLE medkernel_meta (
    id          NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schema_ver  VARCHAR2(32) NOT NULL,
    applied_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    note        CLOB
);

COMMENT ON TABLE medkernel_meta IS '数据库迁移基线元数据';

INSERT INTO medkernel_meta (schema_ver, note)
VALUES ('1.0.0-baseline', 'GA-CORE-03 baseline placeholder');
