package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/** 管理演练机构唯一 ACTIVE 沙盘运行绑定，并提供不阻断页面加载的动态就绪状态。 */
@Service
public class SandboxRuntimeBindingService {

    private final SandboxRuntimeBindingRepository bindings;
    private final KnowledgePackageRepository packages;
    private final EffectiveKnowledgePackageResolver effectivePackages;
    private final SandboxRuntimeBaselineResolver baselines;
    private final AuditRecorder audit;

    public SandboxRuntimeBindingService(
            SandboxRuntimeBindingRepository bindings,
            KnowledgePackageRepository packages,
            EffectiveKnowledgePackageResolver effectivePackages,
            SandboxRuntimeBaselineResolver baselines,
            AuditRecorder audit) {
        this.bindings = bindings;
        this.packages = packages;
        this.effectivePackages = effectivePackages;
        this.baselines = baselines;
        this.audit = audit;
    }

    /** 读取当前上下文的动态运行状态；缺绑定或缺资产返回诚实未就绪，不伪造成功。 */
    public SandboxRuntimeStatusResponse currentStatus() {
        RuntimeScope scope = currentScope();
        try {
            return SandboxRuntimeStatusResponse.ready(
                baselines.resolveCurrent(scope.tenantId(), scope.targetOrgUnitId()));
        } catch (RuntimeException exception) {
            String message = messageOf(exception);
            return SandboxRuntimeStatusResponse.notReady(
                scope.targetOrgUnitId(), reasonCode(message), message);
        }
    }

    /** 激活演练机构自有包或已授权的平台主源包；包版本始终读取权威记录。 */
    @Transactional
    public SandboxRuntimeStatusResponse activate(SandboxRuntimeBindingRequest request) {
        RuntimeScope scope = currentScope();
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "沙盘运行绑定请求不能为空");
        }
        String ownerTenantId = required(request.packageOwnerTenantId(), "配置包归属租户 ID");
        String packageId = required(request.packageId(), "配置包 ID");
        if (!scope.tenantId().equals(ownerTenantId) && !PlatformTenant.ID.equals(ownerTenantId)) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST, "只能绑定演练机构自有包或平台主源包");
        }
        KnowledgePackage pack = packages.findByPackageIdAndTenantId(packageId, ownerTenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "待绑定配置包不存在"));
        if (pack.status() != KnowledgePackageStatus.PUBLISHED
                && pack.status() != KnowledgePackageStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "待绑定配置包不是可运行状态");
        }
        EffectiveKnowledgePackageResponse effective = effectivePackages.resolveExplicitPackage(
            scope.tenantId(), pack, scope.targetOrgUnitId());
        if (!pack.packageId().equals(effective.packageId())
                || !pack.packageVersion().equals(effective.packageVersion())) {
            throw new ApiException(ErrorCode.CONFLICT, "待绑定配置包与有效解析结果不一致");
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("sandbox-governance");
        String traceId = currentTraceId();
        List<SandboxRuntimeBinding> active = bindings
            .findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
                scope.tenantId(), SandboxRuntimeBindingStatus.ACTIVE);
        for (SandboxRuntimeBinding previous : active) {
            bindings.save(new SandboxRuntimeBinding(
                previous.id(), previous.bindingId(), previous.tenantId(), previous.targetOrgUnitId(),
                previous.packageOwnerTenantId(), previous.packageId(), previous.packageCode(),
                previous.packageVersion(), SandboxRuntimeBindingStatus.INACTIVE, null,
                previous.activatedAt(), previous.activatedBy(), previous.createdAt(),
                previous.createdBy(), now, actor, traceId));
        }
        SandboxRuntimeBinding activated = bindings.save(new SandboxRuntimeBinding(
            null, "sandbox-binding-" + UUID.randomUUID(), scope.tenantId(), scope.targetOrgUnitId(),
            ownerTenantId, pack.packageId(), pack.packageCode(), pack.packageVersion(),
            SandboxRuntimeBindingStatus.ACTIVE, scope.tenantId() + "|ACTIVE", now, actor,
            now, actor, now, actor, traceId));
        SandboxResolutionSource source = PlatformTenant.ID.equals(ownerTenantId)
            ? SandboxResolutionSource.PLATFORM_PACKAGE
            : SandboxResolutionSource.TENANT_PACKAGE;
        audit.record(
            AuditAction.PUBLISH,
            "sandbox_runtime_binding",
            activated.bindingId(),
            "激活沙盘运行绑定 " + pack.packageCode() + "@" + pack.packageVersion()
                + " target=" + scope.targetOrgUnitId() + " source=" + source);
        return SandboxRuntimeStatusResponse.ready(
            activated, source, effective.items().size(), effective.warnings());
    }

    private static RuntimeScope currentScope() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return new RuntimeScope(
            scope.tenantId(), scope.nearestOrgUnitIdOrTenant(scope.tenantId()));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private static String currentTraceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null || traceId.isBlank() ? "sandbox-binding" : traceId;
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }

    private static String reasonCode(String message) {
        int separator = message.indexOf('：');
        if (separator < 0) {
            separator = message.indexOf(':');
        }
        String candidate = separator < 0 ? message : message.substring(0, separator);
        return candidate.matches("[A-Z0-9_]+") ? candidate : "SANDBOX_RUNTIME_NOT_READY";
    }

    private record RuntimeScope(String tenantId, String targetOrgUnitId) {
    }
}
