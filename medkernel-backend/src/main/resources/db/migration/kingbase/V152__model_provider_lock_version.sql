ALTER TABLE mk_llm_provider
    ADD COLUMN lock_version BIGINT DEFAULT 0 NOT NULL;

COMMENT ON COLUMN mk_llm_provider.lock_version IS '模型 provider 治理并发版本号，防止配置、探活与启停相互覆盖';
