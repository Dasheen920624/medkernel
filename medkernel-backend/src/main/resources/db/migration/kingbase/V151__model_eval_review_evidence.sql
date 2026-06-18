-- MedKernel P9 · 医学回归独立专家复核证据（金仓）
-- ROLLBACK：删除 menu.model-evaluation-review 权限、证据表并移除 review_comment 列。

ALTER TABLE mk_llm_eval_run ADD COLUMN review_comment VARCHAR(1000) NULL;

CREATE TABLE mk_llm_eval_case_evidence (
    id                    BIGSERIAL     PRIMARY KEY,
    tenant_id             VARCHAR(64)   NOT NULL,
    run_id                BIGINT        NOT NULL,
    regression_case_id    BIGINT        NOT NULL,
    case_version          VARCHAR(64)   NOT NULL,
    case_input            TEXT          NOT NULL,
    expected_phrase       VARCHAR(2000) NOT NULL,
    red_line_type         VARCHAR(64)   NULL,
    source_reference      VARCHAR(512)  NOT NULL,
    output_content        TEXT          NOT NULL,
    source_citations      TEXT          NULL,
    expected_phrase_hit   CHAR(1)       NOT NULL,
    citation_required     CHAR(1)       NOT NULL,
    citation_verified     CHAR(1)       NOT NULL,
    red_line_case         CHAR(1)       NOT NULL,
    red_line_breach       CHAR(1)       NOT NULL,
    passed_flag           CHAR(1)       NOT NULL,
    failure_reasons_json  VARCHAR(1000) NOT NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT fk_mk_llm_eval_case_run FOREIGN KEY (run_id) REFERENCES mk_llm_eval_run(id),
    CONSTRAINT uk_mk_llm_eval_case_run_case UNIQUE (tenant_id, run_id, regression_case_id),
    CONSTRAINT ck_mk_llm_eval_case_flags CHECK (
        expected_phrase_hit IN ('Y', 'N') AND citation_required IN ('Y', 'N')
        AND citation_verified IN ('Y', 'N') AND red_line_case IN ('Y', 'N')
        AND red_line_breach IN ('Y', 'N') AND passed_flag IN ('Y', 'N'))
);

CREATE INDEX idx_mk_llm_eval_case_evidence_run
    ON mk_llm_eval_case_evidence (tenant_id, run_id, id);

COMMENT ON TABLE mk_llm_eval_case_evidence IS '医学回归评测逐用例不可变证据，供独立专家据证复核';
COMMENT ON COLUMN mk_llm_eval_case_evidence.tenant_id IS '租户标识';
COMMENT ON COLUMN mk_llm_eval_case_evidence.run_id IS '医学回归评测运行主键';
COMMENT ON COLUMN mk_llm_eval_case_evidence.regression_case_id IS '评测时使用的回归用例主键';
COMMENT ON COLUMN mk_llm_eval_case_evidence.case_version IS '评测时用例版本';
COMMENT ON COLUMN mk_llm_eval_case_evidence.case_input IS '评测时用例输入快照，不得包含患者身份数据';
COMMENT ON COLUMN mk_llm_eval_case_evidence.expected_phrase IS '评测时安全期望短语快照';
COMMENT ON COLUMN mk_llm_eval_case_evidence.red_line_type IS '评测时红线类型快照';
COMMENT ON COLUMN mk_llm_eval_case_evidence.source_reference IS '评测时真实来源引用快照';
COMMENT ON COLUMN mk_llm_eval_case_evidence.output_content IS '候选模型真实输出';
COMMENT ON COLUMN mk_llm_eval_case_evidence.source_citations IS '候选模型返回的来源引用载荷';
COMMENT ON COLUMN mk_llm_eval_case_evidence.expected_phrase_hit IS '是否命中安全期望短语：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.citation_required IS '该用例是否要求来源引用：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.citation_verified IS '来源引用是否精确核验通过：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.red_line_case IS '是否红线用例：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.red_line_breach IS '是否突破医学红线：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.passed_flag IS '逐用例是否通过：Y/N';
COMMENT ON COLUMN mk_llm_eval_case_evidence.failure_reasons_json IS '失败原因代码 JSON 数组';
COMMENT ON COLUMN mk_llm_eval_case_evidence.created_at IS '证据生成时间';
COMMENT ON COLUMN mk_llm_eval_case_evidence.created_by IS '评测执行人';
COMMENT ON COLUMN mk_llm_eval_run.review_comment IS '独立专家复核意见';

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('menu.model-evaluation-review', 'MENU', 'model-evaluation-review', '查看医学回归复核',
        'LOW', 'migration-v151', 'migration-v151');
