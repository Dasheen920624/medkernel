package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 受限平台知识包的租户授权服务。
 */
@Service
public class PackageEntitlementService {

    private final KnowledgePackageRepository packageRepository;
    private final PackageEntitlementRepository entitlementRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final AuditRecorder auditRecorder;

    public PackageEntitlementService(
            KnowledgePackageRepository packageRepository,
            PackageEntitlementRepository entitlementRepository,
            OrgUnitRepository orgUnitRepository,
            AuditRecorder auditRecorder) {
        this.packageRepository = packageRepository;
        this.entitlementRepository = entitlementRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public PackageEntitlementResponse grant(
            String platformPackageId,
            PackageEntitlementGrantRequest request) {
        requirePlatformCaller();
        KnowledgePackage pack = requireRestrictedPlatformPackage(platformPackageId);
        String targetTenantId = required(request.targetTenantId(), "目标租户");
        if (PlatformTenant.ID.equals(targetTenantId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "平台主租户无需为自身登记包授权");
        }
        requireActiveTenant(targetTenantId);
        Instant now = Instant.now();
        if (request.expiresAt() == null || !request.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "授权到期时间必须晚于当前时间");
        }
        String reason = required(request.reason(), "授权原因");
        String actor = currentActor();
        String traceId = RequestContext.currentTraceId();
        PackageEntitlement saved = entitlementRepository
            .findByTenantIdAndPlatformPackageId(targetTenantId, pack.packageId())
            .map(existing -> entitlementRepository.save(new PackageEntitlement(
                existing.id(),
                existing.entitlementId(),
                targetTenantId,
                PlatformTenant.ID,
                pack.packageId(),
                packageIdentity(pack),
                PackageEntitlementStatus.GRANTED,
                now,
                request.expiresAt(),
                reason,
                existing.createdAt(),
                existing.createdBy(),
                now,
                actor,
                traceId)))
            .orElseGet(() -> entitlementRepository.save(new PackageEntitlement(
                null,
                UUID.randomUUID().toString(),
                targetTenantId,
                PlatformTenant.ID,
                pack.packageId(),
                packageIdentity(pack),
                PackageEntitlementStatus.GRANTED,
                now,
                request.expiresAt(),
                reason,
                now,
                actor,
                now,
                actor,
                traceId)));
        auditRecorder.record(
            AuditAction.PERMISSION_CHANGE,
            "package_entitlement",
            saved.entitlementId(),
            "开通或续期平台包授权: " + saved.packageIdentity() + " -> " + targetTenantId);
        return PackageEntitlementResponse.from(saved, now);
    }

