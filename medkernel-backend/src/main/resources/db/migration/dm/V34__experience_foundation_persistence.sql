-- MedKernel v1.0 GA · BASE-08 产品体验底座持久化（达梦 DM8）

CREATE TABLE mk_experience_saved_view (
    saved_view_id   VARCHAR2(80)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    user_id         VARCHAR2(64)  NOT NULL,
    page_key        VARCHAR2(128) NOT NULL,
    view_name       VARCHAR2(128) NOT NULL,
    definition_json CLOB          NOT NULL,
    default_flag    CHAR(1)       DEFAULT 'N' NOT NULL,
    version         NUMBER(19)    DEFAULT 1 NOT NULL,
    status          VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT pk_saved_view PRIMARY KEY (saved_view_id),
    CONSTRAINT uk_saved_view_user_name UNIQUE (tenant_id, user_id, page_key, view_name),
    CONSTRAINT ck_saved_view_default CHECK (default_flag IN ('Y','N')),
    CONSTRAINT ck_saved_view_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE INDEX idx_saved_view_user_page
    ON mk_experience_saved_view (tenant_id, user_id, page_key, status);

CREATE INDEX idx_saved_view_default
    ON mk_experience_saved_view (tenant_id, user_id, page_key, default_flag, status);

COMMENT ON TABLE mk_experience_saved_view IS '系统保存视图表：按租户、用户和页面保存筛选、列、分页和专家模式配置';
COMMENT ON TABLE mk_experience_export_task IS '系统异步导出任务表：保存大规模列表导出任务、视图快照、物理文件与审计线索';
COMMENT ON COLUMN mk_experience_saved_view.definition_json IS '保存视图定义 JSON，不允许包含患者、令牌、密码等敏感内容';
COMMENT ON COLUMN mk_experience_saved_view.default_flag IS '默认视图标记：Y 默认 / N 非默认';
