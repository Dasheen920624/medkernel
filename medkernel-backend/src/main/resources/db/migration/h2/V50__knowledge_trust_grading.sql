-- MedKernel v1.0 GA · KNOW-01/OPT-07 可信分级与冲突仲裁（H2）
-- ROLLBACK：若需回滚，先确认无 A-E 分级与 GRADE 字段被新版发布链使用，再删除新增约束 / 索引 / 字段并恢复旧 ck_source_document_authority。

ALTER TABLE source_document ADD COLUMN IF NOT EXISTS authority_basis VARCHAR(1024) NULL;
UPDATE source_document
SET authority_basis = 'V50 迁移保留：依据来自旧 authority_level 与 source_type 映射，需后续人工补充原始文件编号'
WHERE authority_basis IS NULL;

ALTER TABLE source_document DROP CONSTRAINT IF EXISTS ck_source_document_authority;
UPDATE source_document SET authority_level = CASE
    WHEN authority_level = 'CHINA_NATIONAL' AND source_type IN ('POLICY', 'STANDARD', 'DRUG_LABEL') THEN 'A_REGULATION'
    WHEN authority_level IN ('CHINA_NATIONAL', 'INTERNATIONAL') THEN 'B_GUIDELINE'
    WHEN authority_level = 'SOCIETY' THEN 'C_CONSENSUS_LITERATURE'
    WHEN authority_level = 'HOSPITAL' THEN 'D_HOSPITAL'
    ELSE 'E_FEEDBACK'
END;
ALTER TABLE source_document ADD CONSTRAINT ck_source_document_authority
    CHECK (authority_level IN ('A_REGULATION','B_GUIDELINE','C_CONSENSUS_LITERATURE','D_HOSPITAL','E_FEEDBACK'));

ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS authority_level VARCHAR(32) NULL;
ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS grade_quality VARCHAR(16) NULL;
ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS grade_strength VARCHAR(16) NULL;
ALTER TABLE knowledge_asset_version ADD COLUMN IF NOT EXISTS conflict_arbitration VARCHAR(2048) NULL;

UPDATE knowledge_asset_version
SET authority_level = (
    SELECT source_document.authority_level
    FROM source_document
    WHERE source_document.id = knowledge_asset_version.source_document_id
)
WHERE authority_level IS NULL
  AND source_document_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM source_document WHERE source_document.id = knowledge_asset_version.source_document_id);

ALTER TABLE knowledge_asset_version ADD CONSTRAINT ck_knowledge_asset_version_authority
    CHECK (authority_level IS NULL OR authority_level IN ('A_REGULATION','B_GUIDELINE','C_CONSENSUS_LITERATURE','D_HOSPITAL','E_FEEDBACK'));
ALTER TABLE knowledge_asset_version ADD CONSTRAINT ck_knowledge_asset_grade_quality
    CHECK (grade_quality IS NULL OR grade_quality IN ('HIGH','MODERATE','LOW','VERY_LOW'));
ALTER TABLE knowledge_asset_version ADD CONSTRAINT ck_knowledge_asset_grade_strength
    CHECK (grade_strength IS NULL OR grade_strength IN ('STRONG','WEAK'));

CREATE INDEX IF NOT EXISTS idx_knowledge_av_authority ON knowledge_asset_version (tenant_id, authority_level);

COMMENT ON COLUMN source_document.authority_basis IS '来源可信分级判定依据，记录法规文号、指南发布机构、共识出处或院内制度编号';
COMMENT ON COLUMN knowledge_asset_version.authority_level IS '知识资产版本发布时快照的 A-E 来源可信分级';
COMMENT ON COLUMN knowledge_asset_version.grade_quality IS 'GRADE 证据质量：HIGH 高 / MODERATE 中 / LOW 低 / VERY_LOW 极低';
COMMENT ON COLUMN knowledge_asset_version.grade_strength IS 'GRADE 推荐强度：STRONG 强推荐 / WEAK 弱推荐';
COMMENT ON COLUMN knowledge_asset_version.conflict_arbitration IS '版本替换时的可信分级冲突裁决摘要，用于低阶覆盖高阶留证';
