package com.medkernel.engine.versioning;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * 发布影响评估、灰度放量和覆盖复用的统一治理入口。
 */
@RestController
@RequestMapping("/api/v1/engine/versioning/releases")
@DataScope(requireTenant = true)
public class ReleaseGovernanceController {

    private final ReleaseSimulationService simulations;
    private final VersionReleaseService releases;
    private final VersionRolloutService rollouts;
    private final OverrideTemplateService overrideTemplates;

    public ReleaseGovernanceController(
            ReleaseSimulationService simulations,
            VersionReleaseService releases,
            VersionRolloutService rollouts,
            OverrideTemplateService overrideTemplates) {
        this.simulations = simulations;
        this.releases = releases;
        this.rollouts = rollouts;
        this.overrideTemplates = overrideTemplates;
    }

    @PostMapping("/simulations")
    @PreAuthorize("@perm.has('release.read')")
    public ApiResult<ReleaseSimulationResult> simulate(
            @Valid @RequestBody SimulationRequest request) {
        return ApiResult.ok(simulations.simulate(simulationCommand(request)));
    }

    @PostMapping("/rollouts")
    @PreAuthorize("@perm.has('release.publish')")
    public ApiResult<VersionReleasePlan> startRollout(
            @Valid @RequestBody StartRolloutRequest request) {
        if (request == null || request.simulation() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度发布必须提交完整影响评估参数");
        }
        ReleaseSimulationResult confirmed = simulations.simulate(simulationCommand(request.simulation()));
        if (!confirmed.releasable()) {
            throw new ApiException(ErrorCode.CONFLICT, "发布影响评估未通过，不允许进入灰度");
        }
        if (request.confirmedSimulationDigest() == null
                || !request.confirmedSimulationDigest().equals(confirmed.simulationDigest())) {
            throw new ApiException(ErrorCode.CONFLICT, "影响评估摘要已变化，请重新评估并确认");
        }
        SimulationRequest simulation = request.simulation();
        return ApiResult.ok(releases.releaseGray(new VersionReleaseCommand(
            tenantId(),
            simulation.assetType(),
            simulation.assetIdentity(),
            simulation.candidateVersionId(),
            simulation.targetOrgPath(),
            simulation.applicableScope(),
            VersionReleaseScopeType.FACILITY,
            simulation.targetOrgPath(),
            simulation.rolloutPolicy(),
            confirmed.simulationDigest(),
            request.reviewConclusion(),
            actor(),
            RequestContext.currentTraceId(),
            request.qualityGate()
        )));
    }

