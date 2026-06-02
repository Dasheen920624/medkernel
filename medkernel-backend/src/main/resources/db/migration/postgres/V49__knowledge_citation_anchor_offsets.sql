-- MedKernel v1.0 GA · KNOW-01 来源指纹去重与引用锚点偏移（PostgreSQL）
-- ROLLBACK：若需回滚，先确认 source_version 无重复内容指纹且 citation.start_offset/end_offset 未被新证据链使用，再删除 uk_source_version_doc_hash / ck_citation_anchor_offsets 约束与两列。

ALTER TABLE source_version ADD CONSTRAINT uk_source_version_doc_hash UNIQUE (source_document_id, content_hash);

ALTER TABLE citation ADD COLUMN IF NOT EXISTS start_offset INTEGER NULL;
ALTER TABLE citation ADD COLUMN IF NOT EXISTS end_offset INTEGER NULL;

ALTER TABLE citation ADD CONSTRAINT ck_citation_anchor_offsets
    CHECK (
        (start_offset IS NULL OR start_offset >= 0)
        AND (end_offset IS NULL OR end_offset >= 0)
        AND (start_offset IS NULL OR end_offset IS NULL OR end_offset >= start_offset)
    );

COMMENT ON COLUMN citation.start_offset IS '来源片段内引用起始偏移，用于把 Citation 精确定位到 SourceFragment 文本范围';
COMMENT ON COLUMN citation.end_offset IS '来源片段内引用结束偏移，用于把 Citation 精确定位到 SourceFragment 文本范围';
