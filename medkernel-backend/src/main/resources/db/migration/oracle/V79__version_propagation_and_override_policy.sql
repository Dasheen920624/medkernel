-- MedKernel v1.0 GA · 平台优先继承底座 P0-1.1：传播语义与覆盖策略护栏（Oracle）
-- ROLLBACK：确认无继承解析依赖后，移除 mk_version_inheritance_override.propagation 与
--           mk_version_asset_version.override_policy 两列及其 CHECK 约束。

ALTER TABLE mk_version_inheritance_override ADD (
    propagation VARCHAR2(32) DEFAULT 'INHERITABLE' NOT NULL
);

ALTER TABLE mk_version_inheritance_override
    ADD CONSTRAINT ck_mk_version_inheritance_override_propagation
    CHECK (propagation IN ('INHERITABLE','EXCLUSIVE'));

ALTER TABLE mk_version_asset_version ADD (
    override_policy VARCHAR2(32) DEFAULT 'FREE' NOT NULL
);

ALTER TABLE mk_version_asset_version
    ADD CONSTRAINT ck_mk_version_asset_override_policy
    CHECK (override_policy IN ('FREE','REVIEW','LOCKED'));

COMMENT ON COLUMN mk_version_inheritance_override.propagation IS '覆盖传播范围：INHERITABLE 下级复用 / EXCLUSIVE 仅本节点独有';
COMMENT ON COLUMN mk_version_asset_version.override_policy IS '下游覆盖策略护栏：FREE 可自由覆盖 / REVIEW 覆盖需评审 / LOCKED 安全单调（禁止关闭、只能收紧不能放宽）';
