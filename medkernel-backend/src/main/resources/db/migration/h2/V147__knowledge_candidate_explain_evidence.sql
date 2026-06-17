ALTER TABLE mk_knowledge_production_candidate
    ADD COLUMN IF NOT EXISTS explain_json CLOB NULL;

COMMENT ON COLUMN mk_knowledge_production_candidate.explain_json IS '候选生产解释元数据JSON：仅保存模型任务ID、模式、版本三元组、来源引用、置信和降级原因，不保存提示词原文或候选正文';
