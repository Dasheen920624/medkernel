package com.medkernel.engine.terminology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageAccessPolicy;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.PackageResponse;
import com.medkernel.engine.pkg.PackageVersionedAssetAdapter;
import com.medkernel.engine.pkg.TerminologyPackageBuildRequest;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 把已确认术语映射冻结为统一知识包，不维护独立术语包生命周期。
 */
@Service
public class TerminologyKnowledgePackageService {

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository packageItemRepository;
    private final TermMappingRepository mappingRepository;
    private final LocalTermRepository localTermRepository;
    private final StandardTermRepository standardTermRepository;
    private final TermMappingSnapshotRepository snapshotRepository;
    private final PackageVersionedAssetAdapter versionedAssets;
    private final AuditRecorder auditRecorder;

    public TerminologyKnowledgePackageService(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository packageItemRepository,
            TermMappingRepository mappingRepository,
            LocalTermRepository localTermRepository,
            StandardTermRepository standardTermRepository,
            TermMappingSnapshotRepository snapshotRepository,
            PackageVersionedAssetAdapter versionedAssets,
            AuditRecorder auditRecorder) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.mappingRepository = mappingRepository;
        this.localTermRepository = localTermRepository;
        this.standardTermRepository = standardTermRepository;
        this.snapshotRepository = snapshotRepository;
        this.versionedAssets = versionedAssets;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 构建仅包含当前范围已确认映射快照的知识包草稿。
     */
    @Transactional
    public PackageResponse build(TerminologyPackageBuildRequest request) {
        OrgScope currentScope = RequestContext.currentOrgScope();
        String tenantId = currentScope.tenantId();
        String packageCode = request.packageCode().trim();
        String packageVersion = request.packageVersion().trim();
        PackageScope scope = requireCurrentScope(
            tenantId, currentScope, request.scopeLevel(), request.scopeCode());
        packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                tenantId, packageCode, packageVersion)
            .ifPresent(existing -> {
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_004,
                    "知识包版本在该租户内已存在: " + packageVersion
                );
            });

        List<TermMappingSnapshot> snapshots = mappingRepository
            .findConfirmedByTenantIdAndScope(tenantId, scope.level(), scope.code())
            .stream()
            .map(mapping -> snapshot(tenantId, mapping))
            .toList();
        if (snapshots.isEmpty()) {
            throw ApiException.conflict("当前范围没有已确认映射，无法构建知识包");
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        KnowledgePackage pack = packageRepository.save(new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageCode,
            packageVersion,
            request.name().trim(),
            "术语映射快照，范围 " + scope.level() + ":" + scope.code(),
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.DRAFT,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        String assetIdentity = terminologyAssetIdentity(pack.packageCode(), scope);
        PackageItem marker = packageItemRepository.save(new PackageItem(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            pack.packageId(),
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            pack.packageVersion(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
        for (TermMappingSnapshot snapshot : snapshots) {
            snapshotRepository.save(TermMappingSnapshotEntity.fromSnapshot(
                tenantId,
                marker.itemId(),
                snapshot.mappingId(),
                snapshot,
                TermMappingSnapshotCodec.write(snapshot),
                now,
                actor
            ));
        }

        String contentHash = contentHash(pack, scope, snapshots);
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.PACKAGE,
            pack.packageCode(),
            pack.packageVersion(),
            scope.level().toLowerCase(Locale.ROOT) + ":" + scope.code(),
            "ALL",
            null,
            contentHash,
            "knowledge-package:" + pack.packageId(),
            actor,
            traceId
        ));
        auditRecorder.record(
            AuditAction.CREATE,
            "knowledge_package",
            pack.packageId(),
            "构建术语知识包草稿: " + pack.name() + " (" + pack.packageVersion() + ")"
        );
        return PackageResponse.from(pack);
    }

    private TermMappingSnapshot snapshot(String tenantId, TermMapping mapping) {
        LocalTerm localTerm = localTermRepository.findByTenantIdAndId(tenantId, mapping.localTermId())
            .orElseThrow(() -> ApiException.notFound("院内术语 id=" + mapping.localTermId()));
        StandardTerm standardTerm = standardTermRepository
            .findFirstByTenantIdsAndId(standardSources(tenantId), tenantId, mapping.standardTermId())
            .filter(term -> term.status() == StandardTermStatus.ACTIVE)
            .orElseThrow(() -> ApiException.notFound("标准术语 id=" + mapping.standardTermId()));
        return TermMappingSnapshot.from(mapping, localTerm, standardTerm);
    }

    private List<String> standardSources(String tenantId) {
        if (PlatformAuthority.PLATFORM_TENANT_ID.equals(tenantId)) {
            return List.of(tenantId);
        }
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID, tenantId);
    }

    private PackageScope requireCurrentScope(
            String tenantId,
            OrgScope current,
            String rawLevel,
            String rawCode) {
        String level = rawLevel == null ? "" : rawLevel.trim().toUpperCase();
        String code = rawCode == null ? "" : rawCode.trim();
        String expectedCode = switch (level) {
            case "TENANT" -> tenantId;
            case "REGION" -> current.groupId();
            case "FACILITY" -> firstNonBlank(current.siteId(), current.hospitalId());
            case "CAMPUS" -> current.campusId();
            case "DEPARTMENT" -> current.departmentId();
            default -> throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "术语知识包范围层级不受支持: " + rawLevel
            );
        };
        if (expectedCode == null || expectedCode.isBlank() || !expectedCode.equals(code)) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "术语知识包范围必须与当前组织上下文一致");
        }
        return new PackageScope(level, code);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String terminologyAssetIdentity(String packageCode, PackageScope scope) {
        return packageCode + "|" + scope.level() + "|" + scope.code();
    }

    private String contentHash(
            KnowledgePackage pack,
            PackageScope scope,
            List<TermMappingSnapshot> snapshots) {
        StringBuilder content = new StringBuilder()
            .append(pack.packageCode()).append('|')
            .append(pack.packageVersion()).append('|')
            .append(scope.level()).append('|')
            .append(scope.code());
        snapshots.forEach(snapshot -> content.append('|').append(TermMappingSnapshotCodec.write(snapshot)));
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "知识包摘要算法不可用", exception);
        }
    }

    private record PackageScope(String level, String code) {
    }
}
