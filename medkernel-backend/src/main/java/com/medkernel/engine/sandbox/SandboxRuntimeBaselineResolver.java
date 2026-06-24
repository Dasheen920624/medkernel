package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 只从认证当前机构生效版本或不可变历史重放清单解析沙盘基线。 */
@Service
public class SandboxRuntimeBaselineResolver {

    private final CurrentClinicalRuntimeReleaseResolver runtimeReleases;
    private final ClinicalRuntimeReleaseContentResolver runtimeContents;
    private final SandboxReplayService replayCases;

    public SandboxRuntimeBaselineResolver(
            CurrentClinicalRuntimeReleaseResolver runtimeReleases,
            ClinicalRuntimeReleaseContentResolver runtimeContents,
            SandboxReplayService replayCases) {
        this.runtimeReleases = runtimeReleases;
        this.runtimeContents = runtimeContents;
        this.replayCases = replayCases;
    }

    /** 冻结认证医院当前不可变机构生效版本及其已校验完整清单。 */
    public SandboxRuntimeBaseline resolveCurrent() {
        OrgScope scope = currentScope();
        ClinicalRuntimeRelease release = runtimeReleases.resolve(scope);
        ClinicalRuntimeReleaseContent content =
            runtimeContents.resolve(scope.tenantId(), release.releaseId());
        return currentBaseline(scope, release, content, SandboxRunMode.CURRENT, null, null);
    }

    /** 只从演练机构持有的不可变清单解析历史基线，不读取任何当前运行状态。 */
    public SandboxRuntimeBaseline resolveHistorical(String replayCaseId) {
        OrgScope scope = currentScope();
        SandboxReplayResolvedCase replay = replayCases.resolve(required(replayCaseId, "replayCaseId"));
        if (!scope.tenantId().equals(replay.replayCase().sandboxTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "历史重放清单不属于当前演练机构");
        }
        return new SandboxRuntimeBaseline(
            newBaselineId(),
            SandboxRunMode.HISTORICAL_EXACT,
            scope.tenantId(),
            targetOrgUnitId(scope),
            null,
            replay.replayCase().sourceRuntimeRevisionNo(),
            null,
            replay.replayCase().manifestHash(),
            SandboxResolutionSource.REPLAY_MANIFEST,
            Instant.now(),
            null,
            replay.replayCase().replayCaseId(),
            replay);
    }

    /** 冻结当前机构生效版本和历史清单，两侧只共享历史清单中的脱敏上下文。 */
    public SandboxRuntimeBaseline resolveCompare(String replayCaseId) {
        OrgScope scope = currentScope();
        ClinicalRuntimeRelease release = runtimeReleases.resolve(scope);
        ClinicalRuntimeReleaseContent content =
            runtimeContents.resolve(scope.tenantId(), release.releaseId());
        SandboxReplayResolvedCase replay = replayCases.resolve(required(replayCaseId, "replayCaseId"));
        if (!scope.tenantId().equals(replay.replayCase().sandboxTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "历史重放清单不属于当前演练机构");
        }
        return currentBaseline(scope, release, content, SandboxRunMode.COMPARE,
            replay.replayCase().replayCaseId(), replay);
    }

    private static SandboxRuntimeBaseline currentBaseline(
            OrgScope scope,
            ClinicalRuntimeRelease release,
            ClinicalRuntimeReleaseContent content,
            SandboxRunMode mode,
            String replayCaseId,
            SandboxReplayResolvedCase replay) {
        return new SandboxRuntimeBaseline(
            newBaselineId(),
            mode,
            scope.tenantId(),
            targetOrgUnitId(scope),
            release.releaseId(),
            release.revisionNo(),
            release.platformBaselineReleaseId(),
            release.manifestSha256(),
            SandboxResolutionSource.CURRENT_RUNTIME_RELEASE,
            Instant.now(),
            content,
            replayCaseId,
            replay);
    }

    private static OrgScope currentScope() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope;
    }

    private static String targetOrgUnitId(OrgScope scope) {
        return scope.nearestOrgUnitIdOrTenant(scope.tenantId());
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private static String newBaselineId() {
        return "sandbox-baseline-" + UUID.randomUUID();
    }
}
