package com.medkernel.engine.sandbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 沙盘不可变运行基线与执行结果账本。 */
@Table("mk_sandbox_run")
public record SandboxRun(
    @Id Long id,
    @Column("run_id") String runId,
    @Column("tenant_id") String tenantId,
    @Column("scenario_id") String scenarioId,
    @Column("run_mode") SandboxRunMode mode,
    @Column("replay_case_id") String replayCaseId,
    @Column("binding_id") String bindingId,
    @Column("baseline_id") String baselineId,
    @Column("package_owner_tenant_id") String packageOwnerTenantId,
    @Column("package_id") String packageId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    @Column("resolution_source") SandboxResolutionSource resolutionSource,
    @Column("asset_bindings_json") String assetBindingsJson,
    @Column("baseline_hash") String baselineHash,
    @Column("external_side_effect_status") SandboxExternalSideEffectStatus externalSideEffectStatus,
    SandboxRunStatus status,
    @Column("failure_code") String failureCode,
    @Column("failure_message") String failureMessage,
    @Column("started_at") Instant startedAt,
    @Column("completed_at") Instant completedAt,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
