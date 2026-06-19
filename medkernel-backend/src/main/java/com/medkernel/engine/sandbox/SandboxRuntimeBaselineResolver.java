package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.EffectivePackageItem;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

/** 从演练机构唯一 ACTIVE 绑定解析 CURRENT 基线。 */
@Service
public class SandboxRuntimeBaselineResolver {

    private final SandboxRuntimeBindingRepository bindings;
    private final KnowledgePackageRepository packages;
    private final EffectiveKnowledgePackageResolver effectivePackages;
    private final SandboxReplayService replayCases;

    public SandboxRuntimeBaselineResolver(
            SandboxRuntimeBindingRepository bindings,
            KnowledgePackageRepository packages,
            EffectiveKnowledgePackageResolver effectivePackages,
            SandboxReplayService replayCases) {
        this.bindings = bindings;
        this.packages = packages;
        this.effectivePackages = effectivePackages;
        this.replayCases = replayCases;
    }

    public SandboxRuntimeBaseline resolveCurrent(String tenantId, String targetOrgUnitId) {
        List<SandboxRuntimeBinding> active = bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            required(tenantId, "租户 ID"), SandboxRuntimeBindingStatus.ACTIVE);
        if (active.isEmpty()) {
            throw conflict("SANDBOX_RUNTIME_BASELINE_MISSING：演练机构未激活沙盘运行绑定");
        }
        if (active.size() != 1) {
            throw conflict("SANDBOX_RUNTIME_BASELINE_AMBIGUOUS：演练机构存在多个 ACTIVE 沙盘运行绑定");
        }
        SandboxRuntimeBinding binding = active.get(0);
        String target = required(targetOrgUnitId, "目标组织 ID");
        if (!target.equals(binding.targetOrgUnitId())) {
            throw conflict("SANDBOX_RUNTIME_TARGET_MISMATCH：运行组织与绑定组织不一致");
        }

        KnowledgePackage pack = packages.findByPackageIdAndTenantId(
                binding.packageId(), binding.packageOwnerTenantId())
            .orElseThrow(() -> conflict("SANDBOX_RUNTIME_PACKAGE_MISSING：绑定的配置包不存在"));
        assertBindingMatchesPackage(binding, pack);
        if (pack.status() != KnowledgePackageStatus.PUBLISHED && pack.status() != KnowledgePackageStatus.ACTIVE) {
            throw conflict("SANDBOX_RUNTIME_PACKAGE_NOT_RELEASED：绑定的配置包不是可运行状态");
        }

        EffectiveKnowledgePackageResponse effective = effectivePackages.resolveExplicitPackage(
            binding.tenantId(), pack, binding.targetOrgUnitId());
        if (!binding.packageId().equals(effective.packageId())
                || !binding.packageVersion().equals(effective.packageVersion())) {
            throw conflict("SANDBOX_RUNTIME_PACKAGE_DRIFT：有效包解析结果与明确绑定不一致");
        }
        SandboxResolutionSource source = PlatformTenant.ID.equals(binding.packageOwnerTenantId())
            ? SandboxResolutionSource.PLATFORM_PACKAGE
            : SandboxResolutionSource.TENANT_PACKAGE;
        return new SandboxRuntimeBaseline(
            "baseline-" + UUID.randomUUID(), SandboxRunMode.CURRENT, binding.tenantId(),
            binding.targetOrgUnitId(), binding.bindingId(), binding.packageOwnerTenantId(),
            binding.packageId(), binding.packageCode(), binding.packageVersion(), source,
            Instant.now(), effective, null, null);
    }

    /** 只从演练机构持有的不可变清单解析历史基线，不读取当前绑定或当前配置包。 */
    public SandboxRuntimeBaseline resolveHistorical(
            String tenantId,
            String targetOrgUnitId,
            String replayCaseId) {
        String tenant = required(tenantId, "租户 ID");
        String target = required(targetOrgUnitId, "目标组织 ID");
        SandboxReplayResolvedCase replay = replayCases.resolve(required(replayCaseId, "replayCaseId"));
        if (!tenant.equals(replay.replayCase().sandboxTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "历史重放清单不属于当前演练机构");
        }
        List<EffectivePackageItem> items = replay.assets().stream()
            .map(asset -> new EffectivePackageItem(
                asset.assetType(), asset.assetIdentity(), asset.assetVersion(), asset.assetVersion(),
                asset.sourceOrgRef(), null, asset.sourceTier(),
                asset.sourceTier() == com.medkernel.engine.versioning.SourceTier.PLATFORM,
                false, false, asset.versionId(), asset.contentHash()))
            .toList();
        EffectiveKnowledgePackageResponse effective = new EffectiveKnowledgePackageResponse(
            tenant, target, null, replay.replayCase().packageCode(),
            replay.replayCase().packageVersion(), items, List.of(),
            List.of("历史原样重放只读基线，不代表当前激活知识"));
        return new SandboxRuntimeBaseline(
            "baseline-" + UUID.randomUUID(), SandboxRunMode.HISTORICAL_EXACT, tenant, target,
            null, null, null, replay.replayCase().packageCode(),
            replay.replayCase().packageVersion(), SandboxResolutionSource.REPLAY_MANIFEST,
            Instant.now(), effective, replay.replayCase().replayCaseId(), replay);
    }

    /** 同时冻结当前有效包和历史清单；两侧只共享历史清单中的脱敏上下文。 */
    public SandboxRuntimeBaseline resolveCompare(
            String tenantId,
            String targetOrgUnitId,
            String replayCaseId) {
        SandboxRuntimeBaseline current = resolveCurrent(tenantId, targetOrgUnitId);
        SandboxRuntimeBaseline historical = resolveHistorical(
            tenantId, targetOrgUnitId, replayCaseId);
        return new SandboxRuntimeBaseline(
            "baseline-" + UUID.randomUUID(), SandboxRunMode.COMPARE,
            current.tenantId(), current.targetOrgUnitId(), current.bindingId(),
            current.packageOwnerTenantId(), current.packageId(), current.packageCode(),
            current.packageVersion(), current.resolutionSource(), Instant.now(),
            current.effectivePackage(), historical.replayCaseId(), historical.historicalReplay());
    }

    private static void assertBindingMatchesPackage(SandboxRuntimeBinding binding, KnowledgePackage pack) {
        if (!binding.packageCode().equals(pack.packageCode())
                || !binding.packageVersion().equals(pack.packageVersion())
                || !binding.packageOwnerTenantId().equals(pack.tenantId())) {
            throw conflict("SANDBOX_RUNTIME_PACKAGE_DRIFT：配置包身份或版本与绑定不一致");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
