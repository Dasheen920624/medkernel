-- MedKernel P0-3 · 结构化过敏资源类型（Oracle）
-- ROLLBACK: 若需回滚，先确认 canonical_resource 中无 ALLERGY_INTOLERANCE 数据，再恢复旧资源类型约束。

ALTER TABLE canonical_resource DROP CONSTRAINT ck_canonical_resource_type;
ALTER TABLE canonical_resource ADD CONSTRAINT ck_canonical_resource_type CHECK (resource_type IN (
    'PATIENT','ALLERGY_INTOLERANCE','ENCOUNTER','CONDITION','NURSING_ASSESSMENT','OBSERVATION',
    'DIAGNOSTIC_REPORT','MEDICATION','PROCEDURE','DOCUMENT','CARE_PLAN','FOLLOW_UP','CLAIM'
));
