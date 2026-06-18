-- AIK-STD-14 公域来源治理乐观锁：阻断草稿更新与审批并发覆盖。
ALTER TABLE mk_knowledge_acquisition_source ADD (
    lock_version NUMBER(19) DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN mk_knowledge_acquisition_source.lock_version IS '公域来源治理并发版本号，防止草稿更新、审批和停用相互覆盖';