    @Transactional
    public PackageEntitlementResponse revoke(
            String platformPackageId,
            String targetTenantId,
            PackageEntitlementRevokeRequest request) {
        requirePlatformCaller();
        KnowledgePackage pack = requireRestrictedPlatformPackage(platformPackageId);
        String normalizedTenantId = required(targetTenantId, "目标租户");
        String reason = required(request.reason(), "撤销原因");
        PackageEntitlement existing = entitlementRepository
            .findByTenantIdAndPlatformPackageId(normalizedTenantId, pack.packageId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标租户尚未登记该平台包授权"));
        Instant now = Instant.now();
        PackageEntitlement saved = entitlementRepository.save(new PackageEntitlement(
            existing.id(),
            existing.entitlementId(),
            existing.tenantId(),
            existing.platformTenantId(),
            existing.platformPackageId(),
            existing.packageIdentity(),
            PackageEntitlementStatus.REVOKED,
            existing.grantedAt(),
            existing.expiresAt(),
            reason,
            existing.createdAt(),
            existing.createdBy(),
            now,
            currentActor(),
            RequestContext.currentTraceId()));
        auditRecorder.record(
            AuditAction.PERMISSION_CHANGE,
            "package_entitlement",
            saved.entitlementId(),
            "撤销平台包授权: " + saved.packageIdentity() + " -> " + normalizedTenantId);
        return PackageEntitlementResponse.from(saved, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<PackageEntitlementResponse> list(
            String platformPackageId,
            PageRequest pageRequest) {
        requirePlatformCaller();
        requireRestrictedPlatformPackage(platformPackageId);
        Instant now = Instant.now();
        List<PackageEntitlementResponse> items = entitlementRepository
            .pageByPlatformPackageId(platformPackageId, pageRequest.offset(), pageRequest.safeSize())
            .stream()
            .map(item -> PackageEntitlementResponse.from(item, now))
            .toList();
        return PageResponse.of(
            items,
            pageRequest,
            entitlementRepository.countByPlatformPackageId(platformPackageId));
    }

    @Transactional(readOnly = true)
    public void assertUsable(String tenantId, KnowledgePackage pack) {
        String normalizedTenantId = required(tenantId, "租户");
        if (pack.accessPolicy() == PackageAccessPolicy.OPEN || PlatformTenant.ID.equals(normalizedTenantId)) {
            return;
        }
        PackageEntitlement entitlement = entitlementRepository
            .findByTenantIdAndPlatformPackageId(normalizedTenantId, pack.packageId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "平台知识包不可用"));
        if (entitlement.status() != PackageEntitlementStatus.GRANTED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "平台知识包不可用");
        }
        if (!entitlement.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(
                ErrorCode.PACKAGE_ENTITLEMENT_EXPIRED,
                "平台知识包授权已到期: " + entitlement.packageIdentity());
        }
    }

    /**
     * 批量返回目标租户当前可使用的平台包 ID。
     *
     * <p>开放包直接可用，受限包仅在授权有效时可用；一次批量读取授权，避免准备度计算产生 N+1。
     */
    @Transactional(readOnly = true)
    public Set<String> usablePackageIds(String tenantId, List<KnowledgePackage> packages) {
        String normalizedTenantId = required(tenantId, "租户");
        if (packages == null || packages.isEmpty()) {
            return Set.of();
        }
        Set<String> usableIds = new HashSet<>();
        Set<String> restrictedIds = new HashSet<>();
        for (KnowledgePackage pack : packages) {
            if (pack.accessPolicy() == PackageAccessPolicy.OPEN
                    || PlatformTenant.ID.equals(normalizedTenantId)) {
                usableIds.add(pack.packageId());
            } else {
                restrictedIds.add(pack.packageId());
            }
        }
        if (restrictedIds.isEmpty()) {
            return Set.copyOf(usableIds);
        }
        Instant now = Instant.now();
        entitlementRepository
            .findByTenantIdAndPlatformPackageIdIn(normalizedTenantId, restrictedIds)
            .stream()
            .filter(entitlement -> entitlement.status() == PackageEntitlementStatus.GRANTED)
            .filter(entitlement -> entitlement.expiresAt().isAfter(now))
            .map(PackageEntitlement::platformPackageId)
            .forEach(usableIds::add);
        return Set.copyOf(usableIds);
    }

    private KnowledgePackage requireRestrictedPlatformPackage(String platformPackageId) {
        KnowledgePackage pack = packageRepository
            .findByPackageIdAndTenantId(required(platformPackageId, "平台包 ID"), PlatformTenant.ID)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "平台知识包不存在"));
        if (pack.accessPolicy() != PackageAccessPolicy.ENTITLED) {
            throw new ApiException(ErrorCode.CONFLICT, "开放平台包无需登记租户授权");
        }
        return pack;
    }

    private void requirePlatformCaller() {
        if (!PlatformTenant.ID.equals(RequestContext.currentOrgScope().tenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "只有平台主租户可管理平台包授权");
        }
    }

    private void requireActiveTenant(String tenantId) {
        OrgUnit tenantRoot = orgUnitRepository.findByTenantIdAndParentIdIsNull(tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标租户不存在"));
        if (tenantRoot.status() != OrgUnitStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "目标租户未启用，不能开通平台包授权");
        }
    }

    private String currentActor() {
        String actor = RequestContext.snapshot().userId();
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    private static String packageIdentity(KnowledgePackage pack) {
        return pack.packageCode() + "@" + pack.packageVersion();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
