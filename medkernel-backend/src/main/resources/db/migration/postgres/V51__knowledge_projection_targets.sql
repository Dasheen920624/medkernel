-- MedKernel v1.0 GA · KNOW-01/SYS-03 知识图与搜索投影目标（PostgreSQL）
-- ROLLBACK：确认无 KNOWLEDGE_GRAPH / KNOWLEDGE_SEARCH 投影记录后，恢复仅 CLINICAL_GRAPH 的目标约束。

ALTER TABLE mk_projection_sync DROP CONSTRAINT ck_mk_projection_sync_target;
ALTER TABLE mk_projection_sync ADD CONSTRAINT ck_mk_projection_sync_target
    CHECK (target_type IN ('CLINICAL_GRAPH','KNOWLEDGE_GRAPH','KNOWLEDGE_SEARCH'));

ALTER TABLE mk_projection_snapshot DROP CONSTRAINT ck_mk_projection_snapshot_target;
ALTER TABLE mk_projection_snapshot ADD CONSTRAINT ck_mk_projection_snapshot_target
    CHECK (target_type IN ('CLINICAL_GRAPH','KNOWLEDGE_GRAPH','KNOWLEDGE_SEARCH'));

COMMENT ON COLUMN mk_projection_sync.target_type IS '投影目标类型：CLINICAL_GRAPH 临床图 / KNOWLEDGE_GRAPH 知识图 / KNOWLEDGE_SEARCH 知识搜索';
COMMENT ON COLUMN mk_projection_snapshot.target_type IS '投影快照目标类型：CLINICAL_GRAPH 临床图 / KNOWLEDGE_GRAPH 知识图 / KNOWLEDGE_SEARCH 知识搜索';
