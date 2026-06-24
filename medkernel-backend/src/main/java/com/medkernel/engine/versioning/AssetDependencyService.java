package com.medkernel.engine.versioning;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 资产依赖图登记与引用完整性校验。
 */
@Service
public class AssetDependencyService {

    private final AssetDependencyRepository dependencies;
    private final AssetVersionRepository assetVersions;
    private final Clock clock;

    @Autowired
    public AssetDependencyService(
            AssetDependencyRepository dependencies,
            AssetVersionRepository assetVersions) {
        this(dependencies, assetVersions, Clock.systemUTC());
    }

    AssetDependencyService(
            AssetDependencyRepository dependencies,
            AssetVersionRepository assetVersions,
            Clock clock) {
        this.dependencies = dependencies;
        this.assetVersions = assetVersions;
        this.clock = clock;
    }

    @Transactional
    public void registerDependencies(
            AssetVersion owner,
            List<AssetDependencyDeclaration> declarations,
            String actor,
            String traceId) {
        AssetVersion value = required(owner, "资产版本");
        dependencies.deleteByTenantIdAndAssetTypeAndAssetIdentityAndVersionId(
            value.tenantId(), value.assetType(), value.assetIdentity(), value.versionId());
        if (declarations == null || declarations.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        for (AssetDependencyDeclaration declaration : declarations) {
            AssetDependency edge = new AssetDependency(
                null,
                "dep-" + Ulid.newUlid(),
                value.tenantId(),
                value.assetType(),
                value.assetIdentity(),
                value.versionId(),
                required(declaration.dependsOnAssetType(), "依赖资产类型"),
                required(declaration.dependsOnIdentity(), "依赖资产身份"),
                blankToNull(declaration.minVersionNo()),
                blankToNull(declaration.maxVersionNo()),
                declaration.kind() == null ? AssetDependencyKind.OTHER : declaration.kind(),
                now,
                required(actor, "操作人"),
                now,
                required(actor, "操作人"),
                blankToNull(traceId)
            );
            if (edge.assetType() == edge.dependsOnAssetType()
                    && edge.assetIdentity().equals(edge.dependsOnIdentity())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产依赖不能直接指向自身");
            }
            dependencies.save(edge);
        }
    }

    public void assertDependenciesResolvable(AssetVersion owner) {
        AssetVersion value = required(owner, "资产版本");
        List<AssetDependency> declared =
            dependencies.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                value.tenantId(), value.assetType(), value.assetIdentity(), value.versionId());
        for (AssetDependency edge : declared) {
            List<AssetVersion> candidates = resolvableCandidates(value, edge);
            if (candidates.isEmpty()) {
                throw dependencyConflict("依赖资产在目标作用域不可解析", edge, value, null);
            }
            boolean compatible = candidates.stream().anyMatch(candidate -> isCompatible(candidate.versionNo(), edge));
            if (!compatible) {
                String actual = candidates.stream()
                    .map(AssetVersion::versionNo)
                    .sorted()
                    .findFirst()
                    .orElse("N/A");
                throw dependencyConflict("依赖资产版本不兼容，当前可解析版本 " + actual, edge, value, null);
            }
        }
    }

    public void assertDisableAllowed(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String targetOrgPath,
            String applicableScope) {
        List<AssetDependency> dependents = dependencies.findByTenantIdAndDependsOnAssetTypeAndDependsOnIdentity(
            required(tenantId, "租户 ID"),
            required(assetType, "资产类型"),
            required(assetIdentity, "资产身份")
        );
        List<AssetVersion> activeDependents = new ArrayList<>();
        for (AssetDependency edge : dependents) {
            assetVersions.findByVersionIdAndTenantId(edge.versionId(), edge.tenantId())
                .filter(this::isInUse)
                .filter(version -> overlapsDisableScope(version, targetOrgPath, applicableScope))
                .ifPresent(activeDependents::add);
        }
        if (!activeDependents.isEmpty()) {
            activeDependents.sort(Comparator
                .comparing(AssetVersion::assetIdentity)
                .thenComparing(AssetVersion::versionNo));
            AssetVersion first = activeDependents.get(0);
            throw new ApiException(
                ErrorCode.CONFLICT,
                "引用完整性校验失败：资产 " + assetIdentity + " 被在用资产 "
                    + first.assetIdentity() + "@" + first.versionNo() + " 依赖，禁止 DISABLE"
            );
        }
    }

