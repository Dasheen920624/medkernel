-- MedKernel SYS-03 · 关系库权威源投影同步基线（H2）

CREATE TABLE IF NOT EXISTS mk_projection_sync (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sync_id          VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    target_type      VARCHAR(32)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    source_count     INTEGER      NOT NULL DEFAULT 0,
    projection_count INTEGER      NOT NULL DEFAULT 0,
    source_hash      VARCHAR(64),
    projection_hash  VARCHAR(64),
    message          CLOB,
    started_at       TIMESTAMP    NOT NULL,
    finished_at      TIMESTAMP,
    requested_by     VARCHAR(64)  NOT NULL,
    trace_id         VARCHAR(128),
    CONSTRAINT uk_mk_projection_sync_tenant_sync UNIQUE (tenant_id, sync_id),
    CONSTRAINT ck_mk_projection_sync_target CHECK (target_type IN ('CLINICAL_GRAPH')),
    CONSTRAINT ck_mk_projection_sync_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','NOT_SYNCED'))
);

CREATE INDEX IF NOT EXISTS idx_mk_projection_sync_tenant_target_ts
    ON mk_projection_sync (tenant_id, target_type, started_at);

CREATE INDEX IF NOT EXISTS idx_mk_projection_sync_tenant_status
    ON mk_projection_sync (tenant_id, status);

CREATE TABLE IF NOT EXISTS mk_projection_snapshot (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    target_type       VARCHAR(32)  NOT NULL,
    fact_key          VARCHAR(256) NOT NULL,
    fact_kind         VARCHAR(16)  NOT NULL,
    object_type       VARCHAR(64)  NOT NULL,
    object_id         VARCHAR(128) NOT NULL,
    subject_key       VARCHAR(256),
    predicate_name    VARCHAR(64),
    object_key        VARCHAR(256),
    content_hash      VARCHAR(64)  NOT NULL,
    canonical_payload CLOB         NOT NULL,
    source_updated_at TIMESTAMP,
    synced_at         TIMESTAMP    NOT NULL,
    trace_id          VARCHAR(128),
    CONSTRAINT uk_mk_projection_snapshot_fact UNIQUE (tenant_id, target_type, fact_key),
    CONSTRAINT ck_mk_projection_snapshot_target CHECK (target_type IN ('CLINICAL_GRAPH')),
    CONSTRAINT ck_mk_projection_snapshot_kind CHECK (fact_kind IN ('NODE','EDGE'))
);

CREATE INDEX IF NOT EXISTS idx_mk_projection_snapshot_tenant_target
    ON mk_projection_snapshot (tenant_id, target_type);

COMMENT ON TABLE mk_projection_sync IS 'SYS-03 投影同步任务表，记录从关系库权威源重建派生投影的状态、数量与摘要';
COMMENT ON TABLE mk_projection_snapshot IS 'SYS-03 投影快照表，保存可由关系库重放生成的图投影事实副本';
