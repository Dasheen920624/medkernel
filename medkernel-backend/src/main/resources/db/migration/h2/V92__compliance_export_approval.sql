-- MedKernel v1.0 GA · SYS-06 敏感数据导出审批（H2）
-- ROLLBACK：确认没有服务依赖 SYS-06 导出审批框架后，删除 mk_compliance_export_approval 表。

CREATE TABLE IF NOT EXISTS mk_compliance_export_approval (
    id                         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    approval_id                VARCHAR(128) NOT NULL,
    tenant_id                  VARCHAR(64)  NOT NULL,
    resource_type              VARCHAR(128) NOT NULL,
    export_scope_snapshot      CLOB         NOT NULL,
    idempotency_key            VARCHAR(128) NOT NULL,
    request_reason             VARCHAR(512) NOT NULL,
    requested_by               VARCHAR(64)  NOT NULL,
    requested_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status                     VARCHAR(32)  NOT NULL DEFAULT 'REQUESTED',
    reviewer_id                VARCHAR(64)  NULL,
    review_decision            VARCHAR(32)  NULL,
    review_comment             VARCHAR(512) NULL,
    reviewed_at                TIMESTAMP    NULL,
    export_uri                 VARCHAR(512) NULL,
    export_digest              VARCHAR(128) NULL,
    approval_evidence_id       VARCHAR(64)  NULL,
    approval_evidence_file_uri VARCHAR(512) NULL,
    export_evidence_id         VARCHAR(64)  NULL,
    export_evidence_file_uri   VARCHAR(512) NULL,
    version                    BIGINT       NOT NULL DEFAULT 1,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                   VARCHAR(128) NULL,
    CONSTRAINT uk_compliance_export_approval UNIQUE (tenant_id, approval_id),
    CONSTRAINT uk_compliance_export_approval_idem UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_compliance_export_approval_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXPORTED')),
    CONSTRAINT ck_compliance_export_approval_decision CHECK (review_decision IS NULL OR review_decision IN ('APPROVE','REJECT')),
    CONSTRAINT ck_compliance_export_approval_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_compliance_export_approval_status
    ON mk_compliance_export_approval (tenant_id, status, requested_at);
CREATE INDEX IF NOT EXISTS idx_compliance_export_approval_resource
    ON mk_compliance_export_approval (tenant_id, resource_type, status);
CREATE INDEX IF NOT EXISTS idx_compliance_export_approval_evidence
    ON mk_compliance_export_approval (tenant_id, approval_evidence_id, export_evidence_id);

COMMENT ON TABLE mk_compliance_export_approval IS 'SYS-06 敏感数据导出审批表，记录申请、审批、真实导出登记与证据链';
COMMENT ON COLUMN mk_compliance_export_approval.approval_id IS '导出审批业务 ID，租户内唯一';
COMMENT ON COLUMN mk_compliance_export_approval.resource_type IS '申请导出的受控资源类型，如 clinical_case 或 evidence_snapshot';
COMMENT ON COLUMN mk_compliance_export_approval.export_scope_snapshot IS '导出范围快照 JSON，审批与证据链以该快照为准';
COMMENT ON COLUMN mk_compliance_export_approval.idempotency_key IS '导出申请幂等键，防止重复创建审批单';
COMMENT ON COLUMN mk_compliance_export_approval.request_reason IS '申请导出原因，必须来自真实业务说明';
COMMENT ON COLUMN mk_compliance_export_approval.status IS '导出审批状态：REQUESTED、APPROVED、REJECTED 或 EXPORTED';
COMMENT ON COLUMN mk_compliance_export_approval.review_decision IS '审批结论：APPROVE 或 REJECT';
COMMENT ON COLUMN mk_compliance_export_approval.export_uri IS '真实导出产物 URI，只有导出完成后写入';
COMMENT ON COLUMN mk_compliance_export_approval.export_digest IS '真实导出产物 SM3 摘要，格式为 sm3:<64 位十六进制>';
COMMENT ON COLUMN mk_compliance_export_approval.approval_evidence_id IS '审批动作生成的证据快照 ID';
COMMENT ON COLUMN mk_compliance_export_approval.export_evidence_id IS '真实导出登记生成的证据快照 ID';
COMMENT ON COLUMN mk_compliance_export_approval.trace_id IS '最近一次导出审批状态变更的链路追踪 ID';
