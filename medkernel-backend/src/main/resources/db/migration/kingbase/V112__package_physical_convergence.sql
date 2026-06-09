-- 统一知识包物理模型：补充术语快照内容，领域包统一由 knowledge_package 与 package_item 承载。
-- 回滚：项目未上线且不保留旧并行表；如需撤销，应回退应用版本并从最近的开发环境备份重建数据库。


CREATE TABLE IF NOT EXISTS mk_term_mapping_snapshot (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             VARCHAR(64)   NOT NULL,
    package_item_id       VARCHAR(64)   NOT NULL,
    mapping_id            BIGINT        NOT NULL,
    local_term_id         BIGINT        NOT NULL,
    standard_term_id      BIGINT        NOT NULL,
    source_system         VARCHAR(64)   NOT NULL,
    local_code            VARCHAR(128)  NOT NULL,
    target_dictionary_key VARCHAR(128)  NOT NULL,
    standard_code         VARCHAR(128)  NOT NULL,
    category              VARCHAR(32)   NULL,
    mapping_snapshot      VARCHAR(4000) NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_term_mapping_snapshot
        UNIQUE (tenant_id, package_item_id, mapping_id),
    CONSTRAINT fk_term_mapping_snapshot_item
        FOREIGN KEY (package_item_id) REFERENCES package_item (item_id)
);

CREATE INDEX IF NOT EXISTS idx_term_mapping_snapshot_item
    ON mk_term_mapping_snapshot (tenant_id, package_item_id);
CREATE INDEX IF NOT EXISTS idx_term_mapping_snapshot_anchor
    ON mk_term_mapping_snapshot (
        tenant_id, source_system, local_code, target_dictionary_key, category
    );

COMMENT ON TABLE mk_term_mapping_snapshot IS '统一知识包术语条目快照：依附 package_item，不再维护独立术语包容器与发布状态';
COMMENT ON COLUMN mk_term_mapping_snapshot.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_term_mapping_snapshot.package_item_id IS '统一知识包条目 ID → package_item.item_id';
COMMENT ON COLUMN mk_term_mapping_snapshot.mapping_id IS '构包时关联的正式术语映射 ID';
COMMENT ON COLUMN mk_term_mapping_snapshot.local_term_id IS '构包时冻结的院内术语 ID';
COMMENT ON COLUMN mk_term_mapping_snapshot.standard_term_id IS '构包时冻结的标准术语 ID';
COMMENT ON COLUMN mk_term_mapping_snapshot.source_system IS '构包时冻结的院内来源系统';
COMMENT ON COLUMN mk_term_mapping_snapshot.local_code IS '构包时冻结的院内编码';
COMMENT ON COLUMN mk_term_mapping_snapshot.target_dictionary_key IS '构包时冻结的目标标准字典';
COMMENT ON COLUMN mk_term_mapping_snapshot.standard_code IS '构包时冻结的标准编码';
COMMENT ON COLUMN mk_term_mapping_snapshot.category IS '构包时冻结的术语分类';
COMMENT ON COLUMN mk_term_mapping_snapshot.mapping_snapshot IS '不可变术语映射 JSON 快照';
