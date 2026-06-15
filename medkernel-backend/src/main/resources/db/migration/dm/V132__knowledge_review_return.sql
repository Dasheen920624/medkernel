-- MedKernel · AIK-STD-12 FR-3 候选退修态（达梦 DM）
-- 放宽知识候选审核 review_status 与 review_assignment.decision 三组 CHECK，加入退修态 RETURNED / RETURN；值名兼容、存量数据不受影响。
-- ROLLBACK：确认无 RETURNED 候选/退修记录后，删除三 CHECK 并恢复各自原值集合。

ALTER TABLE mk_knowledge_candidate_classification DROP CONSTRAINT ck_knowledge_candidate_review_status;
ALTER TABLE mk_knowledge_candidate_classification ADD CONSTRAINT ck_knowledge_candidate_review_status
    CHECK (review_status IN ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED','RETURNED'));

ALTER TABLE mk_knowledge_review_assignment DROP CONSTRAINT ck_review_assignment_review_status;
ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_review_status
    CHECK (review_status IN ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED','RETURNED'));

ALTER TABLE mk_knowledge_review_assignment DROP CONSTRAINT ck_review_assignment_decision;
ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_decision
    CHECK (decision IS NULL OR decision IN ('APPROVE','REJECT','RETURN'));

COMMENT ON COLUMN mk_knowledge_candidate_classification.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / DUPLICATE_SKIPPED 重复跳过 / APPROVED 通过 / REJECTED 拒绝 / RETURNED 退修';
COMMENT ON COLUMN mk_knowledge_review_assignment.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / APPROVED 通过 / REJECTED 拒绝 / RETURNED 退修';
COMMENT ON COLUMN mk_knowledge_review_assignment.decision IS '审核结论：APPROVE 通过 / REJECT 拒绝 / RETURN 退修';
