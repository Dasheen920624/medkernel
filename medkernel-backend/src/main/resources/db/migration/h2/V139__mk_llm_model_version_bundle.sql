-- MedKernel 第二阶段 P2-C · LLM-04 prompt/tool/model 版本治理（H2）
-- 记录模型任务三元组版本包；只存版本号与 hash，不保存提示词正文或工具契约明文。
-- ROLLBACK：确认无引用后 DROP TABLE mk_llm_model_version_bundle；ALTER TABLE model_capability_task DROP COLUMN tool_version。

ALTER TABLE model_capability_task ADD COLUMN IF NOT EXISTS tool_version VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS mk_llm_model_version_bundle (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    capability_code  VARCHAR(96)  NOT NULL,
    prompt_version   VARCHAR(128) NOT NULL,
    prompt_hash      VARCHAR(64)  NOT NULL,
    tool_version     VARCHAR(128) NOT NULL,
    tool_hash        VARCHAR(64)  NOT NULL,
    model_version    VARCHAR(128) NOT NULL,
    model_hash       VARCHAR(64)  NOT NULL,
    status           VARCHAR(24)  NOT NULL,
    effective_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at       TIMESTAMP    NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)  NULL,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NULL,
    CONSTRAINT ck_mk_llm_model_version_bundle_status CHECK (status IN ('ACTIVE','RETIRED'))
);

CREATE INDEX idx_mk_llm_model_version_bundle_capability ON mk_llm_model_version_bundle (tenant_id, capability_code, status, id);

COMMENT ON TABLE mk_llm_model_version_bundle IS '模型版本三元组版本包：记录 prompt/tool/model 版本号、状态和内容 hash，不保存正文或凭据';
