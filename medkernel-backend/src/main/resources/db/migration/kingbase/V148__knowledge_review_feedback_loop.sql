ALTER TABLE mk_knowledge_review_assignment
    ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(48) NULL;

ALTER TABLE mk_knowledge_review_assignment
    ADD COLUMN IF NOT EXISTS followup_action VARCHAR(64) NULL;

ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_feedback_type
    CHECK (feedback_type IS NULL OR feedback_type IN ('ACCEPTED','NOT_ADOPTED','CONTENT_GAP','SOURCE_BLANK','FALSE_POSITIVE'));

ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_followup_action
    CHECK (followup_action IS NULL OR followup_action IN ('NONE','CREATE_REVISION_CANDIDATE','REQUEST_SOURCE_EVIDENCE','MARK_FALSE_POSITIVE','ARCHIVE_REJECTED'));

COMMENT ON COLUMN mk_knowledge_review_assignment.feedback_type IS '审核反馈类型：ACCEPTED 采纳 / NOT_ADOPTED 不采纳 / CONTENT_GAP 内容缺口 / SOURCE_BLANK 来源空白 / FALSE_POSITIVE 误报';
COMMENT ON COLUMN mk_knowledge_review_assignment.followup_action IS '审核后回流动作：NONE 无后续 / CREATE_REVISION_CANDIDATE 创建修订候选 / REQUEST_SOURCE_EVIDENCE 补充来源证据 / MARK_FALSE_POSITIVE 标记误报 / ARCHIVE_REJECTED 封存驳回';
