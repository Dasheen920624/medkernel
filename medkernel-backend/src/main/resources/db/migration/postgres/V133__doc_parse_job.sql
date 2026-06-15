-- MedKernel 第二阶段 P2-C · AIK-STD-02 文档解析 job（PostgreSQL）
-- 文档解析管线生命周期跟踪：源文件 + 格式 + 原文 SHA-256 + 状态 + 解析产物计数；成功后物化进 source_version/source_fragment。
-- ROLLBACK：确认无引用后 DROP TABLE mk_doc_parse_job。

CREATE TABLE IF NOT EXISTS mk_doc_parse_job (
    id                       BIGSERIAL     PRIMARY KEY,
    tenant_id                VARCHAR(64)   NOT NULL,
    job_code                 VARCHAR(64)   NOT NULL,
    source_document_id       BIGINT        NOT NULL,
    source_file_name         VARCHAR(512)  NOT NULL,
    document_format          VARCHAR(24)   NOT NULL,
    source_hash              VARCHAR(64)   NOT NULL,
    status                   VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    result_source_version_id BIGINT        NULL,
    parsed_section_count     INTEGER       NULL,
    parsed_fragment_count    INTEGER       NULL,
    error_message            VARCHAR(1024) NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(64)   NULL,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by               VARCHAR(64)   NULL,
    CONSTRAINT uk_mk_doc_parse_job_code UNIQUE (job_code),
    CONSTRAINT ck_mk_doc_parse_job_format CHECK (document_format IN ('STRUCTURED_TEXT', 'PDF', 'WORD')),
    CONSTRAINT ck_mk_doc_parse_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_mk_doc_parse_job_lookup ON mk_doc_parse_job (tenant_id, source_document_id, status);

COMMENT ON TABLE mk_doc_parse_job IS '文档解析job：解析管线生命周期跟踪，记录源文件与原文指纹及解析产物计数，成功后物化进受控来源版本与片段';
COMMENT ON COLUMN mk_doc_parse_job.source_hash IS '原文字节 SHA-256 指纹，用于版本存证与幂等去重';
COMMENT ON COLUMN mk_doc_parse_job.error_message IS '解析失败诚实原因，禁止失败时产半真片段';
