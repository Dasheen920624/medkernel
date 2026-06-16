-- MedKernel 第二阶段 P2-C · AIK-STD-05 候选安全门禁结果（KingbaseES）
-- 候选提审前每过一项安全门禁记一行结果（候选指纹 + 门禁码 + 通过判定 + 不过原因 + 时点）；append-only 审计轨迹。
-- ROLLBACK：确认无引用后 DROP TABLE mk_aik_gate_result。

CREATE TABLE IF NOT EXISTS mk_aik_gate_result (
    id               BIGSERIAL     PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    job_code         VARCHAR(64)   NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    gate_code        VARCHAR(48)   NOT NULL,
    passed           BOOLEAN       NOT NULL,
    reason           VARCHAR(512)  NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL
);

CREATE INDEX idx_mk_aik_gate_result_job ON mk_aik_gate_result (tenant_id, job_code);

COMMENT ON TABLE mk_aik_gate_result IS '候选安全门禁结果：候选提审前每过一项安全门禁记一行，含候选指纹与门禁码与通过判定与不过原因与时点，append-only 审计轨迹';