    public List<AssetVersion> activeDependentsOf(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String targetOrgPath,
            String applicableScope) {
        List<AssetDependency> dependentEdges = dependencies.findByTenantIdAndDependsOnAssetTypeAndDependsOnIdentity(
            required(tenantId, "租户 ID"),
            required(assetType, "资产类型"),
            required(assetIdentity, "资产身份")
        );
        List<AssetVersion> activeDependents = new ArrayList<>();
        for (AssetDependency edge : dependentEdges) {
            assetVersions.findByVersionIdAndTenantId(edge.versionId(), edge.tenantId())
                .filter(this::isInUse)
                .filter(version -> overlapsDisableScope(version, targetOrgPath, applicableScope))
                .ifPresent(activeDependents::add);
        }
        activeDependents.sort(Comparator
            .comparing(AssetVersion::assetType)
            .thenComparing(AssetVersion::assetIdentity)
            .thenComparing(AssetVersion::versionNo));
        return List.copyOf(activeDependents);
    }

    private List<AssetVersion> resolvableCandidates(AssetVersion owner, AssetDependency edge) {
        List<AssetVersion> candidates = new ArrayList<>();
        candidates.addAll(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            owner.tenantId(),
            edge.dependsOnAssetType(),
            edge.dependsOnIdentity(),
            AssetVersionStatus.PUBLISHED
        ));
        if (!PlatformAuthority.PLATFORM_TENANT_ID.equals(owner.tenantId())
                || !PlatformAuthority.PLATFORM_ORG_PATH.equals(owner.organizationScope())) {
            candidates.addAll(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                PlatformAuthority.PLATFORM_TENANT_ID,
                edge.dependsOnAssetType(),
                edge.dependsOnIdentity(),
                AssetVersionStatus.PUBLISHED
            ));
        }
        return candidates;
    }

    private boolean isInUse(AssetVersion version) {
        return version.status() == AssetVersionStatus.PUBLISHED;
    }

    private boolean overlapsDisableScope(AssetVersion version, String targetOrgPath, String applicableScope) {
        String orgPath = required(targetOrgPath, "目标组织路径");
        String scope = required(applicableScope, "适用人群或上下文");
        if (!Objects.equals(scope, version.applicableScope())) {
            return false;
        }
        return version.organizationScope().equals(orgPath)
            || version.organizationScope().startsWith(orgPath + "/")
            || orgPath.startsWith(version.organizationScope() + "/");
    }

    public static boolean isCompatible(String versionNo, AssetDependency edge) {
        String value = required(versionNo, "依赖版本号");
        if (edge.minVersionNo() != null && compareVersionNo(value, edge.minVersionNo()) < 0) {
            return false;
        }
        return edge.maxVersionNo() == null || compareVersionNo(value, edge.maxVersionNo()) <= 0;
    }

    private static int compareVersionNo(String left, String right) {
        String[] a = required(left, "左侧版本号").split("[._\\-]");
        String[] b = required(right, "右侧版本号").split("[._\\-]");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            String leftPart = i < a.length ? a[i] : "0";
            String rightPart = i < b.length ? b[i] : "0";
            int result = compareVersionPart(leftPart, rightPart);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int compareVersionPart(String left, String right) {
        if (left.matches("\\d+") && right.matches("\\d+")) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        return left.compareToIgnoreCase(right);
    }

    private static ApiException dependencyConflict(
            String reason,
            AssetDependency edge,
            AssetVersion owner,
            Throwable cause) {
        return new ApiException(
            ErrorCode.CONFLICT,
            "引用完整性校验失败：" + reason + "；"
                + owner.assetIdentity() + "@" + owner.versionNo()
                + " 依赖 " + edge.dependsOnIdentity()
                + versionRange(edge),
            cause
        );
    }

    private static String versionRange(AssetDependency edge) {
        if (edge.minVersionNo() == null && edge.maxVersionNo() == null) {
            return "";
        }
        return "（要求 " + nullToAny(edge.minVersionNo()) + " 至 " + nullToAny(edge.maxVersionNo()) + "）";
    }

    private static String nullToAny(String value) {
        return value == null ? "*" : value;
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
