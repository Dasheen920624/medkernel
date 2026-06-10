-- MED-C1：钾/钠高危近似是跨分类医疗安全底线，覆盖 LAB 与 DRUG 等所有术语类别。
-- ROLLBACK：如需回退演练口径，将 MED-C1-K-NA 的 category 改回 'DRUG'。

UPDATE mk_term_high_risk_rule
SET category = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'migration-v114'
WHERE tenant_id = 'SYSTEM'
  AND rule_code = 'MED-C1-K-NA'
  AND category = 'DRUG';
