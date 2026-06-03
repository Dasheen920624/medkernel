-- MedKernel v1.0 GA · SVC-PILOT-02 MPI 合并审核与数据质量报告（PostgreSQL）

CREATE TABLE IF NOT EXISTS mk_mpi_merge_review (
    review_id        VARCHAR(64)   PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    source_mpi_id    VARCHAR(64)   NOT NULL,
    target_mpi_id    VARCHAR(64)   NOT NULL,
    risk_level       VARCHAR(16)   NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    risk_reason      VARCHAR(512)  NOT NULL,
    requested_by     VARCHAR(64)   NOT NULL,
    requested_at     TIMESTAMPTZ   NOT NULL,
    reviewed_by      VARCHAR(64)   NULL,
    reviewed_at      TIMESTAMPTZ   NULL,
    review_reason    VARCHAR(512)  NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(64)   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    trace_id         VARCHAR(128)  NULL,
    CONSTRAINT uk_mpi_merge_review_pair UNIQUE (tenant_id, source_mpi_id, target_mpi_id),
    CONSTRAINT ck_mpi_merge_review_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_mpi_merge_review_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_mpi_mrv_tenant_status
    ON mk_mpi_merge_review (tenant_id, status, requested_at);
CREATE INDEX IF NOT EXISTS idx_mpi_mrv_source
    ON mk_mpi_merge_review (tenant_id, source_mpi_id);

CREATE TABLE IF NOT EXISTS mk_integration_data_quality_report (
    report_id               VARCHAR(64)  PRIMARY KEY,
    tenant_id               VARCHAR(64)  NOT NULL,
    generated_at            TIMESTAMPTZ  NOT NULL,
    required_field_total    INTEGER      NOT NULL,
    required_field_present  INTEGER      NOT NULL,
    required_field_rate     NUMERIC(5,2) NOT NULL,
    adapter_total           INTEGER      NOT NULL,
    mapped_adapter_count    INTEGER      NOT NULL,
    mapping_rate            NUMERIC(5,2) NOT NULL,
    timely_adapter_count    INTEGER      NOT NULL,
    timeliness_rate         NUMERIC(5,2) NOT NULL,
    not_connected_count     INTEGER      NOT NULL,
    misconfigured_count     INTEGER      NOT NULL,
    gap_summary             VARCHAR(2000) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    trace_id                VARCHAR(128) NULL,
    CONSTRAINT ck_dqr_required_nonneg CHECK (required_field_total >= 0 AND required_field_present >= 0),
    CONSTRAINT ck_dqr_adapter_nonneg CHECK (adapter_total >= 0 AND mapped_adapter_count >= 0 AND timely_adapter_count >= 0),
    CONSTRAINT ck_dqr_status_nonneg CHECK (not_connected_count >= 0 AND misconfigured_count >= 0),
    CONSTRAINT ck_dqr_rates CHECK (
        required_field_rate >= 0 AND required_field_rate <= 100
        AND mapping_rate >= 0 AND mapping_rate <= 100
        AND timeliness_rate >= 0 AND timeliness_rate <= 100
    )
);

CREATE INDEX IF NOT EXISTS idx_dqr_tenant_generated
    ON mk_integration_data_quality_report (tenant_id, generated_at DESC);

COMMENT ON TABLE mk_mpi_merge_review IS '高危患者主索引合并审核单';
COMMENT ON COLUMN mk_mpi_merge_review.review_id IS '审核单业务主键（ULID 形态）';
COMMENT ON COLUMN mk_mpi_merge_review.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_mpi_merge_review.source_mpi_id IS '待合并源患者主索引 ID';
COMMENT ON COLUMN mk_mpi_merge_review.target_mpi_id IS '合并目标患者主索引 ID';
COMMENT ON COLUMN mk_mpi_merge_review.risk_level IS '合并风险等级';
COMMENT ON COLUMN mk_mpi_merge_review.status IS '审核状态：PENDING/CONFIRMED/REJECTED';
COMMENT ON COLUMN mk_mpi_merge_review.risk_reason IS '触发人工确认的风险原因';
COMMENT ON COLUMN mk_mpi_merge_review.requested_by IS '发起审核人';
COMMENT ON COLUMN mk_mpi_merge_review.requested_at IS '发起审核时间';
COMMENT ON COLUMN mk_mpi_merge_review.reviewed_by IS '确认或拒绝审核人';
COMMENT ON COLUMN mk_mpi_merge_review.reviewed_at IS '确认或拒绝审核时间';
COMMENT ON COLUMN mk_mpi_merge_review.review_reason IS '人工确认或拒绝理由';
COMMENT ON COLUMN mk_mpi_merge_review.created_at IS '创建时间';
COMMENT ON COLUMN mk_mpi_merge_review.created_by IS '创建人';
COMMENT ON COLUMN mk_mpi_merge_review.updated_at IS '更新时间';
COMMENT ON COLUMN mk_mpi_merge_review.updated_by IS '更新人';
COMMENT ON COLUMN mk_mpi_merge_review.trace_id IS '链路追踪 ID';

COMMENT ON TABLE mk_integration_data_quality_report IS '数据质量报告快照';
COMMENT ON COLUMN mk_integration_data_quality_report.report_id IS '报告业务主键（ULID 形态）';
COMMENT ON COLUMN mk_integration_data_quality_report.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_integration_data_quality_report.generated_at IS '报告生成时间';
COMMENT ON COLUMN mk_integration_data_quality_report.required_field_total IS 'MPI 核心必填字段槽位总数';
COMMENT ON COLUMN mk_integration_data_quality_report.required_field_present IS 'MPI 核心必填字段已填数量';
COMMENT ON COLUMN mk_integration_data_quality_report.required_field_rate IS 'MPI 核心必填字段完成率百分比';
COMMENT ON COLUMN mk_integration_data_quality_report.adapter_total IS '适配器总数';
COMMENT ON COLUMN mk_integration_data_quality_report.mapped_adapter_count IS '已配置字段映射的适配器数量';
COMMENT ON COLUMN mk_integration_data_quality_report.mapping_rate IS '字段映射配置完成率百分比';
COMMENT ON COLUMN mk_integration_data_quality_report.timely_adapter_count IS '24 小时内完成连通核查的适配器数量';
COMMENT ON COLUMN mk_integration_data_quality_report.timeliness_rate IS '连通核查时效率百分比';
COMMENT ON COLUMN mk_integration_data_quality_report.not_connected_count IS 'NOT_CONNECTED 适配器数量';
COMMENT ON COLUMN mk_integration_data_quality_report.misconfigured_count IS 'MISCONFIGURED 适配器数量';
COMMENT ON COLUMN mk_integration_data_quality_report.gap_summary IS '数据质量缺口摘要';
COMMENT ON COLUMN mk_integration_data_quality_report.created_at IS '创建时间';
COMMENT ON COLUMN mk_integration_data_quality_report.created_by IS '创建人';
COMMENT ON COLUMN mk_integration_data_quality_report.trace_id IS '链路追踪 ID';
