-- 统一知识包物理收敛：删除领域重复容器，仅保留统一包与术语快照内容。
-- 回滚：项目未上线且不保留旧并行表；如需撤销，应回退应用版本并从最近的开发环境备份重建数据库。

DROP TABLE IF EXISTS term_mapping_package_release;
DROP TABLE IF EXISTS term_mapping_package_item;
DROP TABLE IF EXISTS term_mapping_package;
DROP TABLE IF EXISTS specialty_package;

CREATE TABLE IF NOT EXISTS mk_term_mapping_snapshot (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
COMMENT ON COLUMN mk_term_mapping_snapshot.package_item_id IS '统一知识包条目 ID → package_item.item_id';
COMMENT ON COLUMN mk_term_mapping_snapshot.mapping_snapshot IS '不可变术语映射 JSON 快照';
