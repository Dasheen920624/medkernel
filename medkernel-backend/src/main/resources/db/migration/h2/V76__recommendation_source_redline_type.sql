-- MedKernel v1.0 GA · OPT-04 推荐来源支持临床安全红线（H2 PostgreSQL 兼容模式）
-- ROLLBACK: 回退前确认 recommendation_source 中 source_type='REDLINE' 的来源已随审计证据归档；随后恢复 ck_rec_source_type 为不含 REDLINE 的枚举。

ALTER TABLE recommendation_source DROP CONSTRAINT IF EXISTS ck_rec_source_type;
ALTER TABLE recommendation_source ADD CONSTRAINT ck_rec_source_type
    CHECK (source_type IN (
        'RULE','REDLINE','PATHWAY','KNOWLEDGE','CONTEXT','TERMINOLOGY','MANUAL'
    ));

COMMENT ON COLUMN recommendation_source.source_type IS '来源类型：RULE 规则命中 / REDLINE 临床安全红线 / PATHWAY 路径节点 / KNOWLEDGE 知识引用 / CONTEXT 上下文事实 / TERMINOLOGY 术语映射 / MANUAL 人工录入';
