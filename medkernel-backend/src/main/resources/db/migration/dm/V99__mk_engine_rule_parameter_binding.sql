-- MedKernel v1.0 GA · P12-4 参数化规则实例参数绑定（达梦）
-- ROLLBACK：如需回滚，先导出 mk_engine_rule_parameter_binding 审计证据与关联规则版本，再删除本表。

CREATE TABLE mk_engine_rule_parameter_binding (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    rule_version_id     VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    param_key           VARCHAR(64)   NOT NULL,
    param_value_json    CLOB          NOT NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR(64)   DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_engine_rule_parameter_binding_key UNIQUE (tenant_id, rule_version_id, param_key)
);

CREATE INDEX idx_mk_engine_rule_parameter_binding_version
    ON mk_engine_rule_parameter_binding (tenant_id, rule_version_id);

CREATE INDEX idx_mk_engine_rule_parameter_binding_key
    ON mk_engine_rule_parameter_binding (tenant_id, param_key);

COMMENT ON TABLE mk_engine_rule_parameter_binding IS '参数化规则实例参数值，schema 保存在规则版本 DSL 的 meta.parameters';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.rule_version_id IS '规则版本业务 ID';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.param_key IS '参数键，对应 DSL meta.parameters.key';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.param_value_json IS '参数实例值 JSON';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.created_at IS '参数绑定创建时间';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.created_by IS '参数绑定创建人';
COMMENT ON COLUMN mk_engine_rule_parameter_binding.trace_id IS '请求链路追踪 ID';
