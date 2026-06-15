-- MedKernel 第二阶段 P2-B · LLM-06 可信来源探索运行存证（H2）
-- 受控来源确定性探索（B0）的检索时点存证：记录检索时点 + 当时源版本快照 + 结果集真实指纹，复查「当时看到什么」。
-- ROLLBACK：确认无引用后 DROP TABLE mk_knowledge_discovery_run。

CREATE TABLE IF NOT EXISTS mk_knowledge_discovery_run (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    run_code         VARCHAR(64)   NOT NULL,
    query_text       VARCHAR(512)  NOT NULL,
    capability_code  VARCHAR(64)   NOT NULL,
    executed_at      TIMESTAMP     NOT NULL,
    source_snapshot  VARCHAR(4000) NOT NULL,
    hit_count        INTEGER       NOT NULL DEFAULT 0,
    candidate_count  INTEGER       NOT NULL DEFAULT 0,
    result_hash      VARCHAR(64)   NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    degraded         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(64)   NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mk_knowledge_discovery_run_code UNIQUE (run_code),
    CONSTRAINT ck_mk_knowledge_discovery_run_status CHECK (status IN ('SUCCEEDED', 'EMPTY', 'DEGRADED'))
);

CREATE INDEX idx_mk_knowledge_discovery_run_tenant ON mk_knowledge_discovery_run (tenant_id, executed_at);
