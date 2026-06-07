-- MedKernel v1.0 GA · GA-ENG-BASE-04 · 审计哈希链基线（达梦 DM8 Oracle 兼容模式）
-- 在 V2 已建好的 audit_event 上新增哈希链前驱列；新增 audit_chain_head 作为每租户链头锁锚点。
-- DM 兼容 Oracle 但不支持 ADD COLUMN IF NOT EXISTS，沿用 Oracle 的 PL/SQL 探测。

DECLARE
    col_exists INT;
BEGIN
    SELECT COUNT(*) INTO col_exists FROM user_tab_columns
        WHERE table_name = 'AUDIT_EVENT' AND column_name = 'PREV_EVENT_ID';
    IF col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE audit_event ADD prev_event_id VARCHAR2(64) NULL';
    END IF;

    SELECT COUNT(*) INTO col_exists FROM user_tab_columns
        WHERE table_name = 'AUDIT_EVENT' AND column_name = 'PREV_SIGNATURE';
    IF col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE audit_event ADD prev_signature VARCHAR2(512) NULL';
    END IF;
END;
/

CREATE TABLE audit_chain_head (
    tenant_id      VARCHAR2(64)  PRIMARY KEY,
    last_event_id  VARCHAR2(64)  NULL,
    last_signature VARCHAR2(512) NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE audit_chain_head IS '每租户审计哈希链链头';