    private ReleaseSimulationCommand simulationCommand(SimulationRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布影响评估参数不能为空");
        }
        String tenantId = tenantId();
        return new ReleaseSimulationCommand(
            tenantId,
            blankToDefault(request.candidateTenantId(), tenantId),
            request.assetType(),
            request.assetIdentity(),
            request.candidateVersionId(),
            request.targetOrgUnitIds(),
            request.targetOrgPath(),
            request.applicableScope(),
            request.rolloutPolicy(),
            request.replayDays(),
            request.replayLimit()
        );
    }

    @PostMapping("/rollouts/{planId}/observations")
    @PreAuthorize("@perm.has('release.publish')")
    public ApiResult<VersionRolloutObservationResult> observeRollout(
            @PathVariable String planId,
            @Valid @RequestBody RolloutObservationRequest request) {
        return ApiResult.ok(rollouts.observe(new VersionRolloutObservationCommand(
            tenantId(),
            planId,
            request.stageIndex(),
            request.sampleCount(),
            request.hitCount(),
            request.blockCount(),
            request.manualRejectionCount(),
            request.anomalyCount(),
            request.observedAt(),
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    @PostMapping("/rollouts/{planId}:rollback")
    @PreAuthorize("@perm.has('release.rollback')")
    public ApiResult<VersionReleasePlan> rollbackRollout(
            @PathVariable String planId,
            @Valid @RequestBody RolloutRollbackRequest request) {
        return ApiResult.ok(rollouts.rollback(new VersionRolloutRollbackCommand(
            tenantId(),
            planId,
            request.reason(),
            request.confirmedOperation(),
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    @GetMapping("/override-templates")
    @PreAuthorize("@perm.has('release.read')")
    public ApiResult<PageResponse<OverrideTemplate>> listTemplates(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {
        return ApiResult.ok(overrideTemplates.listTemplates(tenantId(), new PageRequest(page, size, sort)));
    }

    @PostMapping("/override-templates")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<OverrideTemplateDetail> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        return ApiResult.ok(overrideTemplates.createTemplate(new OverrideTemplateCreateCommand(
            tenantId(),
            request.templateName(),
            request.description(),
            request.applicableScope(),
            request.items(),
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    @PostMapping("/override-batches:preview")
    @PreAuthorize("@perm.has('release.read')")
    public ApiResult<OverrideBatchPreviewResult> previewOverrides(
            @Valid @RequestBody OverridePreviewRequest request) {
        return ApiResult.ok(overrideTemplates.preview(previewCommand(request)));
    }

    @PostMapping("/override-batches:apply")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<OverrideBatchOperationResult> applyOverrides(
            @Valid @RequestBody OverrideApplyRequest request) {
        return ApiResult.ok(overrideTemplates.apply(new OverrideBatchApplyCommand(
            previewCommand(request.preview()),
            request.confirmedPreviewDigest()
        )));
    }

    @PostMapping("/override-batches/{operationId}:revoke")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<OverrideBatchOperationResult> revokeOverrides(
            @PathVariable String operationId) {
        return ApiResult.ok(overrideTemplates.revoke(new OverrideBatchRevokeCommand(
            tenantId(),
            operationId,
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    private OverrideBatchPreviewCommand previewCommand(OverridePreviewRequest request) {
        return new OverrideBatchPreviewCommand(
            tenantId(),
            request.templateId(),
            request.sourceOrgUnitId(),
            request.targetOrgUnitIds(),
            request.targetVersionIds(),
            actor(),
            RequestContext.currentTraceId()
        );
    }

    private String tenantId() {
        return RequestContext.currentOrgScope().tenantId();
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record SimulationRequest(
        @Size(max = 64) String candidateTenantId,
        @NotNull VersionedAssetType assetType,
        @NotBlank @Size(max = 256) String assetIdentity,
        @NotBlank @Size(max = 64) String candidateVersionId,
        @NotEmpty List<@NotBlank @Size(max = 64) String> targetOrgUnitIds,
        @NotBlank @Size(max = 1000) String targetOrgPath,
        @NotBlank @Size(max = 512) String applicableScope,
        @NotNull @Valid RolloutPolicy rolloutPolicy,
        @Positive Integer replayDays,
        @Positive Integer replayLimit
    ) {
    }

    public record StartRolloutRequest(
        @NotNull @Valid SimulationRequest simulation,
        @NotBlank @Size(max = 128) String confirmedSimulationDigest,
        @NotBlank @Size(max = 2000) String reviewConclusion,
        VersionPublishQualityGate qualityGate
    ) {
    }

    public record RolloutObservationRequest(
        @NotNull @PositiveOrZero Integer stageIndex,
        @NotNull @PositiveOrZero Long sampleCount,
        @NotNull @PositiveOrZero Long hitCount,
        @NotNull @PositiveOrZero Long blockCount,
        @NotNull @PositiveOrZero Long manualRejectionCount,
        @NotNull @PositiveOrZero Long anomalyCount,
        @NotNull Instant observedAt
    ) {
    }

    public record RolloutRollbackRequest(
        @NotBlank @Size(max = 2000) String reason,
        @NotNull Boolean confirmedOperation
    ) {
    }

    public record CreateTemplateRequest(
        @NotBlank @Size(max = 128) String templateName,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 512) String applicableScope,
        @NotEmpty List<@Valid OverrideTemplateItemInput> items
    ) {
    }

    public record OverridePreviewRequest(
        @Size(max = 64) String templateId,
        @Size(max = 64) String sourceOrgUnitId,
        @NotEmpty List<@NotBlank @Size(max = 64) String> targetOrgUnitIds,
        @NotNull Map<@NotBlank String, @NotBlank String> targetVersionIds
    ) {
    }

    public record OverrideApplyRequest(
        @NotNull @Valid OverridePreviewRequest preview,
        @NotBlank @Size(max = 128) String confirmedPreviewDigest
    ) {
    }
}
